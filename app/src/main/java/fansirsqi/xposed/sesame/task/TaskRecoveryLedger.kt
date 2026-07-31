package fansirsqi.xposed.sesame.task

import java.util.concurrent.atomic.AtomicLong

enum class RecoverableTaskOutcome {
    COMPLETED,
    FAILED,
    BLOCKED_VERIFICATION,
    NOT_STARTED
}

class TaskRecoveryLedger {
    private data class RunState(
        val outcomes: LinkedHashMap<String, RecoverableTaskOutcome>,
        val claimedRecoveries: MutableSet<Long> = linkedSetOf()
    )

    private val runs = linkedMapOf<String, RunState>()

    @Synchronized
    fun startRun(runId: String, taskIds: List<String>) {
        runs[runId] = RunState(
            LinkedHashMap<String, RecoverableTaskOutcome>().apply {
                taskIds.forEach { put(it, RecoverableTaskOutcome.NOT_STARTED) }
            }
        )
    }

    @Synchronized
    fun record(taskId: String, outcome: RecoverableTaskOutcome) {
        val outcomes = runs.values.lastOrNull()?.outcomes ?: return
        if (outcomes[taskId] != RecoverableTaskOutcome.COMPLETED) {
            outcomes[taskId] = outcome
        }
    }

    @Synchronized
    fun recoveryTaskIds(runId: String): List<String> {
        return runs[runId]?.outcomes
            ?.filterValues {
                it == RecoverableTaskOutcome.BLOCKED_VERIFICATION ||
                    it == RecoverableTaskOutcome.NOT_STARTED
            }
            ?.keys
            ?.toList()
            .orEmpty()
    }

    @Synchronized
    fun claimRecovery(runId: String, generation: Long): List<String> {
        val run = runs[runId] ?: return emptyList()
        if (!run.claimedRecoveries.add(generation)) {
            return emptyList()
        }
        return recoveryTaskIds(runId)
    }
}

object TaskRecoveryRegistry {
    private val runSequence = AtomicLong(0L)
    private val ledger = TaskRecoveryLedger()

    @Volatile
    private var currentRunId: String? = null

    @Volatile
    private var recoverySelection: Set<String>? = null

    @Synchronized
    fun beginRun(taskIds: List<String>): String {
        val runId = "run-${System.currentTimeMillis()}-${runSequence.incrementAndGet()}"
        ledger.startRun(runId, taskIds)
        currentRunId = runId
        return runId
    }

    fun record(taskId: String, outcome: RecoverableTaskOutcome) {
        ledger.record(taskId, outcome)
    }

    @Synchronized
    fun prepareRecovery(generation: Long): List<String> {
        val runId = currentRunId ?: return emptyList()
        val selected = ledger.claimRecovery(runId, generation)
        recoverySelection = selected.toSet()
        return selected
    }

    @Synchronized
    fun consumeRecoverySelection(): Set<String>? {
        val selected = recoverySelection
        recoverySelection = null
        return selected
    }

    fun stableTaskId(task: ModelTask): String = task.javaClass.name
}
