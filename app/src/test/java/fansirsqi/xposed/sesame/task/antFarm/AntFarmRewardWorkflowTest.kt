package fansirsqi.xposed.sesame.task.antFarm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AntFarmRewardWorkflowTest {

    @Test
    fun `大表鸽奖励回查已领取时确认完成`() = runBlocking {
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { "" },
            listFarmTask = { "" },
            receiveZhimaNpcFarmTaskAward = { """{"success":true}""" },
            listZhimaNpcFarmTask = {
                farmTaskResponse("pigeon-1", "RECEIVED")
            },
            receiveParadiseLimitedActivityAward = { _, _ -> "" },
            queryParadiseLimitedActivity = { "" }
        )

        assertEquals(
            FarmRewardOutcome.CONFIRMED,
            workflow.claimZhimaNpcReward("pigeon-1")
        )
    }

    @Test
    fun `乐园奖励回查已领取时确认完成`() = runBlocking {
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { "" },
            listFarmTask = { "" },
            receiveZhimaNpcFarmTaskAward = { "" },
            listZhimaNpcFarmTask = { "" },
            receiveParadiseLimitedActivityAward = { _, _ ->
                """{"success":true}"""
            },
            queryParadiseLimitedActivity = {
                paradiseResponse("RECEIVED")
            }
        )

        assertEquals(
            FarmRewardOutcome.CONFIRMED,
            workflow.claimParadiseReward(
                AntFarmParadiseLimitedActivity.SIGN_TASK_TYPE,
                awardCount = 10
            )
        )
    }

    @Test
    fun `乐园任务类型缺失时不调用动作并保留重试`() = runBlocking {
        var actionCalls = 0
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { "" },
            listFarmTask = { "" },
            receiveZhimaNpcFarmTaskAward = { "" },
            listZhimaNpcFarmTask = { "" },
            receiveParadiseLimitedActivityAward = { _, _ ->
                actionCalls++
                """{"success":true}"""
            },
            queryParadiseLimitedActivity = { "" }
        )

        val outcome = workflow.claimParadiseReward("", awardCount = 10)

        assertEquals(0, actionCalls)
        assertEquals(FarmRewardOutcome.RETRY, outcome)
    }

    @Test
    fun `大表鸽任务ID缺失时不调用动作并保留重试`() = runBlocking {
        var actionCalls = 0
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { "" },
            listFarmTask = { "" },
            receiveZhimaNpcFarmTaskAward = {
                actionCalls++
                """{"success":true}"""
            },
            listZhimaNpcFarmTask = { "" },
            receiveParadiseLimitedActivityAward = { _, _ -> "" },
            queryParadiseLimitedActivity = { "" }
        )

        val outcome = workflow.claimZhimaNpcReward("")

        assertEquals(0, actionCalls)
        assertEquals(FarmRewardOutcome.RETRY, outcome)
    }

    @Test
    fun `乐园奖励ACK后仍为可领取状态时保留重试`() = runBlocking {
        var queryCalls = 0
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { "" },
            listFarmTask = { "" },
            receiveZhimaNpcFarmTaskAward = { "" },
            listZhimaNpcFarmTask = { "" },
            receiveParadiseLimitedActivityAward = { _, _ ->
                """{"success":true}"""
            },
            queryParadiseLimitedActivity = {
                queryCalls++
                paradiseResponse("FINISHED")
            }
        )

        val outcome = workflow.claimParadiseReward(
            AntFarmParadiseLimitedActivity.SIGN_TASK_TYPE,
            awardCount = 10
        )

        assertEquals(1, queryCalls)
        assertEquals(FarmRewardOutcome.RETRY, outcome)
    }

    @Test
    fun `大表鸽奖励ACK后状态未刷新时保留重试`() = runBlocking {
        var queryCalls = 0
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { "" },
            listFarmTask = { "" },
            receiveZhimaNpcFarmTaskAward = { """{"success":true}""" },
            listZhimaNpcFarmTask = {
                queryCalls++
                farmTaskResponse("pigeon-1", "FINISHED")
            },
            receiveParadiseLimitedActivityAward = { _, _ -> "" },
            queryParadiseLimitedActivity = { "" }
        )

        val outcome = workflow.claimZhimaNpcReward("pigeon-1")

        assertEquals(1, queryCalls)
        assertEquals(FarmRewardOutcome.RETRY, outcome)
    }

    @Test
    fun `多项奖励只领取容量内组合并标记超容量项`() = runBlocking {
        val receivedIds = mutableListOf<String>()
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { taskId ->
                receivedIds += taskId
                """{"success":true}"""
            },
            listFarmTask = {
                farmTaskResponse(receivedIds.last(), "RECEIVED")
            },
            receiveZhimaNpcFarmTaskAward = { "" },
            listZhimaNpcFarmTask = { "" },
            receiveParadiseLimitedActivityAward = { _, _ -> "" },
            queryParadiseLimitedActivity = { "" }
        )

        val result = workflow.claimFarmRewards(
            candidates = listOf(
                FarmRewardCandidate("task-1", 90),
                FarmRewardCandidate("task-2", 180),
                FarmRewardCandidate("task-3", 30)
            ),
            remainingCapacity = 120
        )

        assertEquals(listOf("task-1", "task-3"), receivedIds)
        assertEquals(120, result.confirmedAmount)
        assertEquals(
            FarmRewardOutcome.SKIPPED_CAPACITY,
            result.claims.single { it.candidate.id == "task-2" }.outcome
        )
    }

    @Test
    fun `主任务ACK后回查仍为FINISHED时保留重试`() = runBlocking {
        var queryCalls = 0
        val workflow = AntFarmRewardWorkflow(
            receiveFarmTaskAward = { """{"success":true}""" },
            listFarmTask = {
                queryCalls++
                farmTaskResponse("task-1", "FINISHED")
            },
            receiveZhimaNpcFarmTaskAward = { "" },
            listZhimaNpcFarmTask = { "" },
            receiveParadiseLimitedActivityAward = { _, _ -> "" },
            queryParadiseLimitedActivity = { "" }
        )

        val result = workflow.claimFarmRewards(
            candidates = listOf(FarmRewardCandidate("task-1", 90)),
            remainingCapacity = 180
        )

        assertEquals(1, queryCalls)
        assertEquals(0, result.confirmedAmount)
        assertEquals(
            FarmRewardOutcome.RETRY,
            result.claims.single().outcome
        )
    }

    private fun farmTaskResponse(taskId: String, status: String): String {
        return """
            {
              "resultCode": "SUCCESS",
              "data": {
                "farmTaskList": [{
                  "taskId": "$taskId",
                  "taskStatus": "$status"
                }]
              }
            }
        """.trimIndent()
    }

    private fun paradiseResponse(status: String): String {
        return """
            {
              "success": true,
              "taskTriggerPlayInfo": {
                "taskList": [{
                  "sceneCode": "${AntFarmParadiseLimitedActivity.SCENE_CODE}",
                  "taskType": "${AntFarmParadiseLimitedActivity.SIGN_TASK_TYPE}",
                  "taskStatus": "$status",
                  "awardCount": 10
                }]
              }
            }
        """.trimIndent()
    }
}
