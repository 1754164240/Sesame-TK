package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AntAiFishRpcProtocolTest {

    @Test
    fun `状态和主页参数使用抓包来源`() {
        val status = request(AntAiFishRpcCall.buildStatusArgs("uid-status"))
        val homepage = request(
            AntAiFishRpcCall.buildHomepageArgs("uid-home")
        )

        assertEquals("nengliangtixing", status.getString("source"))
        assertEquals("uid-status", status.getString("uniqueId"))
        assertEquals("ANT_OCEAN", homepage.getString("source"))
        assertEquals("uid-home", homepage.getString("uniqueId"))
    }

    @Test
    fun `主任务列表参数使用开放绿色任务协议`() {
        val request = request(
            AntAiFishRpcCall.buildListTasksArgs(
                AiFishProtocol.MAIN_SCENE,
                "uid-list"
            )
        )

        assertEquals("normal", request.getJSONObject("extend").getString("appMode"))
        assertEquals("RPC", request.getString("requestType"))
        assertEquals(AiFishProtocol.MAIN_SCENE, request.getString("sceneCode"))
        assertEquals("ANTAIFISH", request.getString("source"))
        assertEquals("uid-list", request.getString("uniqueId"))
    }

    @Test
    fun `完成任务参数携带动态业务流水`() {
        val request = request(
            AntAiFishRpcCall.buildFinishTaskArgs(
                AiFishProtocol.MAIN_SCENE,
                "AIFISH_SHJF",
                "biz-1",
                "uid-finish"
            )
        )

        assertEquals("biz-1", request.getString("outBizNo"))
        assertEquals("RPC", request.getString("requestType"))
        assertEquals(AiFishProtocol.MAIN_SCENE, request.getString("sceneCode"))
        assertEquals("ANTAIFISH", request.getString("source"))
        assertEquals("AIFISH_SHJF", request.getString("taskType"))
        assertEquals("uid-finish", request.getString("uniqueId"))
    }

    @Test
    fun `领取奖励参数的限制标记是布尔值`() {
        val request = request(
            AntAiFishRpcCall.buildReceiveTaskAwardArgs(
                AiFishProtocol.MAIN_SCENE,
                "AIFISH_SHJF",
                "uid-receive"
            )
        )

        assertFalse(request.getBoolean("ignoreLimit"))
        assertEquals("RPC", request.getString("requestType"))
        assertEquals(AiFishProtocol.MAIN_SCENE, request.getString("sceneCode"))
        assertEquals("ANTAIFISH", request.getString("source"))
        assertEquals("AIFISH_SHJF", request.getString("taskType"))
        assertEquals("uid-receive", request.getString("uniqueId"))
    }

    @Test
    fun `找回和摸鱼参数使用海洋来源`() {
        val rescue = request(
            AntAiFishRpcCall.buildOceanActionArgs("uid-rescue")
        )
        val touch = request(
            AntAiFishRpcCall.buildOceanActionArgs("uid-touch")
        )

        assertEquals("ANT_OCEAN", rescue.getString("source"))
        assertEquals("uid-rescue", rescue.getString("uniqueId"))
        assertEquals("ANT_OCEAN", touch.getString("source"))
        assertEquals("uid-touch", touch.getString("uniqueId"))
    }

    private fun request(args: String) = JSONArray(args).getJSONObject(0)
}
