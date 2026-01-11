package fansirsqi.xposed.sesame.ui.log

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fansirsqi.xposed.sesame.newui.WatermarkLayer
import fansirsqi.xposed.sesame.ui.theme.AppTheme

/**
 * 承载 Compose 日志查看器的 Activity
 */
class LogViewerComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.data?.path ?: ""
        setContent {
            // 使用暗色主题，因为 LogViewerScreen 的背景硬编码为了深色 (0xFF1E1E1E)
            AppTheme {
                WatermarkLayer {
                    LogViewerScreen(
                        filePath = path,
                        onBackClick = {
                            // 点击返回箭头时关闭当前页面
                            finish()
                        }
                    )
                }
            }
        }
    }
}