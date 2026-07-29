package fansirsqi.xposed.sesame.task.antSports

import org.json.JSONObject

enum class SportsTaskAction {
    COMPLETE_SIGN_IN,
    CLAIM_REWARD,
    SKIP_UNSAFE,
    NONE
}

enum class SportsTaskOutcome {
    CONFIRMED,
    RETRY,
    NO_ACTION,
    SKIPPED_UNSAFE
}

data class SportsTaskState(
    val taskId: String,
    val userTaskId: String,
    val bizType: String,
    val taskName: String,
    val status: String
)

data class SportsTaskSnapshot(
    val recognized: Boolean,
    val tasks: List<SportsTaskState>
)

object SportsTaskPolicy {
    private const val SIGN_GROUP = "SPORTS_DAILY_SIGN_GROUP"

    private val riskyAsciiSignals = listOf(
        "AD",
        "GAME",
        "ORDER",
        "PURCHASE",
        "RECHARGE",
        "LOAN",
        "INVEST",
        "WITHDRAW",
        "CASH"
    )
    private val riskyTextSignals = listOf(
        "广告",
        "游戏",
        "下单",
        "购买",
        "充值",
        "借贷",
        "投资",
        "提现",
        "现金"
    )
    private val signAsciiSignals = listOf(
        "SIGN",
        "SIGN_IN",
        "CHECK_IN",
        "CHECKIN"
    )
    private val completeStates = setOf(
        "COMPLETED",
        "FINISHED",
        "DONE",
        "RECEIVED",
        "CLAIMED"
    )
    private val receivedStates = setOf(
        "FINISHED",
        "DONE",
        "RECEIVED",
        "CLAIMED"
    )

    fun parseSnapshot(response: String): SportsTaskSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return unrecognized()
        if (!isSuccess(root)) {
            return unrecognized()
        }
        val group = root.optJSONObject("group")
            ?: return unrecognized()
        val taskList = group.optJSONArray("userTaskList")
            ?: return unrecognized()
        val tasks = mutableListOf<SportsTaskState>()
        for (index in 0 until taskList.length()) {
            val task = taskList.optJSONObject(index)
                ?: return unrecognized()
            val status = requiredString(task, "status")
                ?: return unrecognized()
            val taskInfo = task.optJSONObject("taskInfo")
                ?: return unrecognized()
            val taskId = requiredString(taskInfo, "taskId")
                ?: return unrecognized()
            tasks += SportsTaskState(
                taskId = taskId,
                userTaskId = task.optString("userTaskId").trim(),
                bizType = taskInfo.optString("bizType").trim(),
                taskName = taskInfo.optString("taskName")
                    .trim()
                    .ifBlank { taskId },
                status = status.uppercase()
            )
        }
        return SportsTaskSnapshot(true, tasks)
    }

    fun decide(
        groupId: String,
        task: SportsTaskState
    ): SportsTaskAction {
        if (containsRisk(task)) {
            return SportsTaskAction.SKIP_UNSAFE
        }
        return when (task.status.uppercase()) {
            "TODO" -> {
                if (
                    groupId == SIGN_GROUP &&
                    task.bizType.isNotBlank() &&
                    containsSignSignal(task)
                ) {
                    SportsTaskAction.COMPLETE_SIGN_IN
                } else {
                    SportsTaskAction.SKIP_UNSAFE
                }
            }
            "COMPLETED" -> {
                if (task.userTaskId.isNotBlank()) {
                    SportsTaskAction.CLAIM_REWARD
                } else {
                    SportsTaskAction.SKIP_UNSAFE
                }
            }
            else -> SportsTaskAction.NONE
        }
    }

    fun isActionAccepted(response: String): Boolean {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return false
        return isSuccess(root)
    }

    fun verifyCompletion(
        before: SportsTaskState,
        after: SportsTaskSnapshot
    ): SportsTaskOutcome {
        if (!after.recognized) {
            return SportsTaskOutcome.RETRY
        }
        val current = after.tasks.firstOrNull {
            it.taskId == before.taskId
        } ?: return SportsTaskOutcome.CONFIRMED
        return if (current.status.uppercase() in completeStates) {
            SportsTaskOutcome.CONFIRMED
        } else {
            SportsTaskOutcome.RETRY
        }
    }

    fun verifyReward(
        before: SportsTaskState,
        after: SportsTaskSnapshot
    ): SportsTaskOutcome {
        if (!after.recognized) {
            return SportsTaskOutcome.RETRY
        }
        val current = after.tasks.firstOrNull {
            it.taskId == before.taskId &&
                (
                    before.userTaskId.isBlank() ||
                        it.userTaskId == before.userTaskId
                    )
        } ?: return SportsTaskOutcome.CONFIRMED
        return if (current.status.uppercase() in receivedStates) {
            SportsTaskOutcome.CONFIRMED
        } else {
            SportsTaskOutcome.RETRY
        }
    }

    private fun containsRisk(task: SportsTaskState): Boolean {
        val text = "${task.taskId} ${task.bizType} ${task.taskName}"
        val upper = text.uppercase()
        return riskyTextSignals.any(text::contains) ||
            riskyAsciiSignals.any { hasAsciiSignal(upper, it) }
    }

    private fun containsSignSignal(task: SportsTaskState): Boolean {
        val text = "${task.taskId} ${task.bizType} ${task.taskName}"
        val upper = text.uppercase()
        return text.contains("签到") ||
            signAsciiSignals.any { hasAsciiSignal(upper, it) }
    }

    private fun hasAsciiSignal(text: String, signal: String): Boolean {
        return Regex(
            "(^|[^A-Z0-9])${Regex.escape(signal)}([^A-Z0-9]|$)"
        ).containsMatchIn(text)
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

    private fun unrecognized(): SportsTaskSnapshot {
        return SportsTaskSnapshot(false, emptyList())
    }
}
