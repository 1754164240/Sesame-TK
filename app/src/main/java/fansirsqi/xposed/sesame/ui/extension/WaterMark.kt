package fansirsqi.xposed.sesame.ui.extension

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.ui.viewmodel.MainViewModel.Companion.verifuids
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun WatermarkLayer(
    modifier: Modifier = Modifier,
    // 🔥 核心修改：接收 UID 列表作为参数，而不是读取静态变量
    uidList: List<String?> = verifuids,
    autoRefresh: Boolean = true,
    refreshIntervalMs: Long = 1000L,
    refreshTrigger: Any? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { 13.sp.toPx() }
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f).toArgb()

    var currentTime by remember { mutableStateOf(TimeUtil.getFormatDateTime()) }

    if (autoRefresh) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(refreshIntervalMs)
                currentTime = TimeUtil.getFormatDateTime()
            }
        }
    }

    // 4. 计算文本行
    // 🔥 依赖项改为传入的 uidList
    val textLines = remember(uidList, currentTime, refreshTrigger) {
        val prefixLines = listOf("免费模块仅供学习,勿在国内平台传播!!")
        val suffix = "Now: $currentTime"

        // 使用传入的 uidList 进行判断
        val uidLines = if (uidList.isEmpty()) {
            listOf("未载入账号", "请启用模块后重启一次目标应用", "确保模块生成对应账号配置")
        } else {
            uidList.mapIndexed { index, uid -> "UID${index + 1}: $uid" }
        }

        val versionLines = listOf(
            "Ver: ${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
            "Build: ${BuildConfig.BUILD_DATE}",
        )

        prefixLines + uidLines + listOf(suffix) + versionLines
    }

    val offsetX = remember { Random.nextInt(-200, 200).toFloat() }
    val offsetY = remember { Random.nextInt(-200, 200).toFloat() }

    Box(
        modifier = modifier.drawWithCache {
            // ... (Paint 和 draw 逻辑保持不变，完全不需要改动) ...
            val paint = Paint().apply {
                color = textColor
                textSize = textSizePx
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }

            val fontHeight = paint.fontSpacing
            val maxLineWidth = textLines.maxOfOrNull { paint.measureText(it) } ?: 0f
            val totalTextHeight = fontHeight * textLines.size

            val densityFactor = 0.9f
            val horizontalSpacing = (maxLineWidth * 1.5f / densityFactor)
            val verticalSpacing = (totalTextHeight * 2.5f / densityFactor)
            val rotationDegrees = -30f

            onDrawWithContent {
                drawContent()
                drawContext.canvas.nativeCanvas.apply {
                    withSave {
                        val width = size.width
                        val height = size.height
                        rotate(rotationDegrees, width / 2, height / 2)
                        var y = -height + offsetY
                        var yIndex = 0
                        while (y < height * 2) {
                            var x = -width + offsetX
                            if (yIndex % 2 == 1) x += horizontalSpacing / 2
                            while (x < width * 2) {
                                textLines.forEachIndexed { index, line ->
                                    drawText(line, x, y + index * fontHeight, paint)
                                }
                                x += horizontalSpacing
                            }
                            y += verticalSpacing
                            yIndex++
                        }
                    }
                }
            }
        }
    ) {
        content()
    }
}