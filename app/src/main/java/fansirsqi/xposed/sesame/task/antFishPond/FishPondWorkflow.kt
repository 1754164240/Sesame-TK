package fansirsqi.xposed.sesame.task.antFishPond

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

data class FishPondRunResult(
    val confirmedFishCount: Int,
    val retryNeeded: Boolean,
    val progressed: Boolean
)

class FishPondWorkflow(
    private val gateway: FishPondGateway,
    private val waitForTask: suspend (Long) -> Unit = { millis -> delay(millis) }
) {

    suspend fun run(
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
        var index = callSuccess(gateway::fishpondIndex)
            ?: return state.result(retryNeeded = true)
        if (canExchange(index)) {
            if (callSuccess(gateway::fishpondExchangeReward) == null) {
                return state.result(retryNeeded = true)
            }
            state.markProgress()
            index = callSuccess(gateway::fishpondIndex)
                ?: return state.result(retryNeeded = true)
        }

        val token = riskToken?.trim().orEmpty()
        var usedToday = todayFishCount
        repeat(MAX_CLOSURE_ROUNDS) {
            val progressBeforeRound = state.progressEvents
            if (taskEnabled && !handleTasks(state)) {
                state.markRetry()
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
                val angle = callSuccess { gateway.fishpondAngle(token) }
                    ?: return state.result(retryNeeded = true)

                val angleInfo = angleInfo(angle)
                val needsPositioning =
                    payload(angle).optBoolean("needRodPositioning") ||
                        angleInfo.optString("fishType") == "WELFARE_FISH" ||
                        angleInfo.optBoolean("needRodPositioning")
                if (needsPositioning) {
                    val bizNo = angleInfo.optString("bizNo").trim()
                    if (bizNo.isEmpty()) {
                        return state.result(retryNeeded = true)
                    }
                    if (callSuccess {
                            gateway.fishpondAngleRodPositioning(
                                bizNo,
                                "SPECIAL_BIG_ZONE"
                            )
                        } == null
                    ) {
                        return state.result(retryNeeded = true)
                    }
                }
                state.confirmedFishCount++
                usedToday++
                onFishConfirmed(usedToday)
                state.markProgress()

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

    private suspend fun handleTasks(state: RunState): Boolean {
        var allSucceeded = handleActivities(state)
        val taskResponse = callSuccess(gateway::listTask) ?: return false
        val taskData = payload(taskResponse)
        val signResult = handleSign(taskData, state)
        if (signResult == false) {
            allSucceeded = false
        }

        val taskList = taskData.optJSONArray("taskList") ?: return false
        for (index in 0 until taskList.length()) {
            val task = taskList.optJSONObject(index)
            if (task == null) {
                allSucceeded = false
                continue
            }
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
            when (decision) {
                FishPondTaskDecision.WAIT,
                FishPondTaskDecision.SKIP -> continue
                else -> Unit
            }
            state.handledTaskActions += taskActionKey
            val actionSucceeded = try {
                when (decision) {
                    FishPondTaskDecision.CLAIM -> {
                        callSuccess {
                            gateway.receiveTaskAward(taskType, sceneCode)
                        } != null
                    }

                    FishPondTaskDecision.COMPLETE -> {
                        completeTask(task, snapshot)
                    }

                    FishPondTaskDecision.WAIT,
                    FishPondTaskDecision.SKIP -> true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!actionSucceeded) {
                allSucceeded = false
                continue
            }
            val syncTypes = if (decision == FishPondTaskDecision.COMPLETE) {
                TASK_COMPLETION_SYNC_TYPES
            } else {
                listOf("TASK_DISPLAY")
            }
            if (!syncAfterAction(syncTypes)) {
                allSucceeded = false
                continue
            }
            if (decision == FishPondTaskDecision.COMPLETE &&
                snapshot.adBizNo.isNotBlank() &&
                !verifyTaskState(
                    snapshot,
                    setOf("FINISHED", "TO_RECEIVE", "RECEIVED", "DONE")
                )
            ) {
                allSucceeded = false
                state.markRetry()
                continue
            }
            if (decision == FishPondTaskDecision.CLAIM &&
                !verifyTaskState(snapshot, setOf("RECEIVED", "DONE"))
            ) {
                allSucceeded = false
                state.markRetry()
                continue
            }
            state.markProgress()
        }
        return allSucceeded
    }

    private fun handleActivities(state: RunState): Boolean {
        var allSucceeded = true
        val subplotResponse = callSuccess(gateway::querySubplotsActivity)
            ?: return false
        val subplotData = payload(subplotResponse)
        val activities = subplotData.optJSONArray("subplotsActivityList") ?: return false
        for (index in 0 until activities.length()) {
            val activity = activities.optJSONObject(index)
            if (activity == null) {
                allSucceeded = false
                continue
            }
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
            state.handledActivities += activityKey

            if (callSuccess {
                    gateway.triggerSubplotsActivity(activityType, actionType)
                } == null
            ) {
                allSucceeded = false
                continue
            }
            state.markProgress()
            val syncTypes = if (activityType == "GIFT_BOX") {
                listOf("GIFT_BOX", "TASK_DISPLAY")
            } else {
                listOf("TOMORROW_ROD")
            }
            if (!syncAfterAction(syncTypes)) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private suspend fun completeTask(
        task: JSONObject,
        snapshot: FishPondTaskSnapshot
    ): Boolean {
        val adBizNo = snapshot.adBizNo.trim()
        if (adBizNo.isNotEmpty()) {
            if (callSuccess { gateway.fishpondAdNotice(adBizNo) } == null) {
                return false
            }
            val adConfig = FishPondPolicy.extractAdConfig(task)
            val configResponse = callSuccess {
                gateway.queryAdTaskConfig(adConfig.querySpaceCode)
            }
            requestAdExposureBestEffort(
                adConfig.exposureSpaceCode,
                adConfig.pageUrl
            )
            waitForTask(
                FishPondPolicy.adDurationMillis(configResponse, task)
            )
        } else if (snapshot.actionType.equals("VISIT", ignoreCase = true)) {
            waitForTask(FishPondPolicy.browseDurationMillis(task))
        }
        return callSuccess {
            gateway.finishTask(
                snapshot.type,
                snapshot.sceneCode,
                adBizNo.ifEmpty { null }
            )
        } != null
    }

    private fun requestAdExposureBestEffort(
        spaceCode: String,
        pageUrl: String
    ) {
        try {
            gateway.requestAdExposure(spaceCode, pageUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 抓包中的曝光请求失败后任务仍可完成，因此这里只做尽力请求。
        }
    }

    private fun verifyTaskState(
        snapshot: FishPondTaskSnapshot,
        acceptedStatuses: Set<String>
    ): Boolean {
        val response = callSuccess(gateway::listTask) ?: return false
        val taskList = payload(response).optJSONArray("taskList") ?: return false
        for (index in 0 until taskList.length()) {
            val task = taskList.optJSONObject(index) ?: continue
            val taskType = task.optString("taskId")
                .ifBlank { task.optString("taskType") }
            val sceneCode = task.optString("sceneCode")
                .ifBlank { "ANTFISHPOND_TASK" }
            if (taskType != snapshot.type || sceneCode != snapshot.sceneCode) {
                continue
            }
            return task.optString("taskStatus").uppercase() in acceptedStatuses
        }
        return false
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
            if (callSuccess { gateway.sign(signKey) } == null) {
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
        return callSuccess { gateway.fishpondSyncIndex(syncTypes) } != null
    }

    private fun refreshIndex(syncTypes: List<String>, state: RunState): JSONObject? {
        val synced = callSuccess {
            gateway.fishpondSyncIndex(syncTypes)
        } ?: return null
        if (!canExchange(synced)) {
            return synced
        }
        if (callSuccess(gateway::fishpondExchangeReward) == null) {
            return null
        }
        state.markProgress()
        return callSuccess(gateway::fishpondIndex)
    }

    private fun callSuccess(block: () -> String): JSONObject? {
        return try {
            parseSuccess(block())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
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
        private var retryNeeded = false
        val handledActivities = mutableSetOf<String>()
        val handledTaskActions = mutableSetOf<String>()

        fun markProgress() {
            progressed = true
            progressEvents++
        }

        fun markRetry() {
            retryNeeded = true
        }

        fun result(retryNeeded: Boolean): FishPondRunResult {
            return FishPondRunResult(
                confirmedFishCount,
                this.retryNeeded || retryNeeded,
                progressed
            )
        }
    }

    companion object {
        private const val MAX_CLOSURE_ROUNDS = 3
        private val BASE_SYNC_TYPES =
            listOf("GIFT_BOX", "TASK_DISPLAY", "TOMORROW_ROD")
        private val FISH_SYNC_TYPES =
            listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD")
        private val TASK_COMPLETION_SYNC_TYPES =
            listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD", "LOTTERY_PLUS")
    }
}
