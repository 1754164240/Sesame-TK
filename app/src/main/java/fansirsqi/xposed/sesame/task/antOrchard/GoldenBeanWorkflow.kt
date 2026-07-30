package fansirsqi.xposed.sesame.task.antOrchard

import org.json.JSONObject

data class GoldenBeanRunResult(
    val progressed: Boolean,
    val retryNeeded: Boolean,
    val claimedCount: Int
)

class GoldenBeanWorkflow(
    private val gateway: GoldenBeanGateway
) {
    fun run(): GoldenBeanRunResult {
        val index = callSuccess(gateway::index)
            ?: return GoldenBeanRunResult(
                progressed = false,
                retryNeeded = true,
                claimedCount = 0
            )
        val initialTasks = parseTasks(index)
            ?: return GoldenBeanRunResult(
                progressed = false,
                retryNeeded = true,
                claimedCount = 0
            )

        val state = RunState(initialTasks)
        for (round in 0 until MAX_ROUNDS) {
            var attemptedAction = false
            val roundTasks = state.latestTasks
            for (task in roundTasks) {
                val decision = GoldenBeanPolicy.decide(task)
                if (decision == GoldenBeanTaskDecision.WAIT ||
                    decision == GoldenBeanTaskDecision.SKIP
                ) {
                    continue
                }
                val actionKey =
                    "${task.sceneCode}|${task.type}|${task.status}|$decision"
                if (!state.handledActions.add(actionKey)) {
                    continue
                }
                attemptedAction = true
                try {
                    when (decision) {
                        GoldenBeanTaskDecision.FORTUNE_DRAW ->
                            handleFortuneDraw(task, state)

                        GoldenBeanTaskDecision.COMPLETE ->
                            handleCompletion(task, state)

                        GoldenBeanTaskDecision.CLAIM ->
                            handleClaim(task, state)

                        GoldenBeanTaskDecision.WAIT,
                        GoldenBeanTaskDecision.SKIP -> Unit
                    }
                } catch (_: Exception) {
                    state.retryNeeded = true
                }
            }
            if (!attemptedAction) {
                break
            }
        }
        return state.result()
    }

    private fun handleFortuneDraw(
        task: GoldenBeanTaskSnapshot,
        state: RunState
    ) {
        if (callSuccess(gateway::fortuneDraw) == null) {
            state.retryNeeded = true
            return
        }
        val refreshed = syncTask(task.type, state)
        if (refreshed?.status.equals("RECEIVED", ignoreCase = true)) {
            state.progressed = true
        } else {
            state.retryNeeded = true
        }
    }

    private fun handleCompletion(
        task: GoldenBeanTaskSnapshot,
        state: RunState
    ) {
        if (callSuccess {
                gateway.finishTask(task.type, task.sceneCode)
            } == null
        ) {
            state.retryNeeded = true
            return
        }
        val refreshed = syncTask(task.type, state)
        when {
            refreshed == null -> state.retryNeeded = true
            refreshed.status.equals("RECEIVED", ignoreCase = true) ->
                state.progressed = true
            refreshed.status.equals("FINISHED", ignoreCase = true) ||
                refreshed.status.equals("TO_RECEIVE", ignoreCase = true) ->
                handleClaim(refreshed, state)
            else -> state.retryNeeded = true
        }
    }

    private fun handleClaim(
        task: GoldenBeanTaskSnapshot,
        state: RunState
    ) {
        if (callSuccess {
                gateway.receiveTaskAward(task.type, task.sceneCode)
            } == null
        ) {
            state.retryNeeded = true
            return
        }
        val refreshed = syncTask(task.type, state)
        if (refreshed?.status.equals("RECEIVED", ignoreCase = true) ||
            refreshed?.status.equals("DONE", ignoreCase = true)
        ) {
            state.progressed = true
            state.claimedCount++
        } else {
            state.retryNeeded = true
        }
    }

    private fun syncTask(
        taskType: String,
        state: RunState
    ): GoldenBeanTaskSnapshot? {
        val response = callSuccess {
            gateway.sync(SYNC_TYPES)
        } ?: return null
        val tasks = parseTasks(response) ?: return null
        state.latestTasks = tasks
        return tasks.firstOrNull { it.type == taskType }
    }

    private fun parseTasks(response: JSONObject): List<GoldenBeanTaskSnapshot>? {
        val payload = response.optJSONObject("data") ?: response
        val taskList = payload.optJSONArray("taskList") ?: return null
        return buildList {
            for (index in 0 until taskList.length()) {
                val task = taskList.optJSONObject(index) ?: continue
                val type = task.optString("taskId")
                    .ifBlank { task.optString("taskType") }
                val sceneCode = task.optString("sceneCode")
                    .ifBlank { DEFAULT_SCENE_CODE }
                val title = task.optJSONObject("taskDisplayConfig")
                    ?.optString("title")
                    ?.takeIf(String::isNotBlank)
                    ?: task.optString("taskTitle")
                        .ifBlank { task.optString("title") }
                add(
                    GoldenBeanTaskSnapshot(
                        type = type,
                        sceneCode = sceneCode,
                        status = task.optString("taskStatus"),
                        actionType = task.optString("actionType"),
                        title = title
                    )
                )
            }
        }
    }

    private fun callSuccess(block: () -> String): JSONObject? {
        return try {
            val raw = block()
            if (raw.isBlank()) {
                null
            } else {
                JSONObject(raw).takeIf(GoldenBeanPolicy::isRpcSuccess)
            }
        } catch (_: Exception) {
            null
        }
    }

    private class RunState(
        initialTasks: List<GoldenBeanTaskSnapshot>
    ) {
        var progressed = false
        var retryNeeded = false
        var claimedCount = 0
        var latestTasks = initialTasks
        val handledActions = mutableSetOf<String>()

        fun result() = GoldenBeanRunResult(
            progressed = progressed,
            retryNeeded = retryNeeded,
            claimedCount = claimedCount
        )
    }

    companion object {
        private const val DEFAULT_SCENE_CODE = "GOLDEN_BEAN_MASTER_TASK"
        private const val MAX_ROUNDS = 3
        private val SYNC_TYPES =
            listOf("JAR_INFO", "TASK_LIST", "FARM_TASK", "SIGN")
    }
}
