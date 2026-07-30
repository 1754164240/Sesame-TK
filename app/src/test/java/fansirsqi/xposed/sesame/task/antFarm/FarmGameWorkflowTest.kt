package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Test

class FarmGameWorkflowTest {

    @Test
    fun `成绩提交后剩余次数下降才确认`() {
        val responses = ArrayDeque(
            listOf(snapshot(2, false), snapshot(1, false))
        )
        val result = workflow(
            queryGame = { responses.removeFirst() }
        ).play("starGame")

        assertEquals(FarmGameOutcome.CONFIRMED, result.outcome)
        assertEquals(2, result.before?.remainingGameCount)
        assertEquals(1, result.after?.remainingGameCount)
    }

    @Test
    fun `成绩提交后状态不变必须重试`() {
        val responses = ArrayDeque(
            listOf(snapshot(2, false), snapshot(2, false))
        )
        val result = workflow(
            queryGame = { responses.removeFirst() }
        ).play("starGame")

        assertEquals(FarmGameOutcome.RETRY, result.outcome)
    }

    @Test
    fun `次数用完或三级奖励已领取时不提交`() {
        var submitCalls = 0
        val result = workflow(
            queryGame = { snapshot(0, true) },
            submitScore = {
                submitCalls++
                success()
            }
        ).play("starGame")

        assertEquals(FarmGameOutcome.NO_ACTION, result.outcome)
        assertEquals(0, submitCalls)
    }

    @Test
    fun `未知初始化结构不得提交成绩`() {
        var submitCalls = 0
        val result = workflow(
            queryGame = { """{"success":true}""" },
            submitScore = {
                submitCalls++
                success()
            }
        ).play("starGame")

        assertEquals(FarmGameOutcome.RETRY, result.outcome)
        assertEquals(0, submitCalls)
    }

    private fun workflow(
        queryGame: (String) -> String,
        submitScore: (String) -> String = { success() }
    ): FarmGameWorkflow {
        return FarmGameWorkflow(
            queryGame = queryGame,
            submitScore = submitScore,
            pauseAfterAction = {},
            isActionSuccess = { response ->
                response.contains("\"success\":true")
            }
        )
    }

    private fun snapshot(
        remainingGameCount: Int,
        levelThreeRewardReceived: Boolean
    ): String {
        return """
            {
              "success": true,
              "remainingGameCount": $remainingGameCount,
              "gameAward": {"level3Get": $levelThreeRewardReceived}
            }
        """.trimIndent()
    }

    private fun success(): String = """{"success":true}"""
}
