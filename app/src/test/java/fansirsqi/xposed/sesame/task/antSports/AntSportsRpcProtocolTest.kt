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

    @Test
    fun `文体任务完成参数使用结构化JSON并保留特殊字符`() {
        val args = JSONArray(
            AntSportsRpcCall.buildUserTaskCompleteArgs(
                bizType = "biz-\"quoted\"",
                taskId = "task-\"quoted\"",
                completedTime = 123456789L
            )
        )
        val request = args.getJSONObject(0)

        assertEquals(1, args.length())
        assertEquals("biz-\"quoted\"", request.getString("bizType"))
        assertEquals("task-\"quoted\"", request.getString("taskId"))
        assertEquals(123456789L, request.getLong("completedTime"))
    }
}
