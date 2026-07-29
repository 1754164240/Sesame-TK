package fansirsqi.xposed.sesame.task.antOrchard

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntOrchardRpcProtocolTest {

    @Test
    fun `任务查询使用最新果园容器参数`() {
        val args = JSONArray(
            AntOrchardRpcCall.buildOrchardListTaskArgs("source-test")
        ).getJSONObject(0)

        assertEquals("20260721.01", args.getString("version"))
        assertEquals("normal", args.getString("appMode"))
        assertFalse(args.getBoolean("addWidget"))
        assertTrue(args.getBoolean("hasYebActivityEntrance"))
        assertEquals(
            listOf("main", "yeb"),
            args.getJSONArray("enableSwitchSceneList").let { array ->
                (0 until array.length()).map(array::getString)
            }
        )
        assertEquals(
            listOf("help", "team"),
            args.getJSONArray("enableTeamType").let { array ->
                (0 until array.length()).map(array::getString)
            }
        )
    }

    @Test
    fun `农场乐园查询和领奖使用专用协议参数`() {
        val queryArgs = JSONArray(
            AntOrchardRpcCall.buildQueryOptionalPlayArgs()
        ).getJSONObject(0)
        val claimArgs = JSONArray(
            AntOrchardRpcCall.buildLeyuanClaimArgs(
                "ANTORCHARD_LEYUAN_DAILY_TASK",
                "DAILY_LEYUAN_QIANDAO",
                20
            )
        ).getJSONObject(0)

        assertEquals("ANTORCHARD", queryArgs.getString("bizType"))
        assertEquals("H5", queryArgs.getString("source"))
        assertEquals("10.8.20", queryArgs.getString("version"))
        assertEquals("antorchard", claimArgs.getString("source"))
        assertEquals(20, claimArgs.getInt("awardCountForReceive"))
        assertEquals(
            "ANTORCHARD_LEYUAN_DAILY_TASK",
            claimArgs.getString("sceneCode")
        )
    }
}
