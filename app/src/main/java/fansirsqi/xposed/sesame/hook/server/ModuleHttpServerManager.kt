package fansirsqi.xposed.sesame.hook.server

import fansirsqi.xposed.sesame.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * 内置 HTTP 服务管理器（单例）。
 * - 仅允许在主进程启动
 * - 幂等启动，避免重复绑定端口导致 EADDRINUSE
 * - 提供安全的停止方法
 */
object ModuleHttpServerManager {
    private const val TAG = "ModuleHttpServerManager"

    @Volatile
    private var server: ModuleHttpServer? = null

    /**
     * 启动内置 HTTP 服务（仅主进程，幂等）。
     * @param port 监听端口
     * @param secretToken 认证 Token
     * @param processName 当前进程名
     * @param packageName 应用包名（主进程名）
     * @return true 表示服务处于运行中（新启动或已在运行）；false 表示非主进程或启动失败
     */
    @Synchronized
    fun startIfNeeded(
        port: Int,
        secretToken: String,
        processName: String,
        packageName: String
    ): Boolean {
        // 仅主进程启动，避免子进程重复创建导致端口占用
        if (processName != packageName) {
            Log.record(TAG, "非主进程，无需启动内置 HTTP 服务: $processName")
            return false
        }

        if (server != null) {
            Log.record(TAG, "HTTP 服务已在运行，跳过重复创建")
            return true
        }

        return try {
            val s = ModuleHttpServer(port, secretToken)
            // 使用与原逻辑一致的启动方式
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = s
            Log.record(TAG, "HTTP 服务启动成功，端口: $port")
            true
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "HTTP 服务启动失败:", t)
            false
        }
    }

    /**
     * 停止内置 HTTP 服务（若在运行）。
     */
    @Synchronized
    fun stopIfRunning() {
        try {
            server?.stop()
            if (server != null) {
                Log.record(TAG, "HTTP 服务已停止")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "HTTP 服务停止异常:", t)
        } finally {
            server = null
        }
    }

    /**
     * 查询服务是否运行中。
     */
    fun isRunning(): Boolean = server != null
}


