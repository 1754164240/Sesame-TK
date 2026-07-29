package fansirsqi.xposed.sesame.task.antStall

import org.junit.Assert.assertEquals
import org.junit.Test

class StallTaskSafetyPolicyTest {
    @Test
    fun `专用安全任务路由到对应处理器`() {
        assertEquals(
            StallTaskDecision.HANDLE_QA,
            StallTaskSafetyPolicy.classify("ANTSTALL_NORMAL_DAILY_QA", "每日答题", "")
        )
        assertEquals(
            StallTaskDecision.HANDLE_INVITE,
            StallTaskSafetyPolicy.classify("ANTSTALL_NORMAL_INVITE_REGISTER", "邀请开通", "")
        )
        assertEquals(
            StallTaskDecision.HANDLE_XLIGHT,
            StallTaskSafetyPolicy.classify("ANTSTALL_XLIGHT_VARIABLE_AWARD", "浏览任务", "")
        )
        assertEquals(
            StallTaskDecision.FINISH_RPC,
            StallTaskSafetyPolicy.classify("ANTSTALL_NORMAL_OPEN_NOTICE", "开启收益提醒", "VISIT_AUTO_FINISH")
        )
    }

    @Test
    fun `游戏广告和金融任务优先于自动完成动作`() {
        assertEquals(
            StallTaskDecision.SKIP_GAME,
            StallTaskSafetyPolicy.classify("ANTSTALL_TASK_nongchangleyuan", "农场乐园小游戏", "VISIT_AUTO_FINISH")
        )
        assertEquals(
            StallTaskDecision.SKIP_AD,
            StallTaskSafetyPolicy.classify("LIGHT_AD_TASK", "看广告领币", "VISIT_AUTO_FINISH")
        )
        assertEquals(
            StallTaskDecision.SKIP_FINANCIAL,
            StallTaskSafetyPolicy.classify("ANTSTALL_TASK_diantao202311", "点淘赚元宝提现", "VISIT_AUTO_FINISH")
        )
    }

    @Test
    fun `未知任务不允许调用 finishTask`() {
        assertEquals(
            StallTaskDecision.SKIP_UNKNOWN,
            StallTaskSafetyPolicy.classify("SERVER_NEW_TASK", "新任务", "CALL_APP")
        )
    }
}
