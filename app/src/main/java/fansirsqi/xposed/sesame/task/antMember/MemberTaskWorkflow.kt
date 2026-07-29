package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import kotlin.math.max

data class MemberTaskOutcome(
    val stableKey: String,
    val title: String,
    val decision: MemberTaskDecision,
    val verification: MemberTaskVerification?,
    val retryable: Boolean,
    val message: String
)

data class MemberTaskRunResult(
    val recognized: Boolean,
    val confirmed: Int,
    val partial: Int,
    val failed: Int,
    val skipped: Int,
    val retryable: Boolean,
    val outcomes: List<MemberTaskOutcome>
)

class MemberTaskWorkflow(
    private val queryTaskSources: suspend () -> List<String>,
    private val applyTask: suspend (MemberTaskState) -> String,
    private val executeTask: suspend (MemberTaskState) -> String,
    private val finishAdTask: suspend (MemberTaskState) -> String = { "" },
    private val queryTaskDetail: suspend (MemberTaskState) -> String,
    private val pauseBeforeCompletion: suspend (Long) -> Unit,
    private val isTaskBlocked: (MemberTaskState) -> Boolean = { false }
) {

    suspend fun run(maxActionTasks: Int = Int.MAX_VALUE): MemberTaskRunResult {
        val snapshot = queryInitialSnapshot()
            ?: return emptyResult(recognized = false, retryable = true)
        val outcomes = mutableListOf<MemberTaskOutcome>()
        var actionTaskCount = 0
        val actionLimit = max(0, maxActionTasks)
        for (task in snapshot.tasks) {
            if (MemberTaskProtocol.isTerminalStatus(task.status)) {
                outcomes += skipped(
                    task,
                    MemberTaskDecision.CLAIM_ONLY,
                    "服务端任务已终态"
                )
                continue
            }
            if (isTaskBlocked(task)) {
                outcomes += skipped(
                    task,
                    MemberTaskDecision.SKIP_UNSUPPORTED,
                    "用户黑名单已阻止"
                )
                continue
            }
            val decision = MemberTaskSafetyPolicy.classify(task.toSafetyCandidate())
            val requiresAction = decision == MemberTaskDecision.EXECUTE_BROWSE ||
                decision == MemberTaskDecision.FINISH_AD
            if (requiresAction && actionTaskCount >= actionLimit) {
                continue
            }
            if (requiresAction) {
                actionTaskCount++
            }
            outcomes += processTask(task, decision)
        }
        return MemberTaskRunResult(
            recognized = true,
            confirmed = outcomes.count {
                it.verification == MemberTaskVerification.CONFIRMED
            },
            partial = outcomes.count {
                it.verification == MemberTaskVerification.PARTIAL
            },
            failed = outcomes.count { it.retryable },
            skipped = outcomes.count {
                !it.retryable && it.verification == null
            },
            retryable = outcomes.any { it.retryable },
            outcomes = outcomes
        )
    }

    private suspend fun processTask(
        task: MemberTaskState,
        decision: MemberTaskDecision
    ): MemberTaskOutcome {
        if (decision == MemberTaskDecision.VERIFY_ONLY) {
            return verifyDetail(task, decision)
        }
        if (
            decision != MemberTaskDecision.EXECUTE_BROWSE &&
            decision != MemberTaskDecision.FINISH_AD
        ) {
            return skipped(task, decision)
        }

        var activeTask = task
        if (requiresApply(task)) {
            val applyResponse = runCatching { applyTask(task) }.getOrNull()
            if (!isActionSuccess(applyResponse)) {
                return retryable(task, decision, "任务报名请求失败")
            }
            val detailResponse = runCatching { queryTaskDetail(task) }.getOrNull()
            val detailObject = detailResponse?.let(::parseObject)
                ?: return retryable(task, decision, "报名后详情查询失败")
            val verification = MemberTaskProtocol.verifyTaskDetail(task, detailObject)
            if (verification != MemberTaskVerification.UNCONFIRMED) {
                return outcomeForVerification(task, decision, verification)
            }
            val refreshed = MemberTaskProtocol.parseTaskDetail(detailObject)
                ?: return retryable(task, decision, "报名后未查询到目标任务")
            if (!MemberTaskProtocol.isApplyConfirmed(task, refreshed)) {
                return retryable(task, decision, "报名后状态未推进")
            }
            activeTask = refreshed
        }

        pauseBeforeCompletion(taskWaitMillis(activeTask))
        val actionResponse = runCatching {
            if (decision == MemberTaskDecision.EXECUTE_BROWSE) {
                executeTask(activeTask)
            } else {
                finishAdTask(activeTask)
            }
        }.getOrNull()
        if (!isActionSuccess(actionResponse)) {
            return retryable(activeTask, decision, "任务执行请求失败")
        }
        return verifyDetail(activeTask, decision)
    }

    private suspend fun verifyDetail(
        task: MemberTaskState,
        decision: MemberTaskDecision
    ): MemberTaskOutcome {
        val detailResponse = runCatching { queryTaskDetail(task) }.getOrNull()
        val verification = detailResponse
            ?.let(::parseObject)
            ?.let { MemberTaskProtocol.verifyTaskDetail(task, it) }
            ?: MemberTaskVerification.UNCONFIRMED
        return outcomeForVerification(task, decision, verification)
    }

    private suspend fun queryInitialSnapshot(): MemberTaskSnapshot? {
        val responses = runCatching { queryTaskSources() }.getOrNull()
            ?: return null
        if (responses.isEmpty()) {
            return null
        }
        val parsed = responses.map { parseObject(it) ?: return null }
        if (parsed.any { !MemberTaskProtocol.parseTaskSnapshot(it).recognized }) {
            return null
        }
        return MemberTaskProtocol.parseTaskSnapshot(*parsed.toTypedArray())
    }

    private fun outcomeForVerification(
        task: MemberTaskState,
        decision: MemberTaskDecision,
        verification: MemberTaskVerification
    ): MemberTaskOutcome {
        val retryable = verification == MemberTaskVerification.UNCONFIRMED
        val message = when (verification) {
            MemberTaskVerification.CONFIRMED -> "服务端终态已确认"
            MemberTaskVerification.PARTIAL -> "服务端计数已推进"
            MemberTaskVerification.UNCONFIRMED -> "动作后状态未确认"
        }
        return MemberTaskOutcome(
            stableKey = task.stableKey,
            title = task.title,
            decision = decision,
            verification = verification,
            retryable = retryable,
            message = message
        )
    }

    private fun retryable(
        task: MemberTaskState,
        decision: MemberTaskDecision,
        message: String
    ): MemberTaskOutcome {
        return MemberTaskOutcome(
            stableKey = task.stableKey,
            title = task.title,
            decision = decision,
            verification = MemberTaskVerification.UNCONFIRMED,
            retryable = true,
            message = message
        )
    }

    private fun skipped(
        task: MemberTaskState,
        decision: MemberTaskDecision,
        message: String = "任务类型不执行"
    ): MemberTaskOutcome {
        return MemberTaskOutcome(
            stableKey = task.stableKey,
            title = task.title,
            decision = decision,
            verification = null,
            retryable = false,
            message = message
        )
    }

    private fun emptyResult(
        recognized: Boolean,
        retryable: Boolean
    ): MemberTaskRunResult {
        return MemberTaskRunResult(
            recognized = recognized,
            confirmed = 0,
            partial = 0,
            failed = if (retryable) 1 else 0,
            skipped = 0,
            retryable = retryable,
            outcomes = emptyList()
        )
    }

    private fun taskWaitMillis(task: MemberTaskState): Long {
        val config = task.source.optJSONObject("simpleTaskConfig")
            ?: task.source.optJSONObject("taskConfig")
        val browseSeconds = config?.optInt("browseSeconds") ?: 0
        return (max(browseSeconds, 15) + 1) * 1_000L
    }

    private fun requiresApply(task: MemberTaskState): Boolean {
        return task.status.uppercase() in
            setOf("", "INIT", "TO_APPLY", "NOT_STARTED")
    }

    private fun isActionSuccess(response: String?): Boolean {
        val value = response?.let(::parseObject) ?: return false
        return MemberTaskProtocol.isFinishSuccess(value)
    }

    private fun parseObject(value: String): JSONObject? {
        return runCatching { JSONObject(value) }.getOrNull()
    }
}
