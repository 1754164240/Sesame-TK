package fansirsqi.xposed.sesame.task.antOrchard

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenBeanRpcProtocolTest {

    @Test
    fun `首页和同步使用抓包确认的业务参数`() {
        val index = first(GoldenBeanRpcCall.buildIndexArgs())
        val sync = first(
            GoldenBeanRpcCall.buildSyncArgs(
                listOf("JAR_INFO", "TASK_LIST")
            )
        )

        assertEquals("MASTER", index.getString("bizType"))
        assertEquals("babafarm", index.getString("source"))
        assertEquals("20260723.01", index.getString("version"))
        assertEquals(
            listOf("JAR_INFO", "TASK_LIST"),
            sync.getJSONArray("syncTypeList").let { array ->
                (0 until array.length()).map(array::getString)
            }
        )
    }

    @Test
    fun `完成和领奖参数保留金豆业务上下文`() {
        val finish = first(
            GoldenBeanRpcCall.buildFinishTaskArgs(
                taskType = "JINDOULEYUAN_TRIGGER",
                sceneCode = "GOLDEN_BEAN_MASTER_TASK",
                outBizNo = "out-1"
            )
        )
        val claim = first(
            GoldenBeanRpcCall.buildReceiveTaskAwardArgs(
                taskType = "JINDOULEYUAN_TRIGGER",
                sceneCode = "GOLDEN_BEAN_MASTER_TASK"
            )
        )

        assertEquals("MASTER", finish.getString("bizType"))
        assertEquals("MASTER", finish.getJSONObject("finishBusinessInfo").getString("bizType"))
        assertEquals("out-1", finish.getString("outBizNo"))
        assertEquals("babafarm", finish.getString("source"))
        assertEquals("JINDOULEYUAN_TRIGGER", finish.getString("taskType"))

        assertTrue(claim.getBoolean("ignoreLimit"))
        assertEquals("MASTER", claim.getJSONObject("bizInfo").getString("bizType"))
        assertEquals("GOLDEN_BEAN_MASTER_TASK", claim.getString("sceneCode"))
    }

    private fun first(args: String) = JSONArray(args).getJSONObject(0)
}
