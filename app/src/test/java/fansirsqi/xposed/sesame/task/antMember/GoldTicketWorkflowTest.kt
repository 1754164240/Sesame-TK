package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertEquals
import org.junit.Test

class GoldTicketWorkflowTest {

    @Test
    fun `今日已签到时不重复触发`() {
        var triggerCalls = 0
        val workflow = GoldTicketWorkflow(
            queryHome = { signResponse(true) },
            triggerSign = {
                triggerCalls++
                """{"success":true}"""
            },
            queryBalance = { "" }
        )

        val outcome = workflow.signIn()

        assertEquals(0, triggerCalls)
        assertEquals(GoldTicketOutcome.NO_ACTION, outcome)
    }

    @Test
    fun `签到ACK后状态未推进时保留重试`() {
        var queryCalls = 0
        var triggerCalls = 0
        val workflow = GoldTicketWorkflow(
            queryHome = {
                queryCalls++
                signResponse(false)
            },
            triggerSign = {
                triggerCalls++
                """{"success":true}"""
            },
            queryBalance = { "" }
        )

        val outcome = workflow.signIn()

        assertEquals(2, queryCalls)
        assertEquals(1, triggerCalls)
        assertEquals(GoldTicketOutcome.RETRY, outcome)
    }

    @Test
    fun `签到后服务端明确已签到才确认`() {
        var queryCalls = 0
        val workflow = GoldTicketWorkflow(
            queryHome = {
                queryCalls++
                signResponse(queryCalls > 1)
            },
            triggerSign = { """{"success":true}""" },
            queryBalance = { "" }
        )

        val outcome = workflow.signIn()

        assertEquals(GoldTicketOutcome.CONFIRMED, outcome)
    }

    @Test
    fun `旧提取开关只执行余额查询`() {
        var balanceQueries = 0
        val workflow = GoldTicketWorkflow(
            queryHome = { "" },
            triggerSign = { error("余额查询不应触发签到") },
            queryBalance = {
                balanceQueries++
                """
                    {
                      "success":true,
                      "result":{"assetInfo":{"availableAmount":500}}
                    }
                """.trimIndent()
            }
        )

        val balance = workflow.readBalance()

        assertEquals(1, balanceQueries)
        assertEquals(GoldTicketBalanceSnapshot(true, 500), balance)
    }

    private fun signResponse(signed: Boolean): String {
        return """
            {
              "success":true,
              "result":{"sign":{"todayHasSigned":$signed}}
            }
        """.trimIndent()
    }
}
