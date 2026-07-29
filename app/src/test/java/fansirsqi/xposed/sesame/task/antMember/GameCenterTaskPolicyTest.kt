package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCenterTaskPolicyTest {

    @Test
    fun `普通真实游戏通关任务必须跳过`() {
        val task = JSONObject()
            .put("actionType", "NORMAL")
            .put("taskStatus", "NOT_DONE")
            .put("buttonText", "去完成")
            .put("gameId", "game")
            .put("appId", "app")
            .put("jumpLink", "alipays://platformapi/startapp")
            .put("title", "玩游戏通过一关")

        assertEquals(
            GameCenterTaskDecision.SKIP_REAL_GAME,
            GameCenterTaskPolicy.classifyPlatformTask(task)
        )
    }

    @Test
    fun `P2E真实游戏任务必须跳过`() {
        val task = JSONObject()
            .put("taskType", "GAME_TRAN_TASK")
            .put("actionType", "NORMAL")
            .put("taskStatus", "NOT_DONE")
            .put("title", "完成游戏订单")

        assertEquals(
            GameCenterTaskDecision.SKIP_REAL_GAME,
            GameCenterTaskPolicy.classifyP2eTask(task)
        )
    }

    @Test
    fun `P2E广告任务必须跳过`() {
        val task = JSONObject()
            .put("taskType", "PLATFORM_TRAN_TASK")
            .put("actionType", "LIGHT_AD_TASK")
            .put("taskStatus", "NOT_DONE")
            .put("title", "观看广告")

        assertEquals(
            GameCenterTaskDecision.SKIP_AD,
            GameCenterTaskPolicy.classifyP2eTask(task)
        )
    }

    @Test
    fun `P2E已完成真实游戏也不得领奖`() {
        val task = JSONObject()
            .put("taskType", "GAME_TRAN_TASK")
            .put("actionType", "VIEW_TASK")
            .put("taskStatus", "COMPLETED")
            .put("buttonText", "领取")
            .put("title", "完成游戏订单")

        assertEquals(
            GameCenterTaskDecision.SKIP_REAL_GAME,
            GameCenterTaskPolicy.classifyP2eTask(task)
        )
    }

    @Test
    fun `P2E已完成广告任务也不得领奖`() {
        val task = JSONObject()
            .put("taskType", "PLATFORM_TRAN_TASK")
            .put("actionType", "LIGHT_AD_TASK")
            .put("taskStatus", "COMPLETED")
            .put("buttonText", "领取")
            .put("title", "观看广告")

        assertEquals(
            GameCenterTaskDecision.SKIP_AD,
            GameCenterTaskPolicy.classifyP2eTask(task)
        )
    }

    @Test
    fun `P2E已完成金融任务也不得领奖`() {
        val task = JSONObject()
            .put("taskType", "PLATFORM_TRAN_TASK")
            .put("actionType", "VIEW_TASK")
            .put("taskStatus", "COMPLETED")
            .put("buttonText", "领取")
            .put("title", "提现奖励")

        assertEquals(
            GameCenterTaskDecision.SKIP_FINANCIAL,
            GameCenterTaskPolicy.classifyP2eTask(task)
        )
    }

    @Test
    fun `P2E平台浏览任务允许发送`() {
        val task = JSONObject()
            .put("taskType", "PLATFORM_TRAN_TASK")
            .put("actionType", "VIEW_TASK")
            .put("taskStatus", "NOT_DONE")
            .put("title", "浏览游戏中心")

        assertEquals(
            GameCenterTaskDecision.SEND,
            GameCenterTaskPolicy.classifyP2eTask(task)
        )
    }

    @Test
    fun `已完成任务只允许领奖`() {
        val task = JSONObject()
            .put("taskType", "PLATFORM_TRAN_TASK")
            .put("actionType", "VIEW_TASK")
            .put("taskStatus", "COMPLETED")
            .put("buttonText", "领取")
            .put("title", "领取金币")

        assertEquals(
            GameCenterTaskDecision.CLAIM_ONLY,
            GameCenterTaskPolicy.classifyP2eTask(task)
        )
    }

    @Test
    fun `未知平台任务不得发送`() {
        val task = JSONObject()
            .put("actionType", "UNKNOWN")
            .put("taskStatus", "NOT_DONE")
            .put("title", "未知任务")

        assertEquals(
            GameCenterTaskDecision.SKIP_UNSUPPORTED,
            GameCenterTaskPolicy.classifyPlatformTask(task)
        )
    }

    @Test
    fun `平台任务快照必须识别真实容器并按任务编号查找`() {
        val snapshot = GameCenterTaskPolicy.parsePlatformTasks(
            """
            {
              "success": true,
              "data": {
                "platformTaskModule": {
                  "platformTaskList": [{
                    "taskId": "task-1",
                    "taskStatus": "NOT_DONE",
                    "title": "浏览任务"
                  }]
                }
              }
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals(
            "NOT_DONE",
            GameCenterTaskPolicy.findTask(snapshot, "task-1")
                ?.optString("taskStatus")
        )
        assertNull(GameCenterTaskPolicy.findTask(snapshot, "missing"))
    }

    @Test
    fun `未知任务容器不得推定为空列表`() {
        val snapshot = GameCenterTaskPolicy.parsePlatformTasks(
            """{"success":true,"data":{}}"""
        )

        assertFalse(snapshot.recognized)
        assertTrue(snapshot.tasks.isEmpty())
    }

    @Test
    fun `P2E任务快照兼容曝光与平台任务容器`() {
        val snapshot = GameCenterTaskPolicy.parseP2eTasks(
            """
            {
              "success": true,
              "data": {
                "exposedTaskModuleVO": {
                  "exposedTaskList": [{"taskId":"task-1","taskStatus":"NOT_DONE"}]
                },
                "platformGameTaskModule": {
                  "platformTaskList": [{"taskId":"task-2","taskStatus":"COMPLETED"}]
                }
              }
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals(setOf("task-1", "task-2"), snapshot.tasks.map { it.optString("taskId") }.toSet())
    }

    @Test
    fun `P2E未知任务容器不得推定为空列表`() {
        val snapshot = GameCenterTaskPolicy.parseP2eTasks(
            """{"success":true,"data":{}}"""
        )

        assertFalse(snapshot.recognized)
        assertTrue(snapshot.tasks.isEmpty())
    }

    @Test
    fun `报名只有回查到报名完成或终态才确认`() {
        assertFalse(
            GameCenterTaskPolicy.isSignupConfirmed(
                previousStatus = "NOT_DONE",
                refreshedStatus = "NOT_DONE"
            )
        )
        assertTrue(
            GameCenterTaskPolicy.isSignupConfirmed(
                previousStatus = "NOT_DONE",
                refreshedStatus = "SIGNUP_COMPLETE"
            )
        )
        assertTrue(
            GameCenterTaskPolicy.isSignupConfirmed(
                previousStatus = "NOT_DONE",
                refreshedStatus = "COMPLETED"
            )
        )
    }

    @Test
    fun `发送只有回查到终态才确认`() {
        assertFalse(GameCenterTaskPolicy.isSendConfirmed("SIGNUP_COMPLETE"))
        assertFalse(GameCenterTaskPolicy.isSendConfirmed("NOT_DONE"))
        assertTrue(GameCenterTaskPolicy.isSendConfirmed("COMPLETED"))
        assertTrue(GameCenterTaskPolicy.isSendConfirmed("RECEIVED"))
    }
}
