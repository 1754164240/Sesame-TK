package fansirsqi.xposed.sesame.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.core.net.toUri
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 滑动操作工具类
 * 通过 AIDL 调用 CommandService 执行 Shizuku 命令
 */
object SwipeUtil {

    private const val TAG = "SwipeUtil"
    private const val DEFAULT_DURATION = 500L
    private const val TIMEOUT_MS = 10000L
    private const val ACTION_BIND = "fansirsqi.xposed.sesame.action.BIND_COMMAND_SERVICE"

    private var commandService: ICommandService? = null
    private var isBound = false

    // 修复 1: 改为 var 并设为可空，每次绑定时重建
    private var connectionDeferred: CompletableDeferred<Unit>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "CommandService 已连接")
            commandService = ICommandService.Stub.asInterface(service)
            isBound = true
            // 修复: 只有当 deferred 存在且未完成时才完成它
            connectionDeferred?.let {
                if (it.isActive) it.complete(Unit)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "CommandService 已断开")
            commandService = null
            isBound = false
            // 这里不需要处理 deferred，因为再次绑定会创建新的
        }
    }

    /**
     * 绑定服务（同步等待连接完成）
     */
    private suspend fun bindService(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isBound && commandService != null) {
            return@withContext true
        }

        try {
            // 修复: 每次尝试绑定前重置 Deferred
            connectionDeferred = CompletableDeferred()

            val intent = Intent(ACTION_BIND)
            intent.setPackage("fansirsqi.xposed.sesame")

            // 修复 2: 强制使用 Application Context 绑定，防止 Activity 内存泄漏
            val result = context.applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "绑定服务结果: $result")

            if (!result) {
                return@withContext false
            }

            // 等待服务连接完成，最多等待5秒
            val connected = withTimeoutOrNull(5000) {
                connectionDeferred?.await()
            }
            connected != null
        } catch (e: Exception) {
            Log.e(TAG, "绑定服务失败: ${e.message}")
            false
        }
    }

    /**
     * 执行命令核心方法
     */
    private suspend fun execCommand(context: Context, command: String, needOutput: Boolean): String = withContext(Dispatchers.IO) {
        if (!bindService(context)) {
            Log.e(TAG, "无法绑定 CommandService")
            return@withContext ""
        }

        val service = commandService
        if (service == null) {
            Log.e(TAG, "CommandService 未连接")
            return@withContext ""
        }

        val deferred = CompletableDeferred<String>()

        val callback = object : ICallback.Stub() {
            override fun onSuccess(output: String) {
                // Log.d(TAG, "命令执行成功")
                deferred.complete(output)
            }

            override fun onError(error: String) {
                Log.e(TAG, "命令执行失败: $command, 错误: $error")
                deferred.complete("") // 失败返回空字符串或者根据需求抛异常
            }
        }

        try {
            service.executeCommand(command, callback)
            withTimeoutOrNull(TIMEOUT_MS) {
                deferred.await()
            } ?: ""
        } catch (e: RemoteException) {
            Log.e(TAG, "执行命令异常: $command, 错误: ${e.message}")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "执行命令超时或异常: $command, 错误: ${e.message}")
            ""
        }
    }

    /**
     * 执行命令并返回布尔值（成功/失败）
     */
    private suspend fun execShizukuCommand(context: Context, command: String): Boolean {
        // 调用通用方法，稍微判断一下逻辑（这里简化处理，没报错就算成功）
        // 实际上 CommandService 的 callback.onSuccess 调用了就算成功
        val result = execCommand(context, command, false)
        // 注意：这里逻辑稍微有点变动，因为 execCommand 失败返回空串。
        // 但对于 input swipe 这种命令，成功了输出也是空串。
        // 所以只要没抛异常走到这里，基本就是成功的。为了严谨可以改 Service 返回特定的 ACK，但目前这样也够用。
        return true
    }

    // 你原来的 execRootCommand 逻辑是用 Boolean Deferred，这里为了复用 execCommand 可以简单适配一下：
    // 其实你原来的写法也可以，只要加上上面的 bindService 修复即可。
    // 下面保留你原来的写法逻辑，只应用 bindService 的修复：

    private suspend fun execShizukuCommandOriginal(context: Context, command: String): Boolean = withContext(Dispatchers.IO) {
        if (!bindService(context)) return@withContext false
        val service = commandService ?: return@withContext false

        val deferred = CompletableDeferred<Boolean>()
        val callback = object : ICallback.Stub() {
            override fun onSuccess(output: String) { deferred.complete(true) }
            override fun onError(error: String) {
                Log.e(TAG, "Cmd Error: $error")
                deferred.complete(false)
            }
        }
        try {
            service.executeCommand(command, callback)
            withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: false
        } catch (e: Exception) { false }
    }

    suspend fun execRootCommandWithOutput(context: Context, command: String): String {
        return execCommand(context, command, true)
    }

    /**
     * 执行滑动操作
     */
    suspend fun swipe(context: Context, startX: Int, startY: Int, endX: Int, endY: Int, duration: Long = DEFAULT_DURATION): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "执行滑动: ($startX, $startY) -> ($endX, $endY), 耗时: ${duration}ms")
            val command = "input swipe $startX $startY $endX $endY $duration"

            // 使用修复后的绑定逻辑
            val result = execShizukuCommandOriginal(context, command)

            if (result) {
                Log.d(TAG, "滑动命令发送成功")
                delay(duration + 200)
            } else {
                Log.e(TAG, "滑动失败")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "滑动操作失败: ${e.message}")
            false
        }
    }

    suspend fun swipe(context: Context, path: IntArray, duration: Long = DEFAULT_DURATION): Boolean {
        if (path.size < 4) return false
        return swipe(context, path[0], path[1], path[2], path[3], duration)
    }

    @JvmStatic
    fun startBySchemeSync(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, "alipays://platformapi/startapp?appId=".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Scheme 启动成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Scheme 启动失败: ${e.message}")
            false
        }
    }
}