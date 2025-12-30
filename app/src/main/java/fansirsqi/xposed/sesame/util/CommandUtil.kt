package fansirsqi.xposed.sesame.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object CommandUtil {

    private const val TAG = "CommandUtil"
    private const val TIMEOUT_MS = 10000L
    private const val ACTION_BIND = "fansirsqi.xposed.sesame.action.BIND_COMMAND_SERVICE"
    private const val BIND_TIMEOUT_MS = 5000L // Use a consistent timeout

    private var commandService: ICommandService? = null
    private var isBound = false
    private var lastBindAttemptTime = 0L
    private var lastBindSuccess = false

    private var connectionDeferred: CompletableDeferred<Unit>? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "CommandService 已连接")
            commandService = ICommandService.Stub.asInterface(service)
            isBound = true
            connectionDeferred?.let {
                if (it.isActive) it.complete(Unit)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "CommandService 已断开")
            commandService = null
            isBound = false
        }
    }

    private suspend fun bindService(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isBound && commandService != null) {
            return@withContext true
        }
        val currentTime = System.currentTimeMillis()
        if (lastBindAttemptTime > 0 && (currentTime - lastBindAttemptTime) < 15000L && !lastBindSuccess) {
            Log.d(TAG, "15秒内已尝试绑定失败，跳过本次绑定")
            return@withContext false
        }
        lastBindAttemptTime = currentTime

        try {
            connectionDeferred = CompletableDeferred()
            val intent = Intent(ACTION_BIND)
            intent.setPackage("fansirsqi.xposed.sesame")
            val result = context.applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!result) {
                lastBindSuccess = false
                Log.e(TAG, "bindService returned false")
                return@withContext false
            }
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                connectionDeferred?.await()
            }
            lastBindSuccess = (connected != null)
            if (!lastBindSuccess) {
                Log.e(TAG, "bindService timed out")
            }
            lastBindSuccess
        } catch (e: Exception) {
            Log.e(TAG, "绑定服务失败: ${e.message}")
            lastBindSuccess = false
            false
        }
    }

    suspend fun executeCommand(context: Context, command: String): String? = withContext(Dispatchers.IO) {
        if (!bindService(context)) {
            Log.e(TAG, "绑定服务失败，无法执行命令")
            return@withContext null
        }
        val service = commandService
        if (service == null) {
            Log.e(TAG, "commandService 为 null，无法执行命令")
            return@withContext null
        }

        Log.d(TAG, "服务已连接，开始发送命令: $command")
        val deferred = CompletableDeferred<String?>()
        val callback = object : ICallback.Stub() {
            override fun onSuccess(output: String) {
                Log.d(TAG, "命令执行成功: $output")
                deferred.complete(output)
            }

            override fun onError(error: String) {
                Log.e(TAG, "命令执行失败: $error")
                deferred.complete(null)
            }
        }
        try {
            service.executeCommand(command, callback)
            Log.d(TAG, "命令已发送，等待结果...")
            withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            Log.e(TAG, "执行命令异常: ${e.message}", e)
            null
        }
    }

    suspend fun getShellType(context: Context): String? = withContext(Dispatchers.IO) {
        if (!bindService(context)) {
            Log.e(TAG, "绑定服务失败，无法获取Shell类型")
            return@withContext null
        }
        val service = commandService
        if (service == null) {
            Log.e(TAG, "commandService 为 null，无法获取Shell类型")
            return@withContext null
        }
        try {
            service.shellType
        } catch (e: Exception) {
            Log.e(TAG, "获取 Shell 类型失败: ${e.message}")
            null
        }
    }
}