package fansirsqi.xposed.sesame.task.antSports

data class SportsTaskExecution(
    val taskId: String,
    val taskName: String,
    val action: SportsTaskAction,
    val outcome: SportsTaskOutcome
)

data class SportsTaskBatchResult(
    val recognized: Boolean,
    val executions: List<SportsTaskExecution>
)

class SportsTaskWorkflow(
    private val queryGroup: (String) -> String,
    private val completeTask: (String, String) -> String,
    private val receiveReward: (String, String) -> String
) {

    fun process(groupId: String): SportsTaskBatchResult {
        if (groupId.isBlank()) {
            return SportsTaskBatchResult(false, emptyList())
        }
        var current = SportsTaskPolicy.parseSnapshot(
            queryGroup(groupId)
        )
        if (!current.recognized) {
            return SportsTaskBatchResult(false, emptyList())
        }
        var allSnapshotsRecognized = true
        val executions = mutableListOf<SportsTaskExecution>()
        val taskIds = current.tasks
            .map(SportsTaskState::taskId)
            .distinct()
        for (taskId in taskIds) {
            val task = current.tasks.firstOrNull {
                it.taskId == taskId
            } ?: continue
            val action = SportsTaskPolicy.decide(groupId, task)
            val outcome = when (action) {
                SportsTaskAction.COMPLETE_SIGN_IN -> {
                    val response = completeTask(
                        task.bizType,
                        task.taskId
                    )
                    if (!SportsTaskPolicy.isActionAccepted(response)) {
                        SportsTaskOutcome.RETRY
                    } else {
                        current = SportsTaskPolicy.parseSnapshot(
                            queryGroup(groupId)
                        )
                        allSnapshotsRecognized =
                            allSnapshotsRecognized && current.recognized
                        SportsTaskPolicy.verifyCompletion(task, current)
                    }
                }
                SportsTaskAction.CLAIM_REWARD -> {
                    val response = receiveReward(
                        task.taskId,
                        task.userTaskId
                    )
                    if (!SportsTaskPolicy.isActionAccepted(response)) {
                        SportsTaskOutcome.RETRY
                    } else {
                        current = SportsTaskPolicy.parseSnapshot(
                            queryGroup(groupId)
                        )
                        allSnapshotsRecognized =
                            allSnapshotsRecognized && current.recognized
                        SportsTaskPolicy.verifyReward(task, current)
                    }
                }
                SportsTaskAction.SKIP_UNSAFE ->
                    SportsTaskOutcome.SKIPPED_UNSAFE
                SportsTaskAction.NONE ->
                    SportsTaskOutcome.NO_ACTION
            }
            executions += SportsTaskExecution(
                taskId = task.taskId,
                taskName = task.taskName,
                action = action,
                outcome = outcome
            )
            if (!current.recognized) {
                break
            }
        }
        return SportsTaskBatchResult(
            recognized = allSnapshotsRecognized,
            executions = executions
        )
    }
}
