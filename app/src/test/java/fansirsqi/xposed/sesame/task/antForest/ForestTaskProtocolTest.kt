package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestTaskProtocolTest {

    @Test
    fun `未确认签到时查询全部森林任务源`() {
        assertEquals(
            listOf(
                ForestTaskQuerySource.POPUP,
                ForestTaskQuerySource.HOME_LEAVES,
                ForestTaskQuerySource.TAKE_LOOK_END,
                ForestTaskQuerySource.HOME,
                ForestTaskQuerySource.OPEN_GREEN_HOME
            ),
            ForestTaskProtocol.querySources(signConfirmed = false)
        )
    }

    @Test
    fun `确认签到后只省略签到主任务源`() {
        val sources = ForestTaskProtocol.querySources(signConfirmed = true)

        assertFalse(sources.contains(ForestTaskQuerySource.HOME))
        assertTrue(sources.contains(ForestTaskQuerySource.POPUP))
        assertTrue(sources.contains(ForestTaskQuerySource.HOME_LEAVES))
        assertTrue(sources.contains(ForestTaskQuerySource.TAKE_LOOK_END))
        assertTrue(sources.contains(ForestTaskQuerySource.OPEN_GREEN_HOME))
    }

    @Test
    fun `普通任务查询参数保留来源和版本扩展`() {
        val request = ForestTaskProtocol.buildTaskListArgs(
            fromAct = "home_leaves_task_list",
            source = "chInfo_ch_appcenter__chsub_9patch",
            version = "20250813",
            extend = JSONObject()
                .put("osType", "android")
                .put("version", "20250813")
        ).getJSONObject(0)

        assertEquals("home_leaves_task_list", request.getString("fromAct"))
        assertEquals(
            "chInfo_ch_appcenter__chsub_9patch",
            request.getString("source")
        )
        assertEquals("20250813", request.getString("version"))
        assertEquals(
            "android",
            request.getJSONObject("extend").getString("osType")
        )
    }

    @Test
    fun `弹窗任务查询明确关闭初始化型副作用`() {
        val request = ForestTaskProtocol.buildPopupTaskArgs(
            source = "chInfo_ch_appcenter__chsub_9patch",
            nativeVersion = "10.7.30.8000",
            version = "20250813"
        ).getJSONObject(0)

        assertEquals("pop_task", request.getString("fromAct"))
        assertFalse(request.getBoolean("needInitSign"))
        assertFalse(request.getBoolean("needTeamPlantRewardInfo"))
        assertEquals(
            listOf("TODO", "FINISHED"),
            request.getJSONArray("statusList").let { array ->
                (0 until array.length()).map(array::getString)
            }
        )
        assertEquals(
            "10.7.30.8000",
            request.getJSONObject("extend").getString("nativeVersion")
        )
    }

    @Test
    fun `活力任务查询携带明确业务扩展`() {
        val request = ForestTaskProtocol.buildOpenGreenTaskArgs(
            sceneCode = "ANTFOREST_VITALITY_TASK",
            source = "chInfo_ch_appcenter__chsub_9patch",
            extend = JSONObject()
                .put("businessSource", "ANTFOREST-home_task_list")
                .put("osType", "android")
                .put("version", "20260109")
        ).getJSONObject(0)

        assertEquals("RPC", request.getString("requestType"))
        assertEquals(
            "ANTFOREST_VITALITY_TASK",
            request.getString("sceneCode")
        )
        assertEquals(
            "ANTFOREST-home_task_list",
            request.getJSONObject("extend").getString("businessSource")
        )
    }
}
