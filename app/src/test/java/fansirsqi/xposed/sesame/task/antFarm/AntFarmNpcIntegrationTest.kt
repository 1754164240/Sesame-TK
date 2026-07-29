package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AntFarmNpcIntegrationTest {
    private val source = File(
        "src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarm.kt"
    ).readText()

    @Test
    fun `NPC生产入口通过生命周期工作流回查`() {
        assertTrue(source.contains("AntFarmNpcWorkflow("))
        assertTrue(source.contains("runNpcTasks(targetConfig)"))
        assertTrue(source.contains("waitForRefresh = { delay(1500) }"))
        assertFalse(source.contains("private fun hireNpc("))
        assertFalse(source.contains("private suspend fun checkRewardAndTask("))
    }

    @Test
    fun `大表鸽任务奖励仍由独立奖励工作流处理`() {
        assertTrue(source.contains("createFarmRewardWorkflow()"))
        assertTrue(source.contains("workflow.claimZhimaNpcReward(taskId)"))
    }
}
