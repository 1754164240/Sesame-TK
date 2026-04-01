package fansirsqi.xposed.sesame.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fansirsqi.xposed.sesame.data.Config
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.task.customTasks.CustomTask
import fansirsqi.xposed.sesame.ui.extension.WatermarkLayer
import fansirsqi.xposed.sesame.ui.screen.ManualTaskScreen
import fansirsqi.xposed.sesame.ui.theme.AppTheme
import fansirsqi.xposed.sesame.ui.theme.ThemeManager
import fansirsqi.xposed.sesame.ui.viewmodel.MainViewModel
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 手动任务 Fragment (Compose 实现)
 * 采用列表展示所有可用的子任务，点击即可运行
 */
class ManualTaskActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化配置
        ensureConfigLoaded()

        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                WatermarkLayer(uidList = MainViewModel.verifuids) {
                    ManualTaskScreen(
                        onBackClick = { finish() },
                        onTaskClick = { task, params -> runTask(task, params) }
                    )
                }
            }
        }
    }

    private fun ensureConfigLoaded() {
        Model.initAllModel()
        val activeUser = DataStore.get("activedUser", UserEntity::class.java)
        activeUser?.userId?.let { uid ->
            Config.load(uid)
        }
    }

    private fun runTask(task: CustomTask, params: Map<String, Any>) {
        try {
            val intent = Intent("com.eg.android.AlipayGphone.sesame.manual_task")
            intent.putExtra("task", task.name)
            params.forEach { (key, value) ->
                when (value) {
                    is Int -> intent.putExtra(key, value)
                    is String -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                }
            }
            sendBroadcast(intent)
            ToastUtil.showToast(this, "🚀 已发送指令: ${task.displayName}")
            openRecordLog()
        } catch (e: Exception) {
            ToastUtil.showToast(this, "❌ 发送失败: ${e.message}")
        }
    }

    private fun openRecordLog() {
        val logFile = Files.getRecordLogFile()
        if (!logFile.exists()) {
            ToastUtil.showToast(this, "日志文件尚未生成")
            return
        }
        val intent = Intent(this, LogViewerActivity::class.java).apply {
            data = logFile.toUri()
        }
        startActivity(intent)
    }
}




