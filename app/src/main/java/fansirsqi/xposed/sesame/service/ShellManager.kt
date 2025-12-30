package fansirsqi.xposed.sesame.service

import android.content.Context
import android.util.Log
import com.niki.cmd.RootShell
import com.niki.cmd.Shell
import com.niki.cmd.ShizukuShell
import com.niki.cmd.UserShell
import com.niki.cmd.model.bean.ShellResult

class ShellManager(context: Context) {

    companion object {
        private const val TAG = "ShellManager"
    }

    private val executors = listOf<Shell>(
        RootShell(),
        ShizukuShell(context),
        UserShell()
    )
    private var selectedShell: Shell? = null
    
    /**
     * 获取当前使用的 Shell 名称
     */
    val selectedName: String
        get() = selectedShell?.javaClass?.simpleName ?: "no_executor"

    private suspend fun selectExecutor() {
        selectedShell = executors.firstOrNull {
            try {
                it.isAvailable()
            } catch (e: Exception) {
                Log.d(TAG, "Shell ${it.javaClass.simpleName} 不可用: ${e.message}")
                false
            }
        }
        Log.d(TAG, "选择的 Shell: $selectedName")
    }

    suspend fun exec(command: String): ShellResult {
        selectExecutor()
        val shell = selectedShell ?: throw Throwable("no shell was chosen")
        Log.d(TAG, "执行命令: $command (使用 $selectedName)")
        return shell.exec(command, 5_000L)
    }
}