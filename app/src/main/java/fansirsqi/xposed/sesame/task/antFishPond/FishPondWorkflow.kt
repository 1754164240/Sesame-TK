package fansirsqi.xposed.sesame.task.antFishPond

import org.json.JSONObject

data class FishPondRunResult(
    val confirmedFishCount: Int,
    val retryNeeded: Boolean,
    val progressed: Boolean
)

class FishPondWorkflow(private val gateway: FishPondGateway) {

    fun run(
        taskEnabled: Boolean,
        autoFishEnabled: Boolean,
        todayFishCount: Int,
        dailyLimit: Int,
        riskToken: String?,
        onFishConfirmed: (Int) -> Unit = {}
    ): FishPondRunResult {
        if (!taskEnabled && !autoFishEnabled) {
            return FishPondRunResult(0, retryNeeded = false, progressed = false)
        }

        val state = RunState()
        var index = parseSuccess(gateway.fishpondIndex())
            ?: return state.result(retryNeeded = true)
        if (canExchange(index)) {
            if (parseSuccess(gateway.fishpondExchangeReward()) == null) {
                return state.result(retryNeeded = true)
            }
            state.markProgress()
            index = parseSuccess(gateway.fishpondIndex())
                ?: return state.result(retryNeeded = true)
        }

        val token = riskToken?.trim().orEmpty()
        var usedToday = todayFishCount
        repeat(MAX_CLOSURE_ROUNDS) {
            val progressBeforeRound = state.progressEvents
            if (taskEnabled && !handleTasks(state)) {
                return state.result(retryNeeded = true)
            }

            index = refreshIndex(BASE_SYNC_TYPES, state)
                ?: return state.result(retryNeeded = true)
            var rodCount = extractRodCount(index)
            if (rodCount < 0) {
                return state.result(retryNeeded = true)
            }

            while (
                autoFishEnabled &&
                FishPondPolicy.canContinueFishing(
                    rodCount = rodCount,
                    todayCount = usedToday,
                    dailyLimit = dailyLimit,
                    hasRiskToken = token.isNotEmpty()
                )
            ) {
                val angle = parseSuccess(gateway.fishpondAngle(token))
                    ?: return state.result(retryNeeded = true)
                state.confirmedFishCount++
                usedToday++
                onFishConfirmed(usedToday)
                state.markProgress()

                val angleInfo = angleInfo(angle)
                if (angleInfo.optString("fishType") == "WELFARE_FISH") {
                    val bizNo = angleInfo.optString("bizNo").trim()
                    if (bizNo.isEmpty()) {
                        return state.result(retryNeeded = true)
                    }
                    if (parseSuccess(
                            gateway.fishpondAngleRodPositioning(
                                bizNo,
                                "SPECIAL_BIG_ZONE"
                            )
                        ) == null
                    ) {
                        return state.result(retryNeeded = true)
                    }
                }

                index = refreshIndex(FISH_SYNC_TYPES, state)
                    ?: return state.result(retryNeeded = true)
                rodCount = extractRodCount(index)
                if (rodCount < 0) {
                    return state.result(retryNeeded = true)
                }
            }

            if (state.progressEvents == progressBeforeRound) {
                return state.result(retryNeeded = false)
            }
        }

        return state.result(retryNeeded = false)
    }

    private fun handleTasks(state: RunState): Boolean {
        val subplotResponse = parseSuccess(gateway.querySubplotsActivity()) ?: return false
        val subplotData = payload(subplotResponse)
        val activities = subplotData.optJSONArray("subplotsActivityList") ?: return false
        for (index in 0 until activities.length()) {
            val activity = activities.optJSONObject(index) ?: return false
            val activityType = activity.optString("activityType")
                .ifBlank { activity.optString("activityId") }
            val actionType = when {
                activityType == "GIFT_BOX" &&
                    (activity.optString("status") == "TODO" ||
                        parseObject(activity.optString("extend"))
                            ?.optString("status") == "TODO") -> "receiveAward"

                activityType == "TOMORROW_ROD" &&
                    activity.optString("status") == "TODAY_TODO" -> "FINISH"

                else -> null
            } ?: continue
            val activityKey = "$activityType|$actionType"
            if (activityKey in state.handledActivities) {
                continue
            }

            if (parseSuccess(
                    gateway.triggerSubplotsActivity(activityType, actionType)
                ) == null
            ) {
                return false
            }
            state.handledActivities += activityKey
            state.markProgress()
            val syncTypes = if (activityType == "GIFT_BOX") {
                listOf("GIFT_BOX", "TASK_DISPLAY")
            } else {
                listOf("TOMORROW_ROD")
            }
            if (!syncAfterAction(syncTypes)) {
                return false
            }
        }

        val taskResponse = parseSuccess(gateway.listTask()) ?: return false
        val taskData = payload(taskResponse)
        val signResult = handleSign(taskData, state)
        if (signResult == false) {
            return false
        }

        val taskList = taskData.optJSONArray("taskList") ?: return false
        for (index in 0 until taskList.length()) {
            val task = taskList.optJSONObject(index) ?: return false
            val taskType = task.optString("taskId")
                .ifBlank { task.optString("taskType") }
            if (taskType.isBlank()) {
                continue
            }
            val sceneCode = task.optString("sceneCode")
                .ifBlank { "ANTFISHPOND_TASK" }
            val snapshot = FishPondTaskSnapshot(
                type = taskType,
                sceneCode = sceneCode,
                status = task.optString("taskStatus"),
                title = task.optJSONObject("taskDisplayConfig")
                    ?.optString("title")
                    ?.takeIf { it.isNotBlank() }
                    ?: task.optString("taskTitle")
                        .ifBlank { task.optString("title") },
                adBizNo = task.optString("adBizNo"),
                actionType = task.optString("actionType")
            )

            val decision = FishPondPolicy.decideTask(snapshot)
            val taskActionKey = "$sceneCode|$taskType|$decision"
            if (taskActionKey in state.handledTaskActions) {
                continue
            }
            val response = when (decision) {
                FishPondTaskDecision.CLAIM ->
                    gateway.receiveTaskAward(taskType, sceneCode)

                FishPondTaskDecision.COMPLETE ->
                    gateway.finishTask(taskType, sceneCode)

                FishPondTaskDecision.WAIT,
                FishPondTaskDecision.SKIP -> continue
            }
            if (parseSuccess(response) == null) {
                return false
            }
            state.handledTaskActions += taskActionKey
            state.markProgress()
            if (!syncAfterAction(listOf("TASK_DISPLAY"))) {
                return false
            }
        }
        return true
    }

    private fun handleSign(data: JSONObject, state: RunState): Boolean? {
        val signList = data.optJSONObject("signInfo")
            ?.optJSONArray("list")
            ?: return null
        if (state.signHandled) {
            return true
        }
        for (index in 0 until signList.length()) {
            val signItem = signList.optJSONObject(index) ?: return false
            if (!signItem.optBoolean("today")) {
                continue
            }
            if (signItem.optBoolean("signed")) {
                state.signHandled = true
                return true
            }
            val signKey = signItem.optString("signKey").trim()
            if (signKey.isEmpty()) {
                return false
            }
            if (parseSuccess(gateway.sign(signKey)) == null) {
                return false
            }
            state.signHandled = true
            state.markProgress()
            if (!syncAfterAction(listOf("TASK_DISPLAY"))) {
                return false
            }
            break
        }
        return true
    }

    private fun syncAfterAction(syncTypes: List<String>): Boolean {
        return parseSuccess(gateway.fishpondSyncIndex(syncTypes)) != null
    }

    private fun refreshIndex(syncTypes: List<String>, state: RunState): JSONObject? {
        val synced = parseSuccess(gateway.fishpondSyncIndex(syncTypes)) ?: return null
        if (!canExchange(synced)) {
            return synced
        }
        if (parseSuccess(gateway.fishpondExchangeReward()) == null) {
            return null
        }
        state.markProgress()
        return parseSuccess(gateway.fishpondIndex())
    }

    private fun parseSuccess(raw: String): JSONObject? {
        val response = parseObject(raw) ?: return null
        return response.takeIf(FishPondPolicy::isRpcSuccess)
    }

    private fun parseObject(raw: String): JSONObject? {
        if (raw.isBlank()) {
            return null
        }
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun payload(response: JSONObject): JSONObject {
        return response.optJSONObject("data")
            ?: response.optJSONObject("result")
            ?: response
    }

    private fun extractRodCount(response: JSONObject): Int {
        val data = payload(response)
        if (data.has("rodSumCount")) {
            return data.optInt("rodSumCount", -1)
        }
        val rods = data.optJSONArray("rodAssetInfoList") ?: return -1
        var count = 0
        for (index in 0 until rods.length()) {
            count += rods.optJSONObject(index)?.optInt("rodCount", 0) ?: 0
        }
        return count
    }

    private fun canExchange(response: JSONObject): Boolean {
        val data = payload(response)
        return data.optBoolean("canExchange") ||
            data.optJSONObject("roundInfo")?.optBoolean("canExchange") == true
    }

    private fun angleInfo(response: JSONObject): JSONObject {
        val data = payload(response)
        return data.optJSONObject("angleResultInfo")
            ?: data.optJSONObject("fishResultInfo")
            ?: data
    }

    private class RunState {
        var confirmedFishCount = 0
        var progressed = false
        var progressEvents = 0
        var signHandled = false
        val handledActivities = mutableSetOf<String>()
        val handledTaskActions = mutableSetOf<String>()

        fun markProgress() {
            progressed = true
            progressEvents++
        }

        fun result(retryNeeded: Boolean): FishPondRunResult {
            return FishPondRunResult(confirmedFishCount, retryNeeded, progressed)
        }
    }

    companion object {
        private const val MAX_CLOSURE_ROUNDS = 3
        private val BASE_SYNC_TYPES =
            listOf("GIFT_BOX", "TASK_DISPLAY", "TOMORROW_ROD")
        private val FISH_SYNC_TYPES =
            listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD")
    }
}
