package fansirsqi.xposed.sesame.newui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.ui.MainViewModel.Companion.verifuids
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlin.random.Random

@Composable
fun WatermarkLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 1. 获取 M3 主题颜色 (自动适配深浅模式)
    // 使用 onSurface (文字色) 并加上极低的透明度 (0.08~0.15)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f).toArgb()

    // 2. 准备水印文本内容 (使用 remember 缓存，避免重组时重复计算)
    val textLines = remember(verifuids) {
        val prefixLines = listOf("免费模块仅供学习,勿在国内平台传播!!")
        val suffix = "Now: ${TimeUtil.getFormatDateTime()}" // 如果需要时间实时跳动，这里可能需要 LaunchedEffect 更新
        val uidLines = if (verifuids.isEmpty()) {
            listOf("未载入账号", "请启用模块后重启一次支付宝", "确保模块生成对应账号配置")
        } else {
            verifuids.mapIndexed { index, uid -> "UID${index + 1}: $uid" }
        }
        val versionLines = listOf(
            "Ver: ${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
            "Build: ${BuildConfig.BUILD_DATE}", // 稍微简化了一下
        )
        prefixLines + uidLines + listOf(suffix) + versionLines
    }

    // 3. 字体大小转像素
    val density = LocalDensity.current
    val textSizePx = with(density) { 14.sp.toPx() } // M3 推荐用稍小的字号

    // 随机偏移 (保持原有的随机性)
    val offsetX = remember { Random.nextInt(-200, 200).toFloat() }
    val offsetY = remember { Random.nextInt(-200, 200).toFloat() }

    // 4. 使用 Box 布局：内容在下，水印在上
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        // A. 实际的 UI 内容
        content()

        // B. 水印覆盖层 (不拦截点击事件)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 配置画笔
            val paint = Paint().apply {
                color = textColor
                textSize = textSizePx
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
                // 可以设置字体 Typeface
            }

            val fontHeight = paint.fontSpacing
            val totalTextHeight = fontHeight * textLines.size
            val maxLineWidth = textLines.maxOfOrNull { paint.measureText(it) } ?: 0f

            // 密度与间距计算
            val densityFactor = 0.9f
            val horizontalSpacing = (maxLineWidth * 1.5f / densityFactor)
            val verticalSpacing = (totalTextHeight * 2.0f / densityFactor)

            // 旋转角度 (弧度)
            val rotationDegrees = -30f
            Math.toRadians(rotationDegrees.toDouble())

            drawContext.canvas.nativeCanvas.apply {
                withSave {
                    // 整体旋转画布
                    rotate(rotationDegrees, width / 2, height / 2)

                    // 绘制逻辑 (覆盖稍微大一点的区域以防止旋转后边缘留白)
                    var y = -height + offsetY
                    var yIndex = 0

                    while (y < height * 2) {
                        var x = -width + offsetX
                        // 错位平铺
                        if (yIndex % 2 == 1) x += horizontalSpacing / 2

                        while (x < width * 2) {
                            // 绘制多行文本
                            textLines.forEachIndexed { index, line ->
                                drawText(
                                    line,
                                    x,
                                    y + index * fontHeight,
                                    paint
                                )
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
}