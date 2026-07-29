package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldTicketPolicyTest {

    @Test
    fun `签到状态必须包含明确布尔字段才可识别`() {
        val unsigned = GoldTicketPolicy.parseSign(
            """
                {
                  "success":true,
                  "result":{"sign":{"todayHasSigned":false}}
                }
            """.trimIndent()
        )
        val signed = GoldTicketPolicy.parseSign(
            """
                {
                  "success":true,
                  "result":{"sign":{"todayHasSigned":true}}
                }
            """.trimIndent()
        )
        val missing = GoldTicketPolicy.parseSign(
            """{"success":true,"result":{"sign":{}}}"""
        )

        assertTrue(unsigned.recognized)
        assertFalse(unsigned.signed)
        assertTrue(signed.recognized)
        assertTrue(signed.signed)
        assertFalse(missing.recognized)
    }

    @Test
    fun `余额查询缺少数量或结构畸形时不可识别`() {
        val recognized = GoldTicketPolicy.parseBalance(
            """
                {
                  "success":true,
                  "result":{"assetInfo":{"availableAmount":320}}
                }
            """.trimIndent()
        )
        val missing = GoldTicketPolicy.parseBalance(
            """{"success":true,"result":{"assetInfo":{}}}"""
        )
        val malformed = GoldTicketPolicy.parseBalance(
            """
                {
                  "success":true,
                  "result":{"assetInfo":{"availableAmount":"unknown"}}
                }
            """.trimIndent()
        )

        assertEquals(
            GoldTicketBalanceSnapshot(true, 320),
            recognized
        )
        assertFalse(missing.recognized)
        assertFalse(malformed.recognized)
    }
}
