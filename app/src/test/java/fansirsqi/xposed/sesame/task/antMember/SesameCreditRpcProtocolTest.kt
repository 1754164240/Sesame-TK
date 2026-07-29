package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SesameCreditRpcProtocolTest {
    @Test
    fun `次日奖励查询携带版本且领奖绑定awardId`() {
        val queryArgs = JSONArray(
            AntMemberRpcCall.buildAlchemyQueryEntryListArgs()
        ).getJSONObject(0)
        val claimArgs = JSONArray(
            AntMemberRpcCall.buildAlchemyClaimAwardArgs("award-1")
        ).getJSONObject(0)

        assertFalse(queryArgs.getString("version").isBlank())
        assertEquals("award-1", claimArgs.getString("awardId"))
    }

    @Test
    fun `满级红包协议只暴露资格查询参数`() {
        val args = JSONArray(
            AntMemberRpcCall.buildAlchemyWithdrawPreConsultArgs()
        )

        assertEquals(1, args.length())
        assertEquals(true, args.isNull(0))
    }
}
