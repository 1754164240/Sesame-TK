package fansirsqi.xposed.sesame.task.antOrchard

class OrchardBrowseTaskWorkflow(
    private val listTasks: suspend () -> String,
    private val startBrowse: suspend (String) -> String,
    private val finishTask:
        suspend (AntOrchardTaskState, String) -> String,
    private val waitForBrowse: suspend () -> Unit
) {

    suspend fun process(
        task: AntOrchardTaskState
    ): AntOrchardRewardOutcome {
        val selection = OrchardBrowseTaskPolicy.select(task)
            ?: return AntOrchardRewardOutcome.SKIPPED_UNSAFE
        val startResponse = startBrowse(selection.source)
        if (!AntOrchardRewardPolicy.isActionAccepted(startResponse)) {
            return AntOrchardRewardOutcome.RETRY
        }
        when (
            OrchardBrowseTaskPolicy.completionState(
                listTasks(),
                selection.taskId
            )
        ) {
            OrchardBrowseCompletionState.CONFIRMED ->
                return AntOrchardRewardOutcome.CONFIRMED
            OrchardBrowseCompletionState.UNKNOWN ->
                return AntOrchardRewardOutcome.RETRY
            OrchardBrowseCompletionState.PENDING -> Unit
        }

        waitForBrowse()
        val finishResponse = finishTask(task, selection.source)
        if (!AntOrchardRewardPolicy.isActionAccepted(finishResponse)) {
            return AntOrchardRewardOutcome.RETRY
        }
        return if (
            OrchardBrowseTaskPolicy.isCompletionConfirmed(
                listTasks(),
                selection.taskId
            )
        ) {
            AntOrchardRewardOutcome.CONFIRMED
        } else {
            AntOrchardRewardOutcome.RETRY
        }
    }
}
