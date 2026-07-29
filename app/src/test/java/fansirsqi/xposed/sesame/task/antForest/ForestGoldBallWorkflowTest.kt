package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestGoldBallWorkflowTest {

    @Test
    fun `明确收取正数浇水金球时记录好友`() {
        val recorded = mutableListOf<String>()
        val workflow = ForestGoldBallWorkflow(recorded::add)

        val result = workflow.handle(
            response = """{"success":true,"bubbles":[{"collectedEnergy":3},{"collectedEnergy":2}]}""",
            friendId = "friend-1"
        )

        assertEquals(GoldBallOutcome.CONFIRMED, result.outcome)
        assertEquals(5, result.collected)
        assertTrue(result.friendRecorded)
        assertEquals(listOf("friend-1"), recorded)
    }

    @Test
    fun `零能量和未知响应不记录好友`() {
        val recorded = mutableListOf<String>()
        val workflow = ForestGoldBallWorkflow(recorded::add)

        val zeroResult = workflow.handle(
            response = """{"success":true,"bubbles":[{"collectedEnergy":0}]}""",
            friendId = "friend-1"
        )
        val unknownResult = workflow.handle(
            response = """{"success":true}""",
            friendId = "friend-1"
        )

        assertEquals(GoldBallOutcome.NO_ACTION, zeroResult.outcome)
        assertEquals(GoldBallOutcome.RETRY, unknownResult.outcome)
        assertFalse(zeroResult.friendRecorded)
        assertFalse(unknownResult.friendRecorded)
        assertTrue(recorded.isEmpty())
    }
}
