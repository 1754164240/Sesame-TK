package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCenterP2eProtocolTest {

    @Test
    fun `P2E首页与任务查询使用各自来源`() {
        val home = GameCenterP2eProtocol.buildHomeArgs().getJSONObject(0)
        val tasks = GameCenterP2eProtocol.buildTaskListArgs("session-1")
            .getJSONObject(0)

        assertEquals(GameCenterP2eProtocol.HOME_SOURCE, home.getString("source"))
        assertTrue(home.getBoolean("canAddHome"))
        assertEquals(GameCenterP2eProtocol.TASK_SOURCE, tasks.getString("source"))
        assertEquals("session-1", tasks.getString("sessionId"))
        assertTrue(tasks.getJSONObject("panelLaunchableCheckMap").getBoolean("SET_HEAD_TASK"))
    }

    @Test
    fun `P2E平台任务动作绑定动态编号和令牌`() {
        val task = JSONObject()
            .put("taskId", "task-1")
            .put("taskToken", "token-1")
            .put("activityId", "activity-1")
            .put("taskType", "PLATFORM_TRAN_TASK")
        val signup = GameCenterP2eProtocol.buildPlatformTaskArgs(task)
            .getJSONObject(0)
        val receive = GameCenterP2eProtocol.buildReceiveTaskArgs(task)
            .getJSONObject(0)

        assertEquals("task-1", signup.getString("taskId"))
        assertEquals("token-1", signup.getString("taskToken"))
        assertEquals("P2E_PLATFORM_TASK", signup.getString("activityId"))
        assertEquals("activity-1", receive.getString("activityId"))
        assertEquals("PLATFORM_TRAN_TASK", receive.getString("taskType"))
    }

    @Test
    fun `P2E签到和免费抽取参数不含现金提交字段`() {
        val sign = GameCenterP2eProtocol.buildSignInArgs(
            date = "2026-07-28",
            index = 3,
            signSequenceId = "sequence-1"
        ).getJSONObject(0)
        val draw = GameCenterP2eProtocol.buildSimpleArgs().getJSONObject(0)

        assertEquals("2026-07-28", sign.getString("date"))
        assertEquals(3, sign.getInt("index"))
        assertEquals("sequence-1", sign.getString("signSequenceId"))
        assertEquals(GameCenterP2eProtocol.HOME_SOURCE, sign.getString("source"))
        assertFalse(draw.has("prizeConfigId"))
        assertFalse(draw.has("withdrawalTierType"))
    }
}
