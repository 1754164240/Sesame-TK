package fansirsqi.xposed.sesame.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 命令服务客户端工具类
 * 负责与 CommandService 建立连接并通过 AIDL 发送指令
 * 支持从宿主应用（支付宝）进程跨进程绑定到模块的 Service
 */
object CommandUtil {

    private const val TAG = "CommandUtil"
    private const val ACTION_BIND = "fansirsqi.xposed.sesame.action.BIND_COMMAND_SERVICE"
    private const val PACKAGE_NAME = "fansirsqi.xposed.sesame"

    private const val BIND_TIMEOUT_MS = 5000L      // 绑定超时时间（增加到5秒）
    private const val EXEC_TIMEOUT_MS = 15000L     // 命令执行超时时间

    // AIDL 接口实例
    private var commandService: ICommandService? = null

    // 连接状态管理
    private val bindMutex = Mutex()
    private val isBound = AtomicBoolean(false)
    private var connectionDeferred: CompletableDeferred<Boolean>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "✅ CommandService 已连接: $name")
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
            Log.w(TAG, "❌ CommandService 已断开连接: $name")
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
     * 支持跨进程绑定：从支付宝进程绑定模块的 CommandService
     */
    @SuppressLint("ObsoleteSdkInt")
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
            // 重置状态
            handleServiceLost()
            connectionDeferred = CompletableDeferred()
            // 构建 Intent
            val intent = Intent().apply {
                action = ACTION_BIND
                setPackage(PACKAGE_NAME)
                component = ComponentName(
                    PACKAGE_NAME,
                    "fansirsqi.xposed.sesame.service.CommandService"
                )
            }

            Log.i(TAG, "Intent 配置:")
            Log.i(TAG, "  - action: ${intent.action}")
            Log.i(TAG, "  - package: ${intent.`package`}")
            Log.i(TAG, "  - component: ${intent.component}")

            try {
                // 步骤1: 先尝试启动服务（确保服务进程存在）
                try {
                    Log.i(TAG, "步骤1: 尝试启动 Service...")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.applicationContext.startForegroundService(intent)
                    } else {
                        context.applicationContext.startService(intent)
                    }
                    Log.i(TAG, "✓ startService 调用成功")
                } catch (e: SecurityException) {
                    Log.w(TAG, "✗ startService 失败 (SecurityException): ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ startService 失败: ${e.message}")
                }

                // 等待服务启动
                delay(800)

                // 步骤2: 绑定服务
                Log.i(TAG, "步骤2: 尝试绑定 Service...")
                val bindResult = context.applicationContext.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
                )

                Log.i(TAG, "bindService 返回: $bindResult")

                if (!bindResult) {
                    Log.e(TAG, "❌ bindService 返回 false")
                    Log.e(TAG, "可能的原因:")
                    Log.e(TAG, "  1. 模块 APK 未安装或被禁用")
                    Log.e(TAG, "  2. Service 未在 AndroidManifest.xml 中正确注册")
                    Log.e(TAG, "  3. Android 系统阻止跨应用绑定（SELinux/权限策略）")
                    Log.e(TAG, "  4. 目标应用版本不匹配或签名问题")
                    
                    // 尝试检查模块是否安装
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfo(PACKAGE_NAME, 0)
                        Log.i(TAG, "模块已安装: ${appInfo.enabled}")
                    } catch (e: Exception) {
                        Log.e(TAG, "模块未安装或无法访问: ${e.message}")
                    }
                    
                    return@withLock false
                }

                // 步骤3: 等待连接回调
                Log.i(TAG, "步骤3: 等待连接回调...")
                val success = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                    connectionDeferred?.await()
                } ?: false

                if (!success) {
                    Log.e(TAG, "❌ 绑定超时或失败")
                    // 超时后清理
                    try {
                        context.applicationContext.unbindService(serviceConnection)
                    } catch (_: Exception) {
                        // 忽略解绑异常
                    }
                } else {
                    Log.i(TAG, "✅ Service 绑定成功！")
                }

                Log.i(TAG, "========== 绑定流程结束 ==========")
                return@withLock success
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ 绑定服务失败 (SecurityException): ${e.message}", e)
                Log.e(TAG, "这通常意味着权限不足或 SELinux 策略阻止")
                return@withLock false
            } catch (e: Exception) {
                Log.e(TAG, "❌ 绑定服务异常: ${e.message}", e)
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
                Log.i(TAG, "命令执行成功")
                resultDeferred.complete(output)
            }

            override fun onError(error: String) {
                Log.e(TAG, "服务端返回错误: $error")
                resultDeferred.complete(null)
            }
        }

        try {
            Log.i(TAG, "发送命令: $command")
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
                Log.i(TAG, "已主动解绑服务")
            } catch (e: Exception) {
                Log.w(TAG, "解绑失败: ${e.message}")
            } finally {
                commandService = null
            }
        }
    }
}
