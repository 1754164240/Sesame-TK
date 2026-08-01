package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AntOceanAiFishRpcProtocolTest {

    @Test
    fun `七个AI摸鱼动作映射到抓包端点`() {
        assertEquals("alipay.antaifish.h5.status", AntOceanRpcCall.AI_FISH_STATUS_METHOD)
        assertEquals("alipay.antaifish.h5.homepage", AntOceanRpcCall.AI_FISH_HOMEPAGE_METHOD)
        assertEquals("com.alipay.antieptask.listTaskopengreen", AntOceanRpcCall.AI_FISH_LIST_TASKS_METHOD)
        assertEquals("com.alipay.antiep.finishTask", AntOceanRpcCall.AI_FISH_FINISH_TASK_METHOD)
        assertEquals("com.alipay.antieptask.receiveTaskAwardopengreen", AntOceanRpcCall.AI_FISH_RECEIVE_AWARD_METHOD)
        assertEquals("alipay.antaifish.h5.rescueFish", AntOceanRpcCall.AI_FISH_RESCUE_METHOD)
        assertEquals("alipay.antaifish.h5.touchfish", AntOceanRpcCall.AI_FISH_TOUCH_METHOD)
    }

    @Test
    fun `状态主页和海洋动作使用抓包来源`() {
        val status = request(AntOceanRpcCall.buildAiFishStatusArgs("uid-status"))
        val homepage = request(AntOceanRpcCall.buildAiFishOceanActionArgs("uid-home"))

        assertEquals("nengliangtixing", status.getString("source"))
        assertEquals("uid-status", status.getString("uniqueId"))
        assertEquals("ANT_OCEAN", homepage.getString("source"))
        assertEquals("uid-home", homepage.getString("uniqueId"))
    }

    @Test
    fun `任务列表参数使用AI摸鱼场景`() {
        val value = request(
            AntOceanRpcCall.buildAiFishListTasksArgs("ANTAIFISH", "uid-list")
        )

        assertEquals("normal", value.getJSONObject("extend").getString("appMode"))
        assertEquals("RPC", value.getString("requestType"))
        assertEquals("ANTAIFISH", value.getString("sceneCode"))
        assertEquals("ANTAIFISH", value.getString("source"))
        assertEquals("uid-list", value.getString("uniqueId"))
    }

    @Test
    fun `完成和领奖参数携带场景任务及业务流水`() {
        val finish = request(
            AntOceanRpcCall.buildAiFishFinishTaskArgs(
                "ANTAIFISH", "AIFISH_SHJF", "biz-1", "uid-finish"
            )
        )
        val receive = request(
            AntOceanRpcCall.buildAiFishReceiveTaskAwardArgs(
                "ANTAIFISH", "AIFISH_SHJF", "uid-receive"
            )
        )

        assertEquals("biz-1", finish.getString("outBizNo"))
        assertEquals("RPC", finish.getString("requestType"))
        assertEquals("ANTAIFISH", finish.getString("source"))
        assertEquals("AIFISH_SHJF", finish.getString("taskType"))
        assertFalse(receive.getBoolean("ignoreLimit"))
        assertEquals("ANTAIFISH", receive.getString("sceneCode"))
        assertEquals("uid-receive", receive.getString("uniqueId"))
    }

    private fun request(args: String) = JSONArray(args).getJSONObject(0)
}
