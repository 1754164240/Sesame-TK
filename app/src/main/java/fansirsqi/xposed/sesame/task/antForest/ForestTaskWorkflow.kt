package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONObject

enum class ForestTaskOutcome {
    CONFIRMED,
    NO_ACTION,
    RETRY
}

data class ForestTaskActionOutcome(
    val stableKey: String,
    val title: String,
    val decision: ForestTaskDecision,
    val outcome: ForestTaskOutcome,
    val message: String
)

data class ForestTaskRunResult(
    val recognized: Boolean,
    val signConfirmed: Boolean,
    val confirmed: Int,
    val skipped: Int,
    val failed: Int,
    val retryable: Boolean,
    val outcomes: List<ForestTaskActionOutcome>
)

class ForestTaskWorkflow(
    private val queryTaskSources: suspend () -> List<String>,
    private val sign: suspend (ForestSignState) -> String,
    private val completeTask: suspend (ForestTaskState) -> String,
    private val claimTask: suspend (ForestTaskState) -> String,
    private val allowTask: (ForestTaskState) -> Boolean = { true }
) {
    suspend fun run(): ForestTaskRunResult {
        val initial = querySnapshot()
            ?: return emptyResult(recognized = false, retryable = true)
        val outcomes = mutableListOf<ForestTaskActionOutcome>()

        for (signState in initial.signs.filter { !it.signed }) {
            outcomes += processSign(signState)
        }
        for (task in initial.tasks) {
            outcomes += processTask(task)
        }

        val incompleteSnapshot = !initial.complete
        val confirmedSignKeys = outcomes
            .filter {
                it.decision == ForestTaskDecision.SIGN &&
                    it.outcome == ForestTaskOutcome.CONFIRMED
            }
            .mapTo(mutableSetOf()) { it.stableKey }
        return ForestTaskRunResult(
            recognized = true,
            signConfirmed = initial.signs.isNotEmpty() &&
                initial.signs.all {
                    it.signed || it.stableKey in confirmedSignKeys
                },
            confirmed = outcomes.count {
                it.outcome == ForestTaskOutcome.CONFIRMED
            },
            skipped = outcomes.count {
                it.outcome == ForestTaskOutcome.NO_ACTION
            },
            failed = outcomes.count {
                it.outcome == ForestTaskOutcome.RETRY
            } + if (incompleteSnapshot) 1 else 0,
            retryable = incompleteSnapshot || outcomes.any {
                it.outcome == ForestTaskOutcome.RETRY
            },
            outcomes = outcomes
        )
    }

    private suspend fun processSign(
        signState: ForestSignState
    ): ForestTaskActionOutcome {
        val decision = ForestTaskDecision.SIGN
        val response = runCatching { sign(signState) }.getOrNull()
        if (!isActionSuccess(response)) {
            return retry(
                signState.stableKey,
                "森林签到",
                decision,
                "签到请求失败"
            )
        }
        val refreshed = querySnapshot()
            ?: return retry(
                signState.stableKey,
                "森林签到",
                decision,
                "签到后查询失败"
            )
        val current = refreshed.signs.firstOrNull {
            it.stableKey == signState.stableKey
        }
        return if (current?.signed == true) {
            confirmed(
                signState.stableKey,
                "森林签到",
                decision,
                "签到状态已确认"
            )
        } else {
            retry(
                signState.stableKey,
                "森林签到",
                decision,
                "签到后状态未刷新"
            )
        }
    }

    private suspend fun processTask(
        task: ForestTaskState
    ): ForestTaskActionOutcome {
        if (!allowTask(task)) {
            return noAction(
                task,
                ForestTaskPolicy.decide(task),
                "任务已被配置或黑名单禁用"
            )
        }
        val decision = ForestTaskPolicy.decide(task)
        return when (decision) {
            ForestTaskDecision.COMPLETE_SAFE -> {
                performTaskAction(
                    task = task,
                    decision = decision,
                    action = completeTask,
                    actionName = "完成任务"
                )
            }
            ForestTaskDecision.CLAIM -> {
                performTaskAction(
                    task = task,
                    decision = decision,
                    action = claimTask,
                    actionName = "领取奖励"
                )
            }
            ForestTaskDecision.RETRY -> {
                retry(
                    task.stableKey,
                    task.title,
                    decision,
                    "任务状态未知"
                )
            }
            ForestTaskDecision.SIGN -> {
                retry(
                    task.stableKey,
                    task.title,
                    decision,
                    "签到任务分类错误"
                )
            }
            ForestTaskDecision.TERMINAL -> {
                noAction(
                    task,
                    decision,
                    "任务已终态"
                )
            }
            ForestTaskDecision.SKIP_UNSAFE -> {
                noAction(
                    task,
                    decision,
                    "危险或未知任务已跳过"
                )
            }
        }
    }

    private suspend fun performTaskAction(
        task: ForestTaskState,
        decision: ForestTaskDecision,
        action: suspend (ForestTaskState) -> String,
        actionName: String
    ): ForestTaskActionOutcome {
        val response = runCatching { action(task) }.getOrNull()
        if (!isActionSuccess(response)) {
            return retry(
                task.stableKey,
                task.title,
                decision,
                "${actionName}请求失败"
            )
        }
        val refreshed = querySnapshot()
            ?: return retry(
                task.stableKey,
                task.title,
                decision,
                "${actionName}后查询失败"
            )
        val current = refreshed.tasks.firstOrNull {
            it.stableKey == task.stableKey
        }
        val confirmed = ForestTaskPolicy.isCompletionConfirmed(task, current) ||
            (current == null && refreshed.complete)
        return if (confirmed) {
            confirmed(
                task.stableKey,
                task.title,
                decision,
                "${actionName}状态已确认"
            )
        } else {
            retry(
                task.stableKey,
                task.title,
                decision,
                "${actionName}后状态未刷新"
            )
        }
    }

    private suspend fun querySnapshot(): ForestTaskSnapshot? {
        val responses = runCatching { queryTaskSources() }.getOrNull()
            ?: return null
        val snapshot = ForestTaskPolicy.mergeSnapshots(responses)
        return snapshot.takeIf { it.recognized }
    }

    private fun isActionSuccess(response: String?): Boolean {
        val root = response?.let {
            runCatching { JSONObject(it) }.getOrNull()
        } ?: return false
        return ForestTaskPolicy.isRpcSuccess(root)
    }

    private fun confirmed(
        stableKey: String,
        title: String,
        decision: ForestTaskDecision,
        message: String
    ): ForestTaskActionOutcome {
        return ForestTaskActionOutcome(
            stableKey = stableKey,
            title = title,
            decision = decision,
            outcome = ForestTaskOutcome.CONFIRMED,
            message = message
        )
    }

    private fun retry(
        stableKey: String,
        title: String,
        decision: ForestTaskDecision,
        message: String
    ): ForestTaskActionOutcome {
        return ForestTaskActionOutcome(
            stableKey = stableKey,
            title = title,
            decision = decision,
            outcome = ForestTaskOutcome.RETRY,
            message = message
        )
    }

    private fun noAction(
        task: ForestTaskState,
        decision: ForestTaskDecision,
        message: String
    ): ForestTaskActionOutcome {
        return ForestTaskActionOutcome(
            stableKey = task.stableKey,
            title = task.title,
            decision = decision,
            outcome = ForestTaskOutcome.NO_ACTION,
            message = message
        )
    }

    private fun emptyResult(
        recognized: Boolean,
        retryable: Boolean
    ): ForestTaskRunResult {
        return ForestTaskRunResult(
            recognized = recognized,
            signConfirmed = false,
            confirmed = 0,
            skipped = 0,
            failed = if (retryable) 1 else 0,
            retryable = retryable,
            outcomes = emptyList()
        )
    }
}
