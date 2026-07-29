package fansirsqi.xposed.sesame.task.antOrchard

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AntOrchardRewardWorkflowTest {

    @Test
    fun `乐园奖励领取后回查RECEIVED才确认`() = runBlocking {
        var claimCalls = 0
        var queryCalls = 0
        val workflow = AntOrchardRewardWorkflow(
            listTasks = { "" },
            finishTask = { "" },
            claimTask = { "" },
            queryLeyuanTasks = {
                queryCalls++
                leyuanResponse("RECEIVED")
            },
            claimLeyuanTask = {
                claimCalls++
                """{"resultCode":"100"}"""
            }
        )
        val task = AntOrchardRewardPolicy.parseLeyuanTasks(
            leyuanResponse("FINISHED")
        ).tasks.single()

        val outcome = workflow.claimLeyuanReward(task)

        assertEquals(1, claimCalls)
        assertEquals(1, queryCalls)
        assertEquals(AntOrchardRewardOutcome.CONFIRMED, outcome)
    }

    @Test
    fun `安全任务完成后回查FINISHED才确认`() = runBlocking {
        var finishCalls = 0
        var queryCalls = 0
        val workflow = AntOrchardRewardWorkflow(
            listTasks = {
                queryCalls++
                taskResponse("task-1", "FINISHED")
            },
            finishTask = {
                finishCalls++
                """{"resultCode":"100"}"""
            },
            claimTask = { "" },
            queryLeyuanTasks = { "" },
            claimLeyuanTask = { "" }
        )

        val outcome = workflow.processTask(
            AntOrchardRewardPolicy.parseTasks(
                taskResponse("task-1", "TODO")
            ).tasks.single()
        )

        assertEquals(1, finishCalls)
        assertEquals(1, queryCalls)
        assertEquals(AntOrchardRewardOutcome.CONFIRMED, outcome)
    }

    @Test
    fun `领奖ACK后任务仍为FINISHED时保留重试`() = runBlocking {
        var claimCalls = 0
        var queryCalls = 0
        val workflow = AntOrchardRewardWorkflow(
            listTasks = {
                queryCalls++
                taskResponse("task-1", "FINISHED")
            },
            finishTask = { "" },
            claimTask = {
                claimCalls++
                """{"resultCode":"100"}"""
            },
            queryLeyuanTasks = { "" },
            claimLeyuanTask = { "" }
        )

        val outcome = workflow.processTask(
            AntOrchardRewardPolicy.parseTasks(
                taskResponse("task-1", "FINISHED")
            ).tasks.single()
        )

        assertEquals(1, claimCalls)
        assertEquals(1, queryCalls)
        assertEquals(AntOrchardRewardOutcome.RETRY, outcome)
    }

    private fun taskResponse(taskId: String, status: String): String {
        return """
            {
              "resultCode":"100",
              "taskList":[{
                "taskId":"$taskId",
                "groupId":"group-1",
                "taskStatus":"$status",
                "actionType":"TRIGGER",
                "sceneCode":"ORCHARD",
                "taskPlantType":"NORMAL",
                "awardCount":10,
                "taskDisplayConfig":{"title":"免费任务"}
              }]
            }
        """.trimIndent()
    }

    private fun leyuanResponse(status: String): String {
        return """
            {
              "success":true,
              "taskTriggerPlayInfo":{
                "taskList":[{
                  "sceneCode":"ANTORCHARD_LEYUAN_DAILY_TASK",
                  "taskType":"DAILY_LEYUAN_QIANDAO",
                  "taskStatus":"$status",
                  "title":"每日签到",
                  "awardCount":20
                }]
              }
            }
        """.trimIndent()
    }
}
