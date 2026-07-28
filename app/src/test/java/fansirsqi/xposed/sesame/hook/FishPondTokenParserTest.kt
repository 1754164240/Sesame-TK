package fansirsqi.xposed.sesame.hook

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FishPondTokenParserTest {

    @Test
    fun `兼容数组字符串和直接请求对象`() {
        val arrayRequest = JSONObject()
            .put("requestData", JSONArray().put(JSONObject().put("riskToken", "token-a")))
        val stringRequest = JSONObject()
            .put("requestData", """[{"riskToken":"token-b"}]""")
        val objectRequest = JSONObject()
            .put("requestData", JSONObject().put("riskToken", "token-c"))
        val directRequest = JSONObject().put("riskToken", "token-d")

        assertEquals("token-a", FishPondTokenParser.parse(arrayRequest))
        assertEquals("token-b", FishPondTokenParser.parse(stringRequest))
        assertEquals("token-c", FishPondTokenParser.parse(objectRequest))
        assertEquals("token-d", FishPondTokenParser.parse(directRequest))
    }

    @Test
    fun `空白或非法请求不返回风控令牌`() {
        assertNull(FishPondTokenParser.parse(JSONObject()))
        assertNull(FishPondTokenParser.parse(JSONObject().put("riskToken", " ")))
        assertNull(FishPondTokenParser.parse(JSONObject().put("requestData", "not-json")))
        assertNull(
            FishPondTokenParser.parse(
                JSONObject().put("requestData", JSONArray())
            )
        )
    }
}
