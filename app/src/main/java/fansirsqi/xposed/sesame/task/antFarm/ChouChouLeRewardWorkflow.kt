package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONObject

enum class ChouChouLeRewardOutcome {
    CONFIRMED,
    RETRY
}

data class ChouChouLeRewardTask(
    val taskId: String,
    val title: String,
    val status: String,
    val innerAction: String
)

data class ChouChouLeRewardSnapshot(
    val recognized: Boolean,
    val tasks: List<ChouChouLeRewardTask>
)

data class ChouChouLeRewardExecution(
    val taskId: String,
    val title: String,
    val outcome: ChouChouLeRewardOutcome
)

data class ChouChouLeRewardResult(
    val recognized: Boolean,
    val finished: Boolean,
    val unsupportedPendingCount: Int,
    val executions: List<ChouChouLeRewardExecution>
)

object ChouChouLeRewardPolicy {

    fun parse(response: String): ChouChouLeRewardSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return unrecognized()
        if (!isSuccess(root)) {
            return unrecognized()
        }
        val taskList = root.optJSONArray("farmTaskList")
            ?: return unrecognized()
        val tasks = mutableListOf<ChouChouLeRewardTask>()
        for (index in 0 until taskList.length()) {
            val task = taskList.optJSONObject(index)
                ?: return unrecognized()
            val taskId = requiredString(task, "bizKey")
                ?: return unrecognized()
            val title = requiredString(task, "title")
                ?: return unrecognized()
            val status = requiredString(task, "taskStatus")
                ?: return unrecognized()
            tasks += ChouChouLeRewardTask(
                taskId = taskId,
                title = title,
                status = status.uppercase(),
                innerAction = task.optString("innerAction").trim()
            )
        }
        return ChouChouLeRewardSnapshot(true, tasks)
    }

    fun isActionAccepted(response: String): Boolean {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return false
        return isSuccess(root)
    }

    fun verifyReward(
        before: ChouChouLeRewardTask,
        after: ChouChouLeRewardSnapshot
    ): ChouChouLeRewardOutcome {
        if (!after.recognized) {
            return ChouChouLeRewardOutcome.RETRY
        }
        val current = after.tasks.firstOrNull {
            it.taskId == before.taskId
        } ?: return ChouChouLeRewardOutcome.CONFIRMED
        return if (
            current.status in setOf(
                "RECEIVED",
                "DONE",
                "COMPLETED"
            )
        ) {
            ChouChouLeRewardOutcome.CONFIRMED
        } else {
            ChouChouLeRewardOutcome.RETRY
        }
    }

    private fun requiredString(
        source: JSONObject,
        key: String
    ): String? {
        if (!source.has(key) || source.isNull(key)) {
            return null
        }
        return source.optString(key).trim().ifBlank { null }
    }

    private fun isSuccess(root: JSONObject): Boolean {
        if (root.has("success")) {
            return root.optBoolean("success", false)
        }
        return root.optString("resultCode").uppercase() in
            setOf("SUCCESS", "100", "200", "0")
    }

    private fun unrecognized(): ChouChouLeRewardSnapshot {
        return ChouChouLeRewardSnapshot(false, emptyList())
    }
}

class ChouChouLeRewardWorkflow(
    private val queryTasks: (String) -> String,
    private val receiveReward: (String, String) -> String
) {

    fun process(drawType: String): ChouChouLeRewardResult {
        if (drawType.isBlank()) {
            return unrecognizedResult()
        }
        var current = ChouChouLeRewardPolicy.parse(
            queryTasks(drawType)
        )
        if (!current.recognized) {
            return unrecognizedResult()
        }
        var recognized = true
        var unsupportedPendingCount = current.tasks.count {
            it.status == "TODO"
        }
        val executions = mutableListOf<ChouChouLeRewardExecution>()
        val rewardTaskIds = current.tasks.asSequence()
            .filter { it.status == "FINISHED" }
            .map(ChouChouLeRewardTask::taskId)
            .distinct()
            .toList()
        for (taskId in rewardTaskIds) {
            val task = current.tasks.firstOrNull {
                it.taskId == taskId && it.status == "FINISHED"
            } ?: continue
            val response = receiveReward(drawType, task.taskId)
            val outcome = if (
                ChouChouLeRewardPolicy.isActionAccepted(response)
            ) {
                current = ChouChouLeRewardPolicy.parse(
                    queryTasks(drawType)
                )
                recognized = recognized && current.recognized
                if (current.recognized) {
                    unsupportedPendingCount = current.tasks.count {
                        it.status == "TODO"
                    }
                }
                ChouChouLeRewardPolicy.verifyReward(task, current)
            } else {
                ChouChouLeRewardOutcome.RETRY
            }
            executions += ChouChouLeRewardExecution(
                taskId = task.taskId,
                title = task.title,
                outcome = outcome
            )
            if (!current.recognized) {
                break
            }
        }
        val hasPendingReward = current.tasks.any {
            it.status == "FINISHED"
        }
        val finished = recognized &&
            unsupportedPendingCount == 0 &&
            !hasPendingReward &&
            executions.all {
                it.outcome == ChouChouLeRewardOutcome.CONFIRMED
            }
        return ChouChouLeRewardResult(
            recognized = recognized,
            finished = finished,
            unsupportedPendingCount = unsupportedPendingCount,
            executions = executions
        )
    }

    private fun unrecognizedResult(): ChouChouLeRewardResult {
        return ChouChouLeRewardResult(
            recognized = false,
            finished = false,
            unsupportedPendingCount = 0,
            executions = emptyList()
        )
    }
}
