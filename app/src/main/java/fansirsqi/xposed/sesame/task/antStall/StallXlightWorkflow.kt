package fansirsqi.xposed.sesame.task.antStall

import org.json.JSONArray
import org.json.JSONObject

enum class StallXlightOutcome {
    CONFIRMED,
    RETRY,
    LIMITED
}

data class StallXlightResult(
    val outcome: StallXlightOutcome,
    val refreshedState: StallTaskState?,
    val message: String
)

class StallXlightWorkflow(
    private val queryAd: () -> String,
    private val finishEvent: (String, JSONObject) -> String,
    private val refreshTask: () -> StallTaskState?,
    private val pauseAfterAction: (Long) -> Unit,
    private val isActionSuccess: (String) -> Boolean
) {

    fun run(before: StallTaskState): StallXlightResult {
        val response = runCatching { JSONObject(queryAd()) }.getOrNull()
            ?: return retry(null, "XLight 查询响应不可解析")
        if (StallTaskProtocol.isXlightTrafficLimited(response)) {
            return StallXlightResult(
                StallXlightOutcome.LIMITED,
                null,
                "XLight 流量受限"
            )
        }
        val playingResult = response.optJSONObject("playingResult")
            ?: response.optJSONObject("resData")
                ?.optJSONObject("playingResult")
            ?: return retry(null, "XLight 缺少播放结果")
        val playingBizId = playingResult.optString("playingBizId").trim()
        if (playingBizId.isBlank()) {
            return retry(null, "XLight 缺少播放业务编号")
        }
        val events = playingResult.optJSONObject("eventRewardDetail")
            ?.optJSONArray("eventRewardInfoList")
            ?: JSONArray()
        if (events.length() == 0) {
            return retry(null, "XLight 缺少广告事件")
        }
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index)
                ?: return retry(null, "XLight 广告事件结构未知")
            val finishResponse = runCatching {
                finishEvent(playingBizId, event)
            }.getOrNull()
            if (
                finishResponse == null ||
                !runCatching {
                    isActionSuccess(finishResponse)
                }.getOrDefault(false)
            ) {
                return retry(null, "XLight 广告事件完成失败")
            }
            pauseAfterAction(resolveDurationMillis(event))
        }
        val refreshed = runCatching(refreshTask).getOrNull()
        return if (StallTaskProtocol.isAdvanced(before, refreshed)) {
            StallXlightResult(
                StallXlightOutcome.CONFIRMED,
                refreshed,
                "XLight 任务状态已推进"
            )
        } else {
            retry(refreshed, "XLight 完成后任务状态无进展")
        }
    }

    private fun retry(
        state: StallTaskState?,
        message: String
    ): StallXlightResult {
        return StallXlightResult(StallXlightOutcome.RETRY, state, message)
    }

    private fun resolveDurationMillis(event: JSONObject): Long {
        val seconds = sequenceOf(
            event.optLong("duration", 0L),
            event.optLong("waitTime", 0L),
            event.optLong("rewardTime", 0L)
        ).firstOrNull { it > 0L } ?: 5L
        return seconds.coerceIn(1L, 60L) * 1000L
    }
}
