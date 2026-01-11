package fansirsqi.xposed.sesame.hook

import android.Manifest
import androidx.annotation.RequiresPermission
import fansirsqi.xposed.sesame.entity.RpcEntity
import fansirsqi.xposed.sesame.hook.RequestManager.executeRpc
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.NetworkUtils

/**
 * RPC 请求管理器 (优化版)
 *
 * 优化点：
 * 1. 使用内联高阶函数 [executeRpc] 消除样板代码。
 * 2. 简化空值检查逻辑。
 * 3. 统一管理网络状态检查和 Bridge 获取。
 */
object RequestManager {

    private const val TAG = "RequestManager"

    /**
     * 检查 RPC 返回结果
     */
    private fun checkResult(result: String?, method: String = "Unknown"): String {
        if (result.isNullOrBlank()) {
            Log.error(TAG, "RPC 响应无效 (Null or Empty) | Method: $method")
            return ""
        }
        return result
    }

    /**
     * 获取 RpcBridge 实例
     * 包含网络检查和实例检查的重试逻辑
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getRpcBridge(): RpcBridge? {
        // 1. 检查网络
        if (!NetworkUtils.isNetworkAvailable()) {
            Log.record(TAG, "网络不可用，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
            if (!NetworkUtils.isNetworkAvailable()) {
                Log.error(TAG, "网络仍不可用 (Type: ${NetworkUtils.getNetworkType()})，放弃请求")
                return null
            }
        }

        // 2. 检查 Bridge 实例
        var bridge = ApplicationHook.rpcBridge
        if (bridge == null) {
            Log.record(TAG, "RpcBridge 未初始化，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
            bridge = ApplicationHook.rpcBridge
        }

        if (bridge == null) {
            Log.error(TAG, "RpcBridge 获取失败，无法发送请求")
        }
        return bridge
    }

    /**
     * 核心执行函数 (内联优化)
     * 封装了获取 Bridge -> 执行请求 -> 检查结果 的通用流程
     *
     * @param methodLog 用于日志记录的方法名或描述
     * @param block 实际执行 RPC 请求的 lambda
     */
    private inline fun executeRpc(methodLog: String?, block: (RpcBridge) -> String?): String {
        val bridge = getRpcBridge() ?: return ""
        val result = try {
            block(bridge)
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "RPC 执行异常: $methodLog", e)
            return ""
        }
        return checkResult(result, methodLog ?: "Unknown")
    }

    // ================== 公开 API ==================

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
        // requestObject 无返回值，不走 executeRpc 流程，单独处理
        val bridge = getRpcBridge() ?: return
        try {
            bridge.requestObject(rpcEntity, tryCount, retryInterval)
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "requestObject 异常: ${rpcEntity.methodName}", e)
        }
    }
}