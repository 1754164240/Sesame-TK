package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Test

class ChouChouLeTaskPolicyTest {

    @Test
    fun `两类杂货铺浏览任务使用浏览完成接口`() {
        assertEquals(
            ChouChouLeTaskRoute.BROWSE,
            ChouChouLeTaskPolicy.route(
                "SHANGYEHUA_DAILY_DRAW_TIMES"
            )
        )
        assertEquals(
            ChouChouLeTaskRoute.BROWSE,
            ChouChouLeTaskPolicy.route("IP_SHANGYEHUA_TASK")
        )
    }

    @Test
    fun `其他任务使用庄园任务完成接口`() {
        assertEquals(
            ChouChouLeTaskRoute.FARM,
            ChouChouLeTaskPolicy.route("NORMAL_TASK")
        )
    }

    @Test
    fun `浏览任务查询时长后等待并提交完成`() {
        val calls = mutableListOf<String>()
        val workflow = ChouChouLeBrowseWorkflow(
            queryTask = {
                calls += "query"
                """
                    {
                      "success":true,
                      "resultData":{"duration":15.0}
                    }
                """.trimIndent()
            },
            wait = {
                calls += "wait:$it"
            },
            finishTask = { taskId, sceneCode ->
                calls += "finish:$taskId:$sceneCode"
                """{"success":true}"""
            }
        )

        val response = workflow.execute(
            "ipDraw",
            "IP_SHANGYEHUA_TASK"
        )

        assertEquals("""{"success":true}""", response)
        assertEquals(
            listOf(
                "query",
                "wait:15000",
                "finish:IP_SHANGYEHUA_TASK:" +
                    "ANTFARM_IP_DRAW_TASK"
            ),
            calls
        )
    }
}
