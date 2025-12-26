package fansirsqi.xposed.sesame.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import fansirsqi.xposed.sesame.R

@Composable
fun LogViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 从 XML 读取颜色
    val primary = Color(ContextCompat.getColor(context, R.color.colorPrimary))
    val background = Color(ContextCompat.getColor(context, R.color.background))
    val textPrimary = Color(ContextCompat.getColor(context, R.color.textColorPrimary))
    val activeText = Color(ContextCompat.getColor(context, R.color.active_text))

    // 配置深色模式颜色 (手动适配或读取 values-night)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = activeText,
            background = Color(0xFF121212), //以此为准，或者定义在 xml 的 values-night 中
            onBackground = Color(0xFFE0E0E0),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE0E0E0)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            background = background,
            onBackground = textPrimary,
            surface = Color.White,
            onSurface = textPrimary
        )
    }

    // 设置状态栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 修复：statusBarColor 在 API 35 (Android 15) 被弃用，这里压制警告即可
            // 或者你可以加判断：if (Build.VERSION.SDK_INT < 35) { ... }
            @Suppress("DEPRECATION")
            window.statusBarColor = primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}