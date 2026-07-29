package fansirsqi.xposed.sesame.task.antSports

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntSportsRpcProtocolTest {

    @Test
    fun `城市见闻详情参数使用结构化JSON并保留特殊字符`() {
        val args = JSONArray(
            AntSportsRpcCall.buildCityKnowledgeDetailArgs(
                "city-\"quoted\""
            )
        )
        val request = args.getJSONObject(0)

        assertEquals(1, args.length())
        assertEquals("medical_health", request.getString("chInfo"))
        assertEquals("city-\"quoted\"", request.getString("cityId"))
        assertEquals("android", request.getString("clientOS"))
        assertTrue(request.getJSONArray("features").length() > 0)
    }
}
