package fansirsqi.xposed.sesame.newui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.data.ViewAppInfo.verifuids
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlin.random.Random

class WatermarkView(context: Context) : View(context) {

    private val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    // 定义白天和黑夜的颜色
    private var dayColor = "#3e273f47".toColorInt()   // 深灰蓝（适合亮色背景）
    private var nightColor = "#808a9a9e".toColorInt() // 浅灰（适合暗色背景）

    private var textLines: List<String> = emptyList()

    var watermarkText: String? = null
        set(_) {
            val prefixLines = mutableListOf(
                "免费模块仅供学习,勿在国内平台传播!!"
            )
            val suffix = "Now: ${TimeUtil.getFormatDateTime()}"
            val uidLines = if (verifuids.isEmpty()) {
                listOf(
                    "未载入账号",
                    "请启用模块后重启一次支付宝",
                    "确保模块生成对应账号配置"
                )
            } else {
                verifuids.mapIndexed { index, uid ->
                    "UID${index + 1}: $uid"
                }
            }
            val combinedLines = prefixLines + uidLines + suffix + versionLines
            field = combinedLines.joinToString("\n")
            textLines = combinedLines
            updateTextColor() // 根据当前模式更新颜色
            invalidate()
        }

    /** 控制整体水印稀疏度 */
    private var densityFactor: Float = 0.1f

    /** 旋转角度 */
    var rotationAngle: Float = -30f

    init {
        watermarkText = watermarkText
    }

    fun setInfo(tags: List<String>) {
        watermarkText = buildString {
            appendLine(tags.joinToString(" | "))
        }
    }

    fun setWatermarkStyle(color: Int, size: Float) {
        paint.color = color
        paint.textSize = size
        invalidate()
    }

    fun setDayNightColors(day: Int, night: Int) {
        dayColor = day
        nightColor = night
        updateTextColor()
    }

    fun setDensity(density: Float = 1f) {
        densityFactor = density.coerceAtLeast(0.1f)
        invalidate()
    }

    private fun updateTextColor() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        paint.color = if (isNight) nightColor else dayColor
    }

    private val offsetX = Random.nextInt(-200, 200)
    private val offsetY = Random.nextInt(-200, 200)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || textLines.isEmpty()) return

        val maxLineWidth = textLines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val lineHeight = paint.fontSpacing
        val totalTextHeight = lineHeight * textLines.size

        var horizontalSpacing = (maxLineWidth * 1.3f / densityFactor).toInt()
        var verticalSpacing = (totalTextHeight * 2.3f / densityFactor).toInt()

        horizontalSpacing = horizontalSpacing.coerceAtMost(width)
        verticalSpacing = verticalSpacing.coerceAtMost(height)

        var yIndex = 0
        canvas.withRotation(rotationAngle) {
            var y = -height.toFloat() + offsetY
            while (y < height * 2) {
                var x = -width.toFloat() + offsetX
                if (yIndex % 2 == 1) x += horizontalSpacing / 2

                while (x < width * 2) {
                    val baseY = y - totalTextHeight / 2
                    for ((i, line) in textLines.withIndex()) {
                        drawText(line, x, baseY + i * lineHeight, paint)
                    }
                    x += horizontalSpacing
                }
                y += verticalSpacing
                yIndex++
            }
        }
    }

    /**
     * 刷新水印：重新检测日夜模式并重绘
     */
    fun refresh() {
        updateTextColor()
        invalidate()
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun install(
            activity: Activity,
            text: String = "",
            dayColor: Int = "#3e273f47".toColorInt(),   // 明确命名：白天色
            nightColor: Int = "#7e8a9a9e".toColorInt(), // 明确命名：夜色
            fontSize: Float = 42f,
            density: Float = 0.9f
        ): WatermarkView {
            val watermarkView = WatermarkView(activity).apply {
                // 先设置日夜颜色（必须在 watermarkText 之前！）
                setDayNightColors(day = dayColor, night = nightColor)
                // 再设置文本，内部会调用 updateTextColor()
                watermarkText = text
                paint.textSize = fontSize
                setDensity(density)
            }
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(
                watermarkView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return watermarkView
        }

        /** 模块版本和构建时间的固定信息行 */
        private val versionLines = listOf(
            "Ver: ${BuildConfig.VERSION_NAME}.${BuildConfig.BUILD_TYPE} 📦",
            "Build: ${BuildConfig.BUILD_DATE} ${BuildConfig.BUILD_TIME} ⏰",
            "免费模块仅供学习,勿在国内平台传播!!"
        )
    }
}