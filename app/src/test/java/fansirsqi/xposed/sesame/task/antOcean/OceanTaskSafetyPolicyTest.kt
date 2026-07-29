package fansirsqi.xposed.sesame.task.antOcean

import org.junit.Assert.assertEquals
import org.junit.Test

class OceanTaskSafetyPolicyTest {
    @Test
    fun `答题和明确浏览任务进入安全处理分支`() {
        assertEquals(
            OceanTaskDecision.ANSWER,
            OceanTaskSafetyPolicy.classify("DAILY_QUESTION", "海洋知识答题", "")
        )
        assertEquals(
            OceanTaskDecision.FINISH_RPC,
            OceanTaskSafetyPolicy.classify("BROWSE_OCEAN_PAGE", "浏览海洋主页", "VISIT_AUTO_FINISH")
        )
    }

    @Test
    fun `游戏广告和金融任务优先阻断`() {
        assertEquals(
            OceanTaskDecision.SKIP_GAME,
            OceanTaskSafetyPolicy.classify("MINI_GAME_TASK", "玩小游戏", "VISIT_AUTO_FINISH")
        )
        assertEquals(
            OceanTaskDecision.SKIP_AD,
            OceanTaskSafetyPolicy.classify("LIGHT_AD_TASK", "看广告得拼图", "VISIT_AUTO_FINISH")
        )
        assertEquals(
            OceanTaskDecision.SKIP_FINANCIAL,
            OceanTaskSafetyPolicy.classify("ORDER_TASK", "下单购买得拼图", "VISIT_AUTO_FINISH")
        )
    }

    @Test
    fun `真实业务动作和未知任务不使用 finishTask`() {
        assertEquals(
            OceanTaskDecision.SKIP_BUSINESS_ACTION,
            OceanTaskSafetyPolicy.classify("CLEAN_RUBBISH_2_EVERY_DAY", "清理自己海域", "")
        )
        assertEquals(
            OceanTaskDecision.SKIP_UNKNOWN,
            OceanTaskSafetyPolicy.classify("NEW_SERVER_TASK", "神秘任务", "")
        )
    }
}
