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
    val innerAction: String,
    val rightsTimes: Int,
    val rightsTimesLimit: Int,
    val receivedAwardCount: Int,
    val awardCount: Int
) {
    fun hasRemainingTimes(): Boolean {
        return rightsTimes < rightsTimesLimit
    }
}

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
                innerAction = task.optString("innerAction").trim(),
                rightsTimes = task.optInt("rightsTimes", 0),
                rightsTimesLimit = task.optInt("rightsTimesLimit", 0),
                receivedAwardCount = task.optInt(
                    "alreadyReceiveStageAwardCount",
                    0
                ),
                awardCount = task.optInt("awardCount", 0)
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
        } else if (
            current.receivedAwardCount > before.receivedAwardCount
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
    private val executeTask: (
        String,
        ChouChouLeRewardTask
    ) -> Boolean = { _, _ -> false },
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
        val executions = mutableListOf<ChouChouLeRewardExecution>()
        val failedTaskIds = mutableSetOf<String>()
        val failedRewardIds = mutableSetOf<String>()
        var steps = 0
        while (recognized && steps < 100) {
            val pendingTask = current.tasks.firstOrNull {
                isExecutablePendingTask(it) &&
                    it.taskId !in failedTaskIds
            }
            if (pendingTask != null) {
                steps++
                if (!executeTask(drawType, pendingTask)) {
                    failedTaskIds += pendingTask.taskId
                    continue
                }
                val next = ChouChouLeRewardPolicy.parse(
                    queryTasks(drawType)
                )
                recognized = recognized && next.recognized
                if (!next.recognized) {
                    current = next
                    break
                }
                if (!hasTaskProgress(pendingTask, next)) {
                    failedTaskIds += pendingTask.taskId
                }
                current = next
                continue
            }

            val rewardTask = current.tasks.firstOrNull {
                it.status == "FINISHED" &&
                    it.taskId !in failedRewardIds
            } ?: break
            steps++
            val response = receiveReward(
                drawType,
                rewardTask.taskId
            )
            val outcome = if (
                ChouChouLeRewardPolicy.isActionAccepted(response)
            ) {
                val next = ChouChouLeRewardPolicy.parse(
                    queryTasks(drawType)
                )
                recognized = recognized && next.recognized
                current = next
                ChouChouLeRewardPolicy.verifyReward(
                    rewardTask,
                    next
                )
            } else {
                ChouChouLeRewardOutcome.RETRY
            }
            executions += ChouChouLeRewardExecution(
                taskId = rewardTask.taskId,
                title = rewardTask.title,
                outcome = outcome
            )
            if (outcome == ChouChouLeRewardOutcome.RETRY) {
                failedRewardIds += rewardTask.taskId
            }
        }
        val hasPendingReward = current.tasks.any {
            it.status == "FINISHED"
        }
        val unsupportedPendingCount = current.tasks.count {
            isExecutablePendingTask(it)
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

    private fun hasTaskProgress(
        before: ChouChouLeRewardTask,
        after: ChouChouLeRewardSnapshot
    ): Boolean {
        val current = after.tasks.firstOrNull {
            it.taskId == before.taskId
        } ?: return true
        return current.status != before.status ||
            current.rightsTimes > before.rightsTimes ||
            current.awardCount > before.awardCount ||
            current.receivedAwardCount > before.receivedAwardCount
    }

    private fun isExecutablePendingTask(
        task: ChouChouLeRewardTask
    ): Boolean {
        return task.status == "TODO" &&
            task.innerAction.uppercase() != "DONATION" &&
            task.hasRemainingTimes()
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
