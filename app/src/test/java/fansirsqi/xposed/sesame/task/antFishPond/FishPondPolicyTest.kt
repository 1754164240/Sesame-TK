package fansirsqi.xposed.sesame.task.antFishPond

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FishPondPolicyTest {

    @Test
    fun `所有已完成任务领奖且所有待办动作均尝试推进`() {
        assertEquals(
            FishPondTaskDecision.CLAIM,
            FishPondPolicy.decideTask(task(status = "FINISHED"))
        )
        listOf(
            "VISIT",
            "ADD_HOME",
            "TRIGGER",
            "PUSH_SUBSCRIBE",
            "EXCH_MANURE_4_ROD",
            "OFFLINE_SHARE"
        ).forEach { actionType ->
            assertEquals(
                actionType,
                FishPondTaskDecision.COMPLETE,
                FishPondPolicy.decideTask(
                    task(
                        type = "TASK_$actionType",
                        status = "TODO",
                        actionType = actionType,
                        title = "任务-$actionType",
                        adBizNo = if (actionType == "VISIT") "ad-1" else ""
                    )
                )
            )
        }
        assertEquals(
            FishPondTaskDecision.COMPLETE,
            FishPondPolicy.decideTask(
                task(type = "NEW_TASK", status = "TODO", actionType = "UNKNOWN")
            )
        )
    }

    @Test
    fun `浏览时长优先读取浮球配置并兼容标题秒数`() {
        val capturedTask = JSONObject(
            """
                {
                  "taskDisplayConfig": {
                    "title": "看精选商品得钓竿",
                    "desc": "浏览15秒得钓竿",
                    "floatBallConfig": {
                      "floatBallDuration": 15
                    }
                  }
                }
            """.trimIndent()
        )
        val titleTask = JSONObject(
            """{"taskDisplayConfig":{"title":"玩寻道大千30s"}}"""
        )
        val unknownTask = JSONObject(
            """{"taskDisplayConfig":{"title":"普通任务"}}"""
        )

        assertEquals(
            15_000L,
            FishPondPolicy.browseDurationMillis(capturedTask)
        )
        assertEquals(30_000L, FishPondPolicy.browseDurationMillis(titleTask))
        assertEquals(
            15_000L,
            FishPondPolicy.browseDurationMillis(unknownTask)
        )
    }

    @Test
    fun `从广告任务链接解码广告位和页面地址`() {
        val task = JSONObject(
            """
            {
              "taskDisplayConfig": {
                "targetUrl": "alipays://platformapi/startapp?appId=2060090000304921&renderConfigKey=adPosId%232024042922700095310%23%23spaceCode%23TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW&spaceCode=TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW&url=https%3A%2F%2Frender.alipay.com%2Fp%2Fyuyan%2Ffishing-landing.html%3FcaprMode%3Dsync"
              }
            }
            """.trimIndent()
        )

        val config = FishPondPolicy.extractAdConfig(task)

        assertEquals(
            "adPosId#2024042922700095310##spaceCode#TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW",
            config.querySpaceCode
        )
        assertEquals(
            "TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW",
            config.exposureSpaceCode
        )
        assertEquals(
            "https://render.alipay.com/p/yuyan/fishing-landing.html?caprMode=sync",
            config.pageUrl
        )
    }

    @Test
    fun `广告配置时长优先于任务描述`() {
        val response = JSONObject(
            """{"success":true,"resultData":{"duration":15.0}}"""
        )
        val task = JSONObject(
            """{"taskDisplayConfig":{"desc":"浏览30秒得钓竿"}}"""
        )

        assertEquals(
            15_000L,
            FishPondPolicy.adDurationMillis(response, task)
        )
    }

    @Test
    fun `普通安全任务按状态完成领取或等待`() {
        assertEquals(
            FishPondTaskDecision.COMPLETE,
            FishPondPolicy.decideTask(
                task(type = "FISH_TASK_15", status = "TODO", actionType = "VISIT")
            )
        )
        assertEquals(
            FishPondTaskDecision.COMPLETE,
            FishPondPolicy.decideTask(
                task(type = "FISH_TASK_15", status = "TO_DO", actionType = "VISIT")
            )
        )
        assertEquals(
            FishPondTaskDecision.CLAIM,
            FishPondPolicy.decideTask(task(status = "TO_RECEIVE"))
        )
        assertEquals(
            FishPondTaskDecision.WAIT,
            FishPondPolicy.decideTask(task(status = "RECEIVED"))
        )
        assertEquals(
            FishPondTaskDecision.SKIP,
            FishPondPolicy.decideTask(task(status = "UNKNOWN"))
        )
    }

    @Test
    fun `钓鱼必须有鱼竿和风控令牌且未达到每日上限`() {
        assertTrue(FishPondPolicy.canContinueFishing(1, 0, 30, true))
        assertFalse(FishPondPolicy.canContinueFishing(0, 0, 30, true))
        assertFalse(FishPondPolicy.canContinueFishing(1, 0, 30, false))
        assertFalse(FishPondPolicy.canContinueFishing(1, 30, 30, true))
        assertTrue(FishPondPolicy.canContinueFishing(1, 200, 0, true))
    }

    @Test
    fun `识别常见成功响应且未知结构不视为成功`() {
        assertTrue(FishPondPolicy.isRpcSuccess(JSONObject("""{"success":true}""")))
        assertTrue(FishPondPolicy.isRpcSuccess(JSONObject("""{"resultCode":"SUCCESS"}""")))
        assertTrue(FishPondPolicy.isRpcSuccess(JSONObject("""{"code":"100"}""")))
        assertTrue(
            FishPondPolicy.isRpcSuccess(
                JSONObject("""{"result":{"success":true}}""")
            )
        )
        assertFalse(FishPondPolicy.isRpcSuccess(JSONObject("""{"success":false}""")))
        assertFalse(
            FishPondPolicy.isRpcSuccess(
                JSONObject("""{"success":false,"resultCode":"100"}""")
            )
        )
        assertFalse(FishPondPolicy.isRpcSuccess(JSONObject("""{"code":"SYSTEM_ERROR"}""")))
        assertFalse(FishPondPolicy.isRpcSuccess(JSONObject()))
    }

    private fun task(
        type: String = "FISH_TASK_14",
        sceneCode: String = "ANTFISHPOND_TASK",
        status: String = "TODO",
        title: String = "浏览鱼池",
        adBizNo: String = "",
        actionType: String = "GOFISH"
    ) = FishPondTaskSnapshot(type, sceneCode, status, title, adBizNo, actionType)
}
