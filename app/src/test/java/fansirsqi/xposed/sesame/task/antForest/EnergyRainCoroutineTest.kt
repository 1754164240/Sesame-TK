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
    fun `能量雨结算安全验证后不会设置当天暂停标记`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/task/antForest/EnergyRainCoroutine.kt").readText()

        assertTrue(sourceText.contains("ENERGY_RAIN_VERIFICATION_FLAG"))
        assertTrue(sourceText.contains("pauseForVerification"))
        assertTrue(sourceText.contains("Status.hasFlagToday(ENERGY_RAIN_VERIFICATION_FLAG)"))
        assertTrue(sourceText.contains("Status.setFlagToday(ENERGY_RAIN_VERIFICATION_FLAG)"))
        assertTrue(sourceText.contains("finishSettlementForVerification"))
        assertFalse(sourceText.contains("pauseForVerification(\"结算\", resultJson)"))
    }
}
