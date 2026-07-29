package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YebExpGoldRpcCallTest {

    @Test
    fun `主页查询参数固定为体验金签到和查询配置`() {
        val args = JSONArray(YebExpGoldRpcCall.buildMainQueryArgs())
            .getJSONObject(0)

        assertTrue(args.has("signIn"))
        assertTrue(args.has("task"))
        assertEquals(
            "YEB_TRIAL_ASSET_TASK_BLOCK_REC",
            args.getJSONObject("task").getString("strategyCode")
        )
        assertFalse(args.has("exchangeAmount"))
        assertFalse(args.has("bizOrderNo"))
    }

    @Test
    fun `签到只提交固定活动标识`() {
        val args = JSONArray(YebExpGoldRpcCall.buildSignInArgs())
            .getJSONObject(0)

        assertEquals("PLAY102253251", args.getString("signInPlayId"))
        assertEquals(1, args.length())
    }

    @Test
    fun `券查询和处理参数不包含购买充值或兑换金额`() {
        val query = JSONArray(YebExpGoldRpcCall.buildVoucherQueryArgs())
            .getJSONObject(0)
        val convert = JSONArray(YebExpGoldRpcCall.buildVoucherConvertArgs())
            .getJSONObject(0)

        assertEquals("PROMO_ACTIVITY", query.getString("component"))
        assertEquals("all", convert.getString("convertType"))
        assertFalse(convert.has("exchangeAmount"))
        assertFalse(convert.has("payAmount"))
        assertFalse(convert.has("price"))
    }
}
