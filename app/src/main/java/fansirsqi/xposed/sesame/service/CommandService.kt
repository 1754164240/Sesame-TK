package fansirsqi.xposed.sesame.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.IOException

/**
 * 命令执行服务
 * 使用 Shizuku (adb shell) 执行命令
 */
class CommandService : Service() {

    companion object {
        private const val TAG = "CommandService"

        // 设置命令执行超时时间，例如 15 秒
        private const val COMMAND_TIMEOUT_MS = 15000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val commandMutex = Mutex()

    private val binder = object : ICommandService.Stub() {
        override fun executeCommand(command: String, callback: ICallback?) {
            Log.d(TAG, "收到命令执行请求: $command")

            serviceScope.launch {
                // 使用互斥锁防止并发冲突
                commandMutex.withLock {
                    var process: Process? = null
                    try {
                        // 🔥 核心修改：使用 withTimeout 包裹代码块，实现真正的超时控制
                        withTimeout(COMMAND_TIMEOUT_MS) {
                            Log.d(TAG, "开始执行命令: $command")

                            // 1. 尝试创建进程 (Shizuku)
                            process = createProcess(command)

                            if (process == null) {
                                throw IOException("Shizuku 服务未运行")
                            }

                            val output = StringBuilder()
                            val error = StringBuilder()

                            // 2. 异步读取标准输出流
                            val outputJob = launch(Dispatchers.IO) {
                                try {
                                    process!!.inputStream.bufferedReader().use { reader ->
                                        reader.forEachLine { line ->
                                            output.append(line).append("\n")
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 忽略流关闭异常
                                }
                            }

                            // 3. 异步读取错误输出流
                            val errorJob = launch(Dispatchers.IO) {
                                try {
                                    process!!.errorStream.bufferedReader().use { reader ->
                                        reader.forEachLine { line ->
                                            error.append(line).append("\n")
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 忽略流关闭异常
                                }
                            }

                            // 4. 等待进程结束
                            // waitFor() 会阻塞当前协程，直到进程退出
                            // 如果卡住，外层的 withTimeout 会把它取消掉
                            val exitCode = withContext(Dispatchers.IO) {
                                process!!.waitFor()
                            }

                            // 等待流读取完毕
                            outputJob.join()
                            errorJob.join()

                            if (exitCode == 0) {
                                Log.d(TAG, "命令执行成功: $command")
                                callback?.onSuccess(output.toString().trim())
                            } else {
                                Log.e(TAG, "命令执行失败: $command, 退出码: $exitCode, 错误: $error")
                                callback?.onError("退出码: $exitCode, 错误: $error")
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.e(TAG, "命令执行超时 (${COMMAND_TIMEOUT_MS}ms): $command")
                        callback?.onError("命令执行超时")
                        // 超时后强制杀掉进程
                        process?.destroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "执行命令异常: $command, 错误: ${e.message}")
                        callback?.onError(e.message ?: "未知错误")
                    } finally {
                        // 确保进程资源被释放
                        try {
                            process?.destroy()
                        } catch (ignored: Exception) {
                        }
                        Log.d(TAG, "命令执行流程结束: $command")
                    }
                }
            }
        }
    }

    /**
     * 创建进程的辅助方法
     * 使用 Shizuku (ADB Shell) 执行命令
     */
    private fun createProcess(command: String): Process? {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku 服务未运行或未授权")
            return null
        }

        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            return method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku 启动失败: ${e.message}")
            e.printStackTrace()
        }

        return null
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "CommandService 绑定")
        // Shizuku 初始化监听（可选，防止绑定过早 Shizuku 还没准备好）
        if (Shizuku.pingBinder()) {
            // Shizuku 已经就绪
            Log.i(TAG, "Shizuku 已经就绪")
        }
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "CommandService 解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // 销毁时取消所有协程任务
        Log.d(TAG, "CommandService 销毁")
    }
}