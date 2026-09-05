package fansirsqi.xposed.sesame.hook

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import fansirsqi.xposed.sesame.entity.RpcEntity
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.NetworkUtils
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import fansirsqi.xposed.sesame.task.ModelTask
import java.util.UUID

/**
 * RPC 请求管理器 (带熔断与兜底机制)
 */
object RequestManager {

    private const val TAG = "RequestManager"
    const val EMPTY_RPC_RESPONSE =
        """{"success":false,"resultCode":"EMPTY_RPC_RESPONSE","resultDesc":"RPC返回为空"}"""
    const val VERIFICATION_REQUIRED_RESPONSE =
        """{"success":false,"resultCode":"RPC_VERIFICATION_REQUIRED","resultDesc":"触发安全验证，请人工验证后继续"}"""

    private val recoveryPolicy = RpcRecoveryPolicy()
    private const val VERIFICATION_PREFS = "sesame_rpc_verification"
    private var resumeDialogVisible = false

    private fun verificationPrefs() = ApplicationHook.appContext
        ?.getSharedPreferences(VERIFICATION_PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isVerificationPaused(): Boolean = recoveryPolicy.blockReason == RpcBlockReason.VERIFICATION

    @JvmStatic
    fun isEmptyRpcResponse(result: String?): Boolean {
        return result.isNullOrBlank()
    }

    @JvmStatic
    fun isVerificationRequired(errorCode: String?, errorMessage: String?): Boolean {
        val message = errorMessage.orEmpty()
        return errorCode == "1009" ||
            message.contains("为保障您的正常访问，请进行验证后继续") ||
            message.contains("为了保障您的操作安全，请进行验证后继续") ||
            message.contains("请进行验证后继续")
    }

    @JvmStatic
    fun handleVerificationRequired(method: String?) {
        ApplicationHook.setOffline(true)
        if (recoveryPolicy.onVerificationRequired() != RecoveryDecision.WAIT_FOR_MANUAL_VERIFICATION) {
            return
        }

        UserMap.currentUid?.let { uid ->
            verificationPrefs()?.edit()?.putString(uid, UUID.randomUUID().toString())?.commit()
        }
        ModelTask.stopAllTask()
        Log.record(TAG, "检测到安全验证，暂停后续RPC请求: $method")
        notifyVerificationPause()
    }

    private fun notifyVerificationPause() {
        val context = ApplicationHook.appContext ?: return
        val uid = UserMap.currentUid ?: return
        val token = verificationPrefs()?.getString(uid, null) ?: return
        val intent = Intent(ApplicationHook.BroadcastActions.RESUME_VERIFIED)
            .setPackage(context.packageName)
            .putExtra("userId", uid)
            .putExtra("verificationToken", token)
        val action = PendingIntent.getBroadcast(context, 109,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        Notify.sendNewNotification("自动任务已暂停", "请先在支付宝完成验证，再点已验证恢复", action)
    }

    fun showVerificationResumeDialog(activity: Activity) {
        if (!isVerificationPaused() || resumeDialogVisible || activity.isFinishing || activity.isDestroyed) return
        val uid = UserMap.currentUid ?: return
        val token = verificationPrefs()?.getString(uid, null) ?: return
        resumeDialogVisible = true
        AlertDialog.Builder(activity)
            .setTitle("自动任务已暂停")
            .setMessage("完成支付宝安全验证后，可恢复自动任务。")
            .setNegativeButton("保持暂停", null)
            .setPositiveButton("已验证，恢复任务") { _, _ ->
                activity.sendBroadcast(Intent(ApplicationHook.BroadcastActions.RESUME_VERIFIED)
                    .setPackage(activity.packageName).putExtra("userId", uid)
                    .putExtra("verificationToken", token))
            }
            .setOnDismissListener { resumeDialogVisible = false }
            .show()
    }

    @Synchronized
    fun resumeAfterManualVerification(intent: Intent): Boolean {
        val uid = UserMap.currentUid ?: return false
        val expected = verificationPrefs()?.getString(uid, null) ?: return false
        if (intent.getStringExtra("userId") != uid ||
            intent.getStringExtra("verificationToken") != expected) return false
        // 先等待旧业务退出，避免解除暂停时旧请求继续发出。
        Log.record(TAG, "等待已暂停任务退出后恢复")
        kotlinx.coroutines.runBlocking { ModelTask.stopAllTaskAndJoin() }
        if (UserMap.currentUid != uid || !isVerificationPaused() ||
            verificationPrefs()?.getString(uid, null) != expected) return false
        verificationPrefs()?.edit()?.remove(uid)?.commit()
        recoveryPolicy.reset()
        ApplicationHook.setOffline(false)
        Log.record(TAG, "已由用户确认恢复自动任务")
        return true
    }

    /**
     * 核心执行函数 (内联优化)
     * 流程：离线检查 -> 获取 Bridge -> 执行请求 -> 结果校验 -> 错误计数/重置
     */
    private inline fun executeRpc(methodLog: String?, block: (RpcBridge) -> String?): String {
        // 1. 【前置检查】如果已经离线，直接中断并尝试恢复
        if (ApplicationHook.offline) {
            recoveryPolicy.handleExternalOffline(::handleOfflineRecovery)
            return blockedResponse()
        }

        // 2. 获取 Bridge (包含网络检查)
        // 如果这里获取失败，也视为一次错误
        val bridge = getRpcBridge()
        if (bridge == null) {
            handleFailure("Network/Bridge Unavailable", "网络或Bridge不可用")
            return EMPTY_RPC_RESPONSE
        }

        // 3. 执行请求
        val result = try {
            block(bridge)
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "RPC 执行异常: $methodLog", e)
            null // 异常视为 null，触发失败逻辑
        }

        // 在途请求可能在验证后才结束，不能按普通空响应处理。
        if (ApplicationHook.offline) return blockedResponse()

        // 4. 结果校验与状态维护
        if (isEmptyRpcResponse(result)) {
            // 失败：增加计数，检查兜底
            handleFailure(methodLog ?: "Unknown", "返回数据为空")
            return EMPTY_RPC_RESPONSE
        }

        if (result == VERIFICATION_REQUIRED_RESPONSE || ApplicationHook.offline) {
            return blockedResponse()
        }

        val hadFailure = recoveryPolicy.failureCount > 0 ||
            recoveryPolicy.blockReason != RpcBlockReason.NONE
        recoveryPolicy.onSuccess()
        if (isVerificationPaused()) return blockedResponse()
        if (hadFailure) {
            Log.record(TAG, "RPC 恢复正常，错误计数重置")
        }
        return result.orEmpty()
    }

    /**
     * 处理失败逻辑：计数、报警、熔断
     */
    private fun handleFailure(method: String, reason: String) {
        val maxCount = BaseModel.setMaxErrorCount.value
        val decision = recoveryPolicy.onNetworkFailure(maxCount)
        val currentCount = recoveryPolicy.failureCount

        Log.error(TAG, "RPC 失败 ($currentCount/$maxCount) | Method: $method | Reason: $reason")

        if (decision == RecoveryDecision.SCHEDULE_REOPEN) {
            Log.record(TAG, "🔴 连续失败次数达到阈值，触发熔断兜底机制！")
            ApplicationHook.setOffline(true)
            if (BaseModel.errNotify.value) {
                val msg = "${TimeUtil.getTimeStr()} | 网络异常次数超过阈值[$maxCount]"
                Notify.sendNewNotification(msg, "RPC 连续失败，脚本已暂停")
            }
            handleOfflineRecovery()
        }
    }

    private fun blockedResponse(): String {
        recoveryPolicy.onBlockedRequest()
        return if (recoveryPolicy.blockReason == RpcBlockReason.VERIFICATION) {
            VERIFICATION_REQUIRED_RESPONSE
        } else {
            EMPTY_RPC_RESPONSE
        }
    }

    @JvmStatic
    fun onRpcBridgeReady() {
        val uid = UserMap.currentUid
        recoveryPolicy.reset()
        if (uid != null && verificationPrefs()?.contains(uid) == true) {
            recoveryPolicy.onVerificationRequired()
            ApplicationHook.setOffline(true)
            Log.record(TAG, "保留安全验证暂停状态，等待用户确认恢复")
            notifyVerificationPause()
        }
    }

    /**
     * 处理离线恢复逻辑
     * 可以是发送广播、拉起 App 等
     */
    private fun handleOfflineRecovery() {
        // 防止短时间内频繁触发恢复逻辑 (可选)
        // 这里简单实现：尝试拉起支付宝或发送重登录广播

        Log.record(TAG, "正在尝试执行离线恢复策略...")
        // 策略 A: 重新拉起 App (推荐)
        ApplicationHook.reOpenApp()
        // 策略 B: 发送重登录广播 (如果宿主还能响应广播)
        // ApplicationHook.reLoginByBroadcast()
    }

    /**
     * 获取 RpcBridge 实例
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getRpcBridge(): RpcBridge? {
        if (!NetworkUtils.isNetworkAvailable()) {
            Log.record(TAG, "网络不可用，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
            if (!NetworkUtils.isNetworkAvailable()) {
                return null
            }
        }

        var bridge = ApplicationHook.rpcBridge
        if (bridge == null) {
            Log.record(TAG, "RpcBridge 未初始化，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
            bridge = ApplicationHook.rpcBridge
        }

        return bridge
    }

    // ================== 公开 API (保持不变) ==================

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity): String {
        return executeRpc(rpcEntity.methodName) { bridge ->
            bridge.requestString(rpcEntity, 3, 1200)
        }
    }

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): String {
        return executeRpc(rpcEntity.methodName) { bridge ->
            bridge.requestString(rpcEntity, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, relation: String?): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, relation)
        }
    }

    @JvmStatic
    fun requestString(
        method: String?,
        data: String?,
        appName: String?,
        methodName: String?,
        facadeName: String?
    ): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, appName, methodName, facadeName)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, tryCount: Int, retryInterval: Int): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestString(
        method: String?,
        data: String?,
        relation: String?,
        tryCount: Int,
        retryInterval: Int
    ): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, relation, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestObject(rpcEntity: RpcEntity?, tryCount: Int, retryInterval: Int) {
        if (rpcEntity == null) return
        // requestObject 不涉及返回值判断，但同样需要离线检查
        if (ApplicationHook.offline) {
            recoveryPolicy.handleExternalOffline(::handleOfflineRecovery)
            return
        }

        val bridge = getRpcBridge()
        if (bridge == null) {
            handleFailure("requestObject", "Bridge Unavailable")
            return
        }

        try {
            bridge.requestObject(rpcEntity, tryCount, retryInterval)
            recoveryPolicy.onRequestCompletedWithoutResponse()
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "requestObject 异常: ${rpcEntity.methodName}", e)
            handleFailure(rpcEntity.methodName ?: "Unknown", "Exception")
        }
    }
}
