package fansirsqi.xposed.sesame.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 命令服务客户端工具类
 * 负责与 CommandService 建立连接并通过 AIDL 发送指令
 */
object CommandUtil {

    private const val TAG = "CommandUtil"
    private const val ACTION_BIND = "fansirsqi.xposed.sesame.action.BIND_COMMAND_SERVICE"
    private const val PACKAGE_NAME = "fansirsqi.xposed.sesame"

    private const val BIND_TIMEOUT_MS = 3000L      // 绑定超时时间
    private const val EXEC_TIMEOUT_MS = 15000L     // 命令执行超时时间

    // AIDL 接口实例
    private var commandService: ICommandService? = null

    // 连接状态管理
    private val bindMutex = Mutex()
    private val isBound = AtomicBoolean(false)
    private var connectionDeferred: CompletableDeferred<Boolean>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "✅ CommandService 已连接")
            try {
                commandService = ICommandService.Stub.asInterface(service)
                // 监听服务端死亡（例如服务进程崩溃）
                service?.linkToDeath({
                    Log.w(TAG, "💀 CommandService 远程进程死亡")
                    handleServiceLost()
                }, 0)

                isBound.set(true)
                connectionDeferred?.complete(true)
            } catch (e: Exception) {
                Log.e(TAG, "服务连接初始化失败", e)
                connectionDeferred?.complete(false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "❌ CommandService 已断开连接")
            handleServiceLost()
        }
    }

    private fun handleServiceLost() {
        commandService = null
        isBound.set(false)
        connectionDeferred = null
    }

    /**
     * 绑定服务 (线程安全)
     */
    private suspend fun ensureServiceBound(context: Context): Boolean {
        // 快速检查：如果已经绑定且服务对象有效
        if (isBound.get() && commandService?.asBinder()?.isBinderAlive == true) {
            return true
        }

        return bindMutex.withLock {
            // 双重检查
            if (isBound.get() && commandService?.asBinder()?.isBinderAlive == true) {
                return@withLock true
            }

            Log.d(TAG, "正在尝试绑定 CommandService...")

            // 重置状态
            handleServiceLost()
            connectionDeferred = CompletableDeferred()

            val intent = Intent(ACTION_BIND).apply {
                setPackage(PACKAGE_NAME)
            }

            try {
                val bindResult = context.applicationContext.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )

                if (!bindResult) {
                    Log.e(TAG, "bindService 返回 false，可能是服务未注册或权限不足")
                    return@withLock false
                }

                // 等待连接结果
                val success = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                    connectionDeferred?.await()
                } ?: false

                if (!success) {
                    Log.e(TAG, "绑定服务超时或失败")
                    // 超时后清理一下
                    context.applicationContext.unbindService(serviceConnection)
                }

                return@withLock success
            } catch (e: Exception) {
                Log.e(TAG, "绑定服务异常", e)
                return@withLock false
            }
        }
    }

    /**
     * 执行命令
     * @return 命令输出结果，如果执行失败或超时则返回 null
     */
    suspend fun executeCommand(context: Context, command: String): String? = withContext(Dispatchers.IO) {
        if (!ensureServiceBound(context)) {
            Log.e(TAG, "无法连接到命令服务，放弃执行: $command")
            return@withContext null
        }

        val service = commandService ?: return@withContext null
        val resultDeferred = CompletableDeferred<String?>()

        val callback = object : ICallback.Stub() {
            override fun onSuccess(output: String) {
                resultDeferred.complete(output)
            }

            override fun onError(error: String) {
                Log.e(TAG, "服务端返回错误: $error")
                resultDeferred.complete(null)
            }
        }

        try {
            service.executeCommand(command, callback)

            // 等待结果
            withTimeoutOrNull(EXEC_TIMEOUT_MS) {
                resultDeferred.await()
            } ?: run {
                Log.e(TAG, "命令执行等待超时")
                null
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "AIDL 调用失败 (RemoteException): ${e.message}")
            // 发生远程异常通常意味着连接断了，重置状态以便下次重连
            handleServiceLost()
            null
        } catch (e: Exception) {
            Log.e(TAG, "执行命令未知异常", e)
            null
        }
    }

    /**
     * 获取当前服务端使用的 Shell 类型
     */
    suspend fun getShellType(context: Context): String = withContext(Dispatchers.IO) {
        if (!ensureServiceBound(context)) {
            return@withContext "服务未连接"
        }

        try {
            commandService?.shellType ?: "未知"
        } catch (e: RemoteException) {
            handleServiceLost()
            "获取失败(连接断开)"
        } catch (e: Exception) {
            "获取失败(${e.message})"
        }
    }

    /**
     * 手动解绑服务 (如果需要清理资源)
     */
    fun unbind(context: Context) {
        if (isBound.compareAndSet(true, false)) {
            try {
                context.applicationContext.unbindService(serviceConnection)
                Log.d(TAG, "已主动解绑服务")
            } catch (e: Exception) {
                Log.w(TAG, "解绑失败: ${e.message}")
            } finally {
                commandService = null
            }
        }
    }
}