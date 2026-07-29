package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntFarmRewardPolicyTest {

    @Test
    fun `抽抽乐广告任务不得执行伪完成`() {
        assertFalse(
            AntFarmRewardPolicy.shouldExecuteChouTask("SHANGYEHUA_DAILY_DRAW_TIMES")
        )
        assertFalse(
            AntFarmRewardPolicy.shouldExecuteChouTask("IP_SHANGYEHUA_TASK")
        )
        assertTrue(
            AntFarmRewardPolicy.shouldExecuteChouTask("NORMAL_DRAW_TASK")
        )
    }

    @Test
    fun `多项饲料奖励按剩余容量选择且不超领`() {
        val candidates = listOf(
            FarmRewardCandidate("task-1", 90),
            FarmRewardCandidate("task-2", 180),
            FarmRewardCandidate("task-3", 30)
        )

        assertEquals(
            listOf("task-1", "task-3"),
            AntFarmRewardPolicy.selectWithinCapacity(
                candidates,
                remainingCapacity = 120
            ).map { it.id }
        )
    }

    @Test
    fun `庄园任务只有回查到已领取才确认`() {
        assertTrue(
            AntFarmRewardPolicy.isTaskReceived(
                farmTaskResponse("task-1", "RECEIVED"),
                "task-1"
            )
        )
        assertFalse(
            AntFarmRewardPolicy.isTaskReceived(
                farmTaskResponse("task-1", "FINISHED"),
                "task-1"
            )
        )
        assertFalse(
            AntFarmRewardPolicy.isTaskReceived(
                """{"success":true,"farmTaskList":[]}""",
                "task-1"
            )
        )
    }

    @Test
    fun `家庭签到以重新查询后的待签到提示消失为准`() {
        assertFalse(
            AntFarmRewardPolicy.isFamilySignConfirmed(
                """{"success":true,"familySignTips":true}"""
            )
        )
        assertTrue(
            AntFarmRewardPolicy.isFamilySignConfirmed(
                """{"resultCode":"SUCCESS","data":{"familySignTips":false}}"""
            )
        )
        assertFalse(
            AntFarmRewardPolicy.isFamilySignConfirmed(
                """{"success":true,"data":{}}"""
            )
        )
    }

    @Test
    fun `家庭奖励记录消失或明确已领取才确认`() {
        assertFalse(
            AntFarmRewardPolicy.isFamilyAwardConfirmed(
                familyAwardResponse("right-1", received = false),
                "right-1"
            )
        )
        assertTrue(
            AntFarmRewardPolicy.isFamilyAwardConfirmed(
                familyAwardResponse("right-1", received = true),
                "right-1"
            )
        )
        assertTrue(
            AntFarmRewardPolicy.isFamilyAwardConfirmed(
                """{"success":true,"familyAwardRecordList":[]}""",
                "right-1"
            )
        )
    }

    @Test
    fun `乐园限时奖励领取后不再处于可领取态才确认`() {
        val before = paradiseResponse("FINISHED")
        val afterPending = paradiseResponse("FINISHED")
        val afterReceived = paradiseResponse("RECEIVED")

        assertFalse(
            AntFarmRewardPolicy.isParadiseRewardConfirmed(
                afterPending,
                AntFarmParadiseLimitedActivity.SIGN_TASK_TYPE
            )
        )
        assertTrue(
            AntFarmRewardPolicy.isParadiseRewardConfirmed(
                afterReceived,
                AntFarmParadiseLimitedActivity.SIGN_TASK_TYPE
            )
        )
        assertEquals(
            1,
            AntFarmParadiseLimitedActivity.claimableTasks(
                org.json.JSONObject(before),
                nowMillis = 0L
            ).size
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

    private fun familyAwardResponse(
        rightId: String,
        received: Boolean
    ): String {
        return """
            {
              "success": true,
              "familyAwardRecordList": [{
                "rightId": "$rightId",
                "received": $received
              }]
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
