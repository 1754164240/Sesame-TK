package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForestMultiplierIntegrationTest {
    private val source = File(
        "src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt"
    ).readText()

    @Test
    fun `倍卡入口使用工作流并移除本地假终态`() {
        assertTrue(source.contains("private fun useRobMultiplierCard("))
        assertTrue(source.contains("ForestMultiplierWorkflow("))
        assertTrue(source.contains("queryPropList(true)"))
        assertTrue(source.contains("needRefreshHome = false"))
        assertTrue(source.contains("confirmedActive.endTime"))
        assertFalse(source.contains("private fun userobExpandCard("))
        assertFalse(
            source.contains(
                "robExpandCardEndTime = System.currentTimeMillis() + 1000 * 60 * 5"
            )
        )
    }

    @Test
    fun `倍卡入口使用独立时间调度和可续用策略`() {
        assertTrue(
            source.contains("ForestMultiplierSchedulePolicy.scheduledPoints")
        )
        assertTrue(
            source.contains("ForestMultiplierSchedulePolicy.isRegularUseAllowed")
        )
        assertTrue(source.contains("ROB_EXPAND|$" + "targetTime"))
        assertTrue(
            source.contains(
                "return ForestMultiplierPolicy.isRenewablePropType(propType)"
            )
        )
    }
}
