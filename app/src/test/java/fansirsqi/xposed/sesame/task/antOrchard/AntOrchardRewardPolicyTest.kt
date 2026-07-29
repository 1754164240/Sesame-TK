package fansirsqi.xposed.sesame.task.antOrchard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntOrchardRewardPolicyTest {

    @Test
    fun `限时挑战奖励按回查任务终态确认`() {
        assertFalse(
            AntOrchardRewardPolicy.isLimitedRewardConfirmed(
                limitedChallengeResponse("task-1", "FINISHED"),
                "task-1"
            )
        )
        assertTrue(
            AntOrchardRewardPolicy.isLimitedRewardConfirmed(
                limitedChallengeResponse("task-1", "RECEIVED"),
                "task-1"
            )
        )
        assertTrue(
            AntOrchardRewardPolicy.isLimitedRewardConfirmed(
                """{"success":true,"limitedTimeChallenge":{"limitedTimeChallengeTasks":[]}}""",
                "task-1"
            )
        )
        assertFalse(
            AntOrchardRewardPolicy.isLimitedRewardConfirmed(
                """{"success":true,"data":{}}""",
                "task-1"
            )
        )
    }

    @Test
    fun `解析农场乐园奖励并按服务端终态确认`() {
        val finished = leyuanResponse("FINISHED", "nextStageAwardCount", 20)
        val task = AntOrchardRewardPolicy.parseLeyuanTasks(finished)
            .tasks
            .single()

        assertEquals("ANTORCHARD_LEYUAN_DAILY_TASK", task.sceneCode)
        assertEquals("DAILY_LEYUAN_QIANDAO", task.taskType)
        assertEquals(20, task.awardCount)
        assertTrue(AntOrchardRewardPolicy.isLeyuanClaimable(task))
        assertFalse(
            AntOrchardRewardPolicy.isLeyuanRewardConfirmed(
                finished,
                task.sceneCode,
                task.taskType
            )
        )
        assertTrue(
            AntOrchardRewardPolicy.isLeyuanRewardConfirmed(
                leyuanResponse("RECEIVED", "awardCount", 20),
                task.sceneCode,
                task.taskType
            )
        )
        assertTrue(
            AntOrchardRewardPolicy.isLeyuanRewardConfirmed(
                """{"success":true,"taskTriggerPlayInfo":{"taskList":[]}}""",
                task.sceneCode,
                task.taskType
            )
        )
        assertFalse(
            AntOrchardRewardPolicy.isLeyuanRewardConfirmed(
                """{"success":true,"data":{}}""",
                task.sceneCode,
                task.taskType
            )
        )
    }

    @Test
    fun `动作响应必须明确成功且完成状态必须服务端推进`() {
        assertFalse(AntOrchardRewardPolicy.isActionAccepted(""))
        assertFalse(AntOrchardRewardPolicy.isActionAccepted("{}"))
        assertFalse(
            AntOrchardRewardPolicy.isActionAccepted(
                """{"success":false}"""
            )
        )
        assertTrue(
            AntOrchardRewardPolicy.isActionAccepted(
                """{"resultCode":"100"}"""
            )
        )
        assertFalse(
            AntOrchardRewardPolicy.isCompletionConfirmed(
                taskStatusResponse("task-1", "TODO"),
                "task-1"
            )
        )
        assertTrue(
            AntOrchardRewardPolicy.isCompletionConfirmed(
                taskStatusResponse("task-1", "FINISHED"),
                "task-1"
            )
        )
        assertFalse(
            AntOrchardRewardPolicy.isCompletionConfirmed(
                """{"resultCode":"100","taskList":[]}""",
                "task-1"
            )
        )
    }

    @Test
    fun `奖励只有RECEIVED或已识别列表中消失才确认`() {
        val finished = taskStatusResponse("task-1", "FINISHED")
        val received = taskStatusResponse("task-1", "RECEIVED")
        val disappeared = """{"resultCode":"100","taskList":[]}"""
        val unknown = """{"resultCode":"100","data":{}}"""

        assertFalse(
            AntOrchardRewardPolicy.isRewardConfirmed(finished, "task-1")
        )
        assertTrue(
            AntOrchardRewardPolicy.isRewardConfirmed(received, "task-1")
        )
        assertTrue(
            AntOrchardRewardPolicy.isRewardConfirmed(disappeared, "task-1")
        )
        assertFalse(
            AntOrchardRewardPolicy.isRewardConfirmed(unknown, "task-1")
        )
    }

    @Test
    fun `仅白名单免费动作可完成且危险任务统一跳过`() {
        val safeActions = listOf("TRIGGER", "ADD_HOME", "PUSH_SUBSCRIBE")
        val unsafeActions = listOf(
            "GAME_CENTER",
            "XLIGHT",
            "VISIT",
            "RECHARGE",
            "ORDER",
            "PURCHASE",
            "MULTI_STAGE",
            "UNKNOWN"
        )

        for (action in safeActions) {
            assertEquals(
                AntOrchardTaskDecision.COMPLETE,
                AntOrchardRewardPolicy.decideTask(
                    parseSingleTask(actionType = action)
                )
            )
        }
        for (action in unsafeActions) {
            assertEquals(
                AntOrchardTaskDecision.SKIP_UNSAFE,
                AntOrchardRewardPolicy.decideTask(
                    parseSingleTask(actionType = action)
                )
            )
        }
        assertEquals(
            AntOrchardTaskDecision.SKIP_UNSAFE,
            AntOrchardRewardPolicy.decideTask(
                parseSingleTask(
                    actionType = "TRIGGER",
                    title = "完成充值并下单"
                )
            )
        )
    }

    @Test
    fun `已完成任务只进入免费领奖`() {
        assertEquals(
            AntOrchardTaskDecision.CLAIM,
            AntOrchardRewardPolicy.decideTask(
                parseSingleTask(
                    actionType = "GAME_CENTER",
                    status = "FINISHED"
                )
            )
        )
    }

    @Test
    fun `解析根对象与data result中的多种任务容器`() {
        val responses = listOf(
            """{"resultCode":"100","taskList":[${task("root-1")}]}""",
            """{"success":true,"data":{"orchardTaskList":[${task("data-1")}]}}""",
            """{"success":true,"result":{"dailyTaskList":[${task("result-1")}]}}""",
            """{"success":true,"data":{"tasks":[${task("tasks-1")}]}}"""
        )

        val snapshots = responses.map(
            AntOrchardRewardPolicy::parseTasks
        )

        assertTrue(snapshots.all { it.recognized })
        assertEquals(
            listOf("root-1", "data-1", "result-1", "tasks-1"),
            snapshots.map { it.tasks.single().id }
        )
    }

    @Test
    fun `已识别空容器与未知结构分别分类`() {
        val empty = AntOrchardRewardPolicy.parseTasks(
            """{"resultCode":"100","data":{"taskList":[]}}"""
        )
        val unknown = AntOrchardRewardPolicy.parseTasks(
            """{"resultCode":"100","data":{}}"""
        )

        assertTrue(empty.recognized)
        assertTrue(empty.tasks.isEmpty())
        assertFalse(unknown.recognized)
    }

    private fun task(id: String): String {
        return """
            {
              "taskId":"$id",
              "groupId":"group-$id",
              "taskStatus":"TODO",
              "actionType":"TRIGGER",
              "sceneCode":"ORCHARD",
              "taskPlantType":"NORMAL",
              "awardCount":10,
              "taskDisplayConfig":{"title":"免费任务"}
            }
        """.trimIndent()
    }

    private fun parseSingleTask(
        actionType: String,
        status: String = "TODO",
        title: String = "免费任务"
    ): AntOrchardTaskState {
        val response = """
            {
              "resultCode":"100",
              "taskList":[{
                "taskId":"task-1",
                "groupId":"group-1",
                "taskStatus":"$status",
                "actionType":"$actionType",
                "sceneCode":"ORCHARD",
                "taskPlantType":"NORMAL",
                "awardCount":10,
                "taskDisplayConfig":{"title":"$title"}
              }]
            }
        """.trimIndent()
        return AntOrchardRewardPolicy.parseTasks(response).tasks.single()
    }

    private fun taskStatusResponse(taskId: String, status: String): String {
        return """
            {
              "resultCode":"100",
              "taskList":[{
                "taskId":"$taskId",
                "taskStatus":"$status",
                "actionType":"TRIGGER",
                "sceneCode":"ORCHARD"
              }]
            }
        """.trimIndent()
    }

    private fun leyuanResponse(
        status: String,
        awardField: String,
        awardCount: Int
    ): String {
        return """
            {
              "success":true,
              "taskTriggerPlayInfo":{
                "taskList":[{
                  "sceneCode":"ANTORCHARD_LEYUAN_DAILY_TASK",
                  "taskType":"DAILY_LEYUAN_QIANDAO",
                  "taskStatus":"$status",
                  "title":"每日签到",
                  "$awardField":$awardCount
                }]
              }
            }
        """.trimIndent()
    }

    private fun limitedChallengeResponse(
        taskId: String,
        status: String
    ): String {
        return """
            {
              "success":true,
              "limitedTimeChallenge":{
                "currentRound":1,
                "limitedTimeChallengeTasks":[{
                  "taskId":"$taskId",
                  "taskStatus":"$status"
                }]
              }
            }
        """.trimIndent()
    }
}
