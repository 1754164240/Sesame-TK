package fansirsqi.xposed.sesame.task.antOrchard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenBeanPolicyTest {

    @Test
    fun `支付和余额宝待办只等待但完成后允许领奖`() {
        listOf(
            "GOLDEN_BEAN_TASK_XIANSHANGZHIFU",
            "GOLDEN_BEAN_TASK_XIANXIAZHIFU",
            "GOLDEN_BEAN_TASK_YUEBAO"
        ).forEach { taskType ->
            assertEquals(
                GoldenBeanTaskDecision.WAIT,
                GoldenBeanPolicy.decide(task(type = taskType, status = "TODO"))
            )
            assertEquals(
                GoldenBeanTaskDecision.CLAIM,
                GoldenBeanPolicy.decide(task(type = taskType, status = "FINISHED"))
            )
        }
    }

    @Test
    fun `订阅游戏和金豆乐园待办允许完成`() {
        listOf(
            task(type = "TEST_PUSH_SUBSCRIBE", actionType = "PUSH_SUBSCRIBE"),
            task(type = "GOLDENBEAN_GAME_ZH_CGNNC", actionType = "VISIT"),
            task(type = "GOLDEN_BEAN_TASK_WAKUANG", actionType = "TRIGGER"),
            task(type = "JINDOULEYUAN_TRIGGER", actionType = "GAMECENTER_TRIGGER")
        ).forEach { snapshot ->
            assertEquals(
                GoldenBeanTaskDecision.COMPLETE,
                GoldenBeanPolicy.decide(snapshot)
            )
        }
    }

    @Test
    fun `财运签使用专用动作且肥料兑换和未知任务跳过`() {
        assertEquals(
            GoldenBeanTaskDecision.FORTUNE_DRAW,
            GoldenBeanPolicy.decide(
                task(type = "FORTUNE_DRAW", actionType = "FORTUNE_DRAW")
            )
        )
        assertEquals(
            GoldenBeanTaskDecision.SKIP,
            GoldenBeanPolicy.decide(
                task(type = "MANURE_EXCHANGE", actionType = "MANURE_EXCHANGE")
            )
        )
        assertEquals(
            GoldenBeanTaskDecision.SKIP,
            GoldenBeanPolicy.decide(
                task(type = "NEW_UNKNOWN_TASK", actionType = "UNKNOWN")
            )
        )
        assertEquals(
            GoldenBeanTaskDecision.SKIP,
            GoldenBeanPolicy.decide(
                task(
                    type = "NEW_UNKNOWN_TASK",
                    actionType = "VISIT",
                    title = "参与游戏挑战"
                )
            )
        )
    }

    @Test
    fun `服务端终态优先于任务类型分类`() {
        assertEquals(
            GoldenBeanTaskDecision.CLAIM,
            GoldenBeanPolicy.decide(task(type = "UNKNOWN", status = "TO_RECEIVE"))
        )
        assertEquals(
            GoldenBeanTaskDecision.WAIT,
            GoldenBeanPolicy.decide(task(type = "UNKNOWN", status = "RECEIVED"))
        )
    }

    @Test
    fun `只接受明确成功响应`() {
        assertTrue(GoldenBeanPolicy.isRpcSuccess(JSONObject("""{"success":true}""")))
        assertTrue(GoldenBeanPolicy.isRpcSuccess(JSONObject("""{"resultCode":"100"}""")))
        assertFalse(GoldenBeanPolicy.isRpcSuccess(JSONObject("""{"success":false}""")))
        assertFalse(
            GoldenBeanPolicy.isRpcSuccess(
                JSONObject("""{"success":false,"resultCode":"100"}""")
            )
        )
        assertFalse(GoldenBeanPolicy.isRpcSuccess(JSONObject()))
    }

    private fun task(
        type: String,
        status: String = "TODO",
        actionType: String = "VISIT",
        title: String = "测试任务"
    ) = GoldenBeanTaskSnapshot(
        type = type,
        sceneCode = "GOLDEN_BEAN_MASTER_TASK",
        status = status,
        actionType = actionType,
        title = title
    )
}
