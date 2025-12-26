package fansirsqi.xposed.sesame

import android.app.Application
import android.os.Process
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 芝麻粒应用主类
 *
 * 负责应用初始化
 */
class SesameApplication : Application() {

    companion object {
        private const val TAG = "SesameApplication"
        var preferencesKey = "sesame-tk"
        var hasPermissions: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        ToastUtil.init(this) // 初始化全局 Context

        Log.init(this)

        val processName = getCurrentProcessName()
        Log.runtime(TAG, "🚀 应用启动 | 进程: $processName | PID: ${Process.myPid()}")
    }

    /**
     * 获取当前进程名
     */
    private fun getCurrentProcessName(): String {
        return try {
            // Android 9.0+ 可直接获取
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                getProcessName()
            } else {
                // 通过读取 /proc/self/cmdline 获取
                val pid = Process.myPid()
                val cmdlineFile = java.io.File("/proc/$pid/cmdline")
                if (cmdlineFile.exists()) {
                    cmdlineFile.readText().trim('\u0000')
                } else {
                    packageName
                }
            }
        } catch (e: Exception) {
            packageName
        }
    }
}