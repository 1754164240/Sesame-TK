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

    @Test
    fun `广告配置和曝光参数使用抓包确认字段`() {
        val config = request(
            AntFishPondRpcCall.buildAdTaskConfigArgs("ad-position")
        )
        val exposure = request(
            AntFishPondRpcCall.buildAdExposureArgs(
                spaceCode = "TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW",
                pageUrl = "https://render.alipay.com/fishing-landing.html",
                session = "session-1"
            )
        )

        assertEquals("ad-position", config.getString("spaceCode"))
        assertEquals(
            "TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW",
            exposure.getJSONObject("positionRequest").getString("spaceCode")
        )
        val pageInfo = exposure.getJSONObject("sdkPageInfo")
        assertEquals("session-1", pageInfo.getString("session"))
        assertEquals(
            "https://render.alipay.com/fishing-landing.html",
            pageInfo.getString("pageUrl")
        )
        assertEquals("2060090000304921", pageInfo.getString("unionAppId"))
    }

    private fun request(args: String) = JSONArray(args).getJSONObject(0)
}
