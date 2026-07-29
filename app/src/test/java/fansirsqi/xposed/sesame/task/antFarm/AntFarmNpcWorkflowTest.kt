package fansirsqi.xposed.sesame.task.antFarm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AntFarmNpcWorkflowTest {

    @Test
    fun `无NPC时雇佣后必须回查目标出现`() = runBlocking {
        val queries = ArrayDeque(listOf(farmResponse(), farmResponse(npc("target", 0.0))))
        var hireCount = 0
        val workflow = workflow(
            queries = queries,
            hire = { _, _ -> hireCount++; success() }
        )

        val result = workflow.run("target", "source", 88.0)

        assertEquals(FarmNpcOutcome.CONFIRMED, result.outcome)
        assertEquals(FarmNpcAction.HIRE, result.action)
        assertEquals(1, hireCount)
    }

    @Test
    fun `雇佣ACK后目标未出现返回重试`() = runBlocking {
        val queries = ArrayDeque(listOf(farmResponse(), farmResponse()))
        val result = workflow(queries).run("target", "source", 88.0)

        assertEquals(FarmNpcOutcome.RETRY, result.outcome)
    }

    @Test
    fun `切换NPC必须先确认旧NPC消失`() = runBlocking {
        val old = npc("old", 10.0)
        val queries = ArrayDeque(listOf(farmResponse(old), farmResponse(old)))
        var hireCount = 0
        val workflow = workflow(
            queries = queries,
            hire = { _, _ -> hireCount++; success() }
        )

        val result = workflow.run("target", "source", 88.0)

        assertEquals(FarmNpcOutcome.RETRY, result.outcome)
        assertEquals(0, hireCount)
    }

    @Test
    fun `满产领取后确认消失并重雇目标`() = runBlocking {
        val queries = ArrayDeque(
            listOf(
                farmResponse(npc("target", 88.0)),
                farmResponse(),
                farmResponse(npc("target", 0.0))
            )
        )
        var taskRuns = 0
        val workflow = workflow(queries)

        val result = workflow.run(
            targetAnimalId = "target",
            source = "source",
            rewardThreshold = 88.0,
            onTargetPresent = { taskRuns++ }
        )

        assertEquals(FarmNpcOutcome.CONFIRMED, result.outcome)
        assertEquals(FarmNpcAction.CLAIM_AND_REHIRE, result.action)
        assertEquals(1, taskRuns)
    }

    @Test
    fun `领取后奖励未下降立即停止`() = runBlocking {
        val full = npc("target", 88.0)
        val queries = ArrayDeque(listOf(farmResponse(full), farmResponse(full)))
        var hireCount = 0
        val workflow = workflow(
            queries = queries,
            hire = { _, _ -> hireCount++; success() }
        )

        val result = workflow.run("target", "source", 88.0)

        assertEquals(FarmNpcOutcome.RETRY, result.outcome)
        assertEquals(0, hireCount)
    }

    @Test
    fun `目标NPC奖励未满只处理任务不执行生命周期动作`() = runBlocking {
        val queries = ArrayDeque(listOf(farmResponse(npc("target", 10.0))))
        var taskRuns = 0
        var sendBackCount = 0
        val workflow = workflow(
            queries = queries,
            sendBack = { sendBackCount++; success() }
        )

        val result = workflow.run(
            "target",
            "source",
            88.0,
            onTargetPresent = { taskRuns++ }
        )

        assertEquals(FarmNpcOutcome.NO_ACTION, result.outcome)
        assertEquals(1, taskRuns)
        assertEquals(0, sendBackCount)
    }

    private fun workflow(
        queries: ArrayDeque<String>,
        hire: suspend (String, String) -> String = { _, _ -> success() },
        sendBack: suspend (FarmNpcAnimalSnapshot) -> String = { success() }
    ): AntFarmNpcWorkflow {
        return AntFarmNpcWorkflow(
            queryFarm = { queries.removeFirst() },
            hireNpc = hire,
            sendBackNpc = sendBack
        )
    }

    private fun success() = """{"success":true}"""

    private fun farmResponse(animal: String? = null): String {
        val animals = animal?.let { "[$it]" } ?: "[]"
        return """{"success":true,"subFarmVO":{"farmId":"farm","animals":$animals}}"""
    }

    private fun npc(animalId: String, reward: Double): String {
        return """
            {
              "animalId":"$animalId",
              "subAnimalType":"NPC",
              "currentFarmId":"farm",
              "masterFarmId":"master",
              "npcBizReward":$reward,
              "reachNpcBizRewardLimit":false
            }
        """.trimIndent()
    }
}
