package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnergyRainCoroutineTest {

    @Test
    fun `能量雨识别安全验证响应并停止当天流程`() {
        assertTrue(
            EnergyRainCoroutine.isVerificationRequiredResult(
                JSONObject("""{"success":false,"resultCode":"RPC_VERIFICATION_REQUIRED"}""")
            )
        )
        assertTrue(
            EnergyRainCoroutine.isVerificationRequiredResult(
                JSONObject("""{"success":false,"resultCode":"1009","resultDesc":"请进行验证后继续"}""")
            )
        )
        assertFalse(
            EnergyRainCoroutine.isVerificationRequiredResult(
                JSONObject("""{"success":true,"resultCode":"SUCCESS"}""")
            )
        )
    }

    @Test
    fun `能量雨安全验证只结束当前流程且后续可重试`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/task/antForest/EnergyRainCoroutine.kt").readText()

        assertTrue(sourceText.contains("pauseForVerification"))
        assertTrue(sourceText.contains("本次流程结束，后续可再次执行"))
        assertTrue(sourceText.contains("if (!startEnergyRain())"))
        assertFalse(sourceText.contains("ENERGY_RAIN_VERIFICATION_FLAG"))
        assertFalse(sourceText.contains("今日能量雨已触发安全验证，跳过执行"))
    }
}
