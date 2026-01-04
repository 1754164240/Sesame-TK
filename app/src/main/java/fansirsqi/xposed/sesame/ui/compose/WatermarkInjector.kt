package fansirsqi.xposed.sesame.ui.compose

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import fansirsqi.xposed.sesame.newui.WatermarkLayer
import fansirsqi.xposed.sesame.ui.theme.AppTheme

/**
 * 专门供 Java Activity 调用的辅助类
 */
object WatermarkInjector {
    @JvmStatic
    fun inject(activity: Activity) {
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // 使用 AppTheme 确保颜色正确 (如 onSurface)
                AppTheme {
                    // 水印层作为覆盖层
                    WatermarkLayer(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 内容为空，因为我们只是要把水印盖在原来的 View 上
                        // Canvas 默认不拦截点击，所以点击事件会穿透到下面的 View
                    }
                }
            }
        }

        // 添加到根布局
        activity.addContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}