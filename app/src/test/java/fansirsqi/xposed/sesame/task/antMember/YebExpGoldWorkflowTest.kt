package fansirsqi.xposed.sesame.task.antMember

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YebExpGoldWorkflowTest {

    @Test
    fun `签到动作后回查为已签到才确认`() = runBlocking {
        val mainResponses = ArrayDeque(
            listOf(mainResponse("TO_SIGNED"), mainResponse("SIGNED"))
        )
        var signCalls = 0
        val workflow = workflow(
            queryMain = { mainResponses.removeFirst() },
            signIn = {
                signCalls++
                successResponse()
            },
            queryVouchers = { voucherResponse(0) }
        )

        val result = workflow.run()

        assertEquals(YebExpGoldStepResult.CONFIRMED, result.signIn)
        assertEquals(YebExpGoldStepResult.NOT_NEEDED, result.voucher)
        assertEquals(1, signCalls)
        assertFalse(result.retryable)
    }

    @Test
    fun `签到ACK成功但回查仍待签到时保留重试`() = runBlocking {
        val mainResponses = ArrayDeque(
            listOf(mainResponse("TO_SIGNED"), mainResponse("TO_SIGNED"))
        )
        var voucherQueries = 0
        val workflow = workflow(
            queryMain = { mainResponses.removeFirst() },
            signIn = { successResponse() },
            queryVouchers = {
                voucherQueries++
                voucherResponse(0)
            }
        )

        val result = workflow.run()

        assertEquals(YebExpGoldStepResult.RETRY, result.signIn)
        assertEquals(YebExpGoldStepResult.DEFERRED, result.voucher)
        assertEquals(0, voucherQueries)
        assertTrue(result.retryable)
    }

    @Test
    fun `券处理后待使用数量减少才确认`() = runBlocking {
        val voucherResponses = ArrayDeque(
            listOf(voucherResponse(2), voucherResponse(1))
        )
        var convertCalls = 0
        val workflow = workflow(
            queryMain = { mainResponse("SIGNED") },
            queryVouchers = { voucherResponses.removeFirst() },
            convertVouchers = {
                convertCalls++
                successResponse()
            }
        )

        val result = workflow.run()

        assertEquals(YebExpGoldStepResult.NOT_NEEDED, result.signIn)
        assertEquals(YebExpGoldStepResult.CONFIRMED, result.voucher)
        assertEquals(1, convertCalls)
        assertFalse(result.retryable)
    }

    @Test
    fun `券处理ACK成功但数量未减少时保留重试`() = runBlocking {
        val voucherResponses = ArrayDeque(
            listOf(voucherResponse(2), voucherResponse(2))
        )
        val workflow = workflow(
            queryMain = { mainResponse("SIGNED") },
            queryVouchers = { voucherResponses.removeFirst() },
            convertVouchers = { successResponse() }
        )

        val result = workflow.run()

        assertEquals(YebExpGoldStepResult.RETRY, result.voucher)
        assertTrue(result.retryable)
    }

    @Test
    fun `没有待签到和待使用券时不执行动作`() = runBlocking {
        var signCalls = 0
        var convertCalls = 0
        val workflow = workflow(
            queryMain = { mainResponse("SIGNED") },
            signIn = {
                signCalls++
                successResponse()
            },
            queryVouchers = { voucherResponse(0) },
            convertVouchers = {
                convertCalls++
                successResponse()
            }
        )

        val result = workflow.run()

        assertEquals(YebExpGoldStepResult.NOT_NEEDED, result.signIn)
        assertEquals(YebExpGoldStepResult.NOT_NEEDED, result.voucher)
        assertEquals(0, signCalls)
        assertEquals(0, convertCalls)
    }

    private fun workflow(
        queryMain: suspend () -> String,
        signIn: suspend () -> String = { successResponse() },
        queryVouchers: suspend () -> String,
        convertVouchers: suspend () -> String = { successResponse() }
    ): YebExpGoldWorkflow = YebExpGoldWorkflow(
        queryMain = queryMain,
        signIn = signIn,
        queryVouchers = queryVouchers,
        convertVouchers = convertVouchers
    )

    private fun mainResponse(status: String): String = """
        {
          "success": true,
          "resultData": {
            "signInData": {
              "list": [
                {
                  "displayDate": "今天",
                  "signInfo": {
                    "signDateDesc": "TODAY",
                    "signStatus": "$status"
                  }
                }
              ]
            }
          }
        }
    """.trimIndent()

    private fun voucherResponse(pendingCount: Int): String {
        val items = (0 until pendingCount).joinToString(",") {
            """{"equityStatus":"CAN_USE"}"""
        }
        return """{"success":true,"result":{"equityList":[$items]}}"""
    }

    private fun successResponse(): String = """{"success":true}"""
}
