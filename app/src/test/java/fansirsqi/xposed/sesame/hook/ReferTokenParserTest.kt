package fansirsqi.xposed.sesame.hook

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferTokenParserTest {

    @Test
    fun `从桥接请求的requestData首项解析referToken`() {
        val params = JSONObject(
            """
            {
              "operationType": "com.alipay.adexchange.ad.facade.xlightPlugin",
              "requestData": [
                {
                  "positionRequest": {
                    "referInfo": {
                      "referToken": "captured-token"
                    }
                  }
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("captured-token", ReferTokenParser.parse(params))
    }

    @Test
    fun `兼容直接业务参数并拒绝空Token`() {
        val direct = JSONObject(
            """{"positionRequest":{"referInfo":{"referToken":"direct-token"}}}"""
        )
        val empty = JSONObject(
            """{"requestData":[{"positionRequest":{"referInfo":{"referToken":""}}}]}"""
        )

        assertEquals("direct-token", ReferTokenParser.parse(direct))
        assertNull(ReferTokenParser.parse(empty))
        assertNull(ReferTokenParser.parse(JSONObject()))
    }
}
