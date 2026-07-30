package fansirsqi.xposed.sesame.task.antStall

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StallXlightWorkflowTest {

    @Test
    fun `广告事件完成且任务状态推进后确认`() {
        var finishedEventId = ""
        val pauses = mutableListOf<Long>()
        val workflow = workflow(
            queryAd = {
                xlightResponse("play-1", "event-1", duration = 7L)
            },
            finishEvent = { playingBizId, event ->
                assertEquals("play-1", playingBizId)
                finishedEventId = event.getString("eventId")
                """{"success":true}"""
            },
            refreshTask = {
                StallTaskState(XLIGHT_TYPE, "FINISHED")
            },
            pauseAfterAction = pauses::add
        )

        val result = workflow.run(StallTaskState(XLIGHT_TYPE, "TODO"))

        assertEquals("event-1", finishedEventId)
        assertEquals(listOf(7000L), pauses)
        assertEquals(StallXlightOutcome.CONFIRMED, result.outcome)
    }

    @Test
    fun `XLight流量限制不提交广告事件`() {
        var finishCalls = 0
        val result = workflow(
            queryAd = {
                """{"retCode":"217","sspErrorCode":"61002"}"""
            },
            finishEvent = { _, _ ->
                finishCalls++
                """{"success":true}"""
            }
        ).run(StallTaskState(XLIGHT_TYPE, "TODO"))

        assertEquals(0, finishCalls)
        assertEquals(StallXlightOutcome.LIMITED, result.outcome)
    }

    @Test
    fun `广告响应缺少播放编号或事件时保留重试`() {
        val missingPlayId = workflow(
            queryAd = {
                xlightResponse("", "event-1")
            }
        ).run(StallTaskState(XLIGHT_TYPE, "TODO"))
        val missingEvents = workflow(
            queryAd = {
                JSONObject()
                    .put("success", true)
                    .put(
                        "playingResult",
                        JSONObject()
                            .put("playingBizId", "play-1")
                            .put(
                                "eventRewardDetail",
                                JSONObject().put(
                                    "eventRewardInfoList",
                                    JSONArray()
                                )
                            )
                    )
                    .toString()
            }
        ).run(StallTaskState(XLIGHT_TYPE, "TODO"))

        assertEquals(StallXlightOutcome.RETRY, missingPlayId.outcome)
        assertEquals(StallXlightOutcome.RETRY, missingEvents.outcome)
    }

    @Test
    fun `广告完成后任务状态不推进不得确认`() {
        val result = workflow(
            queryAd = { xlightResponse("play-1", "event-1") },
            refreshTask = {
                StallTaskState(XLIGHT_TYPE, "TODO")
            }
        ).run(StallTaskState(XLIGHT_TYPE, "TODO"))

        assertEquals(StallXlightOutcome.RETRY, result.outcome)
    }

    private fun workflow(
        queryAd: () -> String,
        finishEvent: (String, JSONObject) -> String = { _, _ ->
            """{"success":true}"""
        },
        refreshTask: () -> StallTaskState? = {
            StallTaskState(XLIGHT_TYPE, "FINISHED")
        },
        pauseAfterAction: (Long) -> Unit = {}
    ): StallXlightWorkflow {
        return StallXlightWorkflow(
            queryAd = queryAd,
            finishEvent = finishEvent,
            refreshTask = refreshTask,
            pauseAfterAction = pauseAfterAction,
            isActionSuccess = { response ->
                JSONObject(response).optBoolean("success", false)
            }
        )
    }

    private fun xlightResponse(
        playingBizId: String,
        eventId: String,
        duration: Long = 0L
    ): String {
        return JSONObject()
            .put("success", true)
            .put(
                "playingResult",
                JSONObject()
                    .put("playingBizId", playingBizId)
                    .put(
                        "eventRewardDetail",
                        JSONObject().put(
                            "eventRewardInfoList",
                            JSONArray().put(
                                JSONObject()
                                    .put("eventId", eventId)
                                    .put("duration", duration)
                            )
                        )
                    )
            )
            .toString()
    }

    companion object {
        private const val XLIGHT_TYPE =
            "ANTSTALL_XLIGHT_VARIABLE_AWARD"
    }
}
