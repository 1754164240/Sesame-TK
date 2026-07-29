package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmGameReadOnlyWorkflowTest {

    @Test
    fun `游戏状态缺少剩余次数或奖励状态时不可识别`() {
        assertFalse(
            FarmGameReadOnlyPolicy.parse(
                """{"success":true,"gameAward":{"level3Get":false}}"""
            ).recognized
        )
        assertFalse(
            FarmGameReadOnlyPolicy.parse(
                """{"success":true,"remainingGameCount":2}"""
            ).recognized
        )
    }

    @Test
    fun `只读工作流逐项查询且不提供改分动作`() {
        val queried = mutableListOf<String>()
        val workflow = FarmGameReadOnlyWorkflow(
            queryGame = { gameType ->
                queried += gameType
                """
                    {
                      "success":true,
                      "remainingGameCount":2,
                      "gameAward":{"level3Get":false}
                    }
                """.trimIndent()
            }
        )

        val snapshots = workflow.inspect(
            listOf("flyGame", "hitGame")
        )

        assertEquals(listOf("flyGame", "hitGame"), queried)
        assertEquals(2, snapshots.size)
        assertTrue(snapshots.all { it.recognized })
        assertEquals(2, snapshots.first().remainingGameCount)
        assertFalse(snapshots.first().levelThreeRewardReceived)
    }

    @Test
    fun `禁用成绩提交后加速喂食不再为游戏奖励预留空间`() {
        val decision =
            FarmGameReadOnlyPolicy.decideFeedRewardAfterAcceleratedFeed(
                scoreSubmissionAllowed = false,
                gameFinished = false,
                foodStock = 900,
                foodStockLimit = 1000,
                expectedGameReward = 180
            )

        assertEquals(
            FarmGameFeedDecision.RECEIVE_REWARD,
            decision
        )
    }
}
