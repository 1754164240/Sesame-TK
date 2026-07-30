package fansirsqi.xposed.sesame.task.antFishPond

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AntFishPondRpcProtocolTest {

    @Test
    fun `浏览完成参数携带抓包中的业务标识`() {
        val notice = request(AntFishPondRpcCall.buildAdNoticeArgs("ad-1"))
        val finish = request(
            AntFishPondRpcCall.buildFinishTaskArgs(
                taskType = "GYG_XLIGHT_JX_BUSINEES_3",
                sceneCode = "ANTFISHPOND_TASK",
                adBizNo = "ad-1",
                outBizNo = "out-1"
            )
        )

        assertEquals("ad-1", notice.getString("adBizNo"))
        assertEquals("NORMAL", notice.getString("requestType"))
        assertEquals("GameCenter", notice.getString("sceneCode"))
        assertEquals("farmpool", notice.getString("source"))
        assertEquals(
            "ad-1",
            finish.getJSONObject("finishBusinessInfo")
                .getString("pwPreBizId")
        )
        assertEquals("out-1", finish.getString("outBizNo"))
        assertEquals("RPC", finish.getString("requestType"))
        assertEquals("ANTFISHPOND_TASK", finish.getString("sceneCode"))
        assertEquals("GYG_XLIGHT_JX_BUSINEES_3", finish.getString("taskType"))
    }

    @Test
    fun `普通任务完成参数不生成空业务信息`() {
        val finish = request(
            AntFishPondRpcCall.buildFinishTaskArgs(
                taskType = "FISHPOND_NORMAL_ADD_HOME",
                sceneCode = "ANTFISHPOND_TASK",
                adBizNo = null,
                outBizNo = "out-2"
            )
        )

        assertFalse(finish.has("finishBusinessInfo"))
        assertEquals("ADBASICLIB", finish.getString("source"))
    }

    private fun request(args: String) = JSONArray(args).getJSONObject(0)
}
