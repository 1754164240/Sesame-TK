package fansirsqi.xposed.sesame.task.antOrchard

enum class AntOrchardRewardOutcome {
    CONFIRMED,
    RETRY,
    SKIPPED_UNSAFE,
    TERMINAL
}

class AntOrchardRewardWorkflow(
    private val listTasks: suspend () -> String,
    private val finishTask: suspend (AntOrchardTaskState) -> String,
    private val claimTask: suspend (AntOrchardTaskState) -> String,
    private val queryLeyuanTasks: suspend () -> String,
    private val claimLeyuanTask: suspend (AntOrchardLeyuanTask) -> String
) {

    suspend fun claimLeyuanReward(
        task: AntOrchardLeyuanTask
    ): AntOrchardRewardOutcome {
        if (!AntOrchardRewardPolicy.isLeyuanClaimable(task)) {
            return AntOrchardRewardOutcome.RETRY
        }
        val actionResponse = claimLeyuanTask(task)
        if (!AntOrchardRewardPolicy.isActionAccepted(actionResponse)) {
            return AntOrchardRewardOutcome.RETRY
        }
        return if (
            AntOrchardRewardPolicy.isLeyuanRewardConfirmed(
                queryLeyuanTasks(),
                task.sceneCode,
                task.taskType
            )
        ) {
            AntOrchardRewardOutcome.CONFIRMED
        } else {
            AntOrchardRewardOutcome.RETRY
        }
    }

    suspend fun processTask(
        task: AntOrchardTaskState
    ): AntOrchardRewardOutcome {
        return when (AntOrchardRewardPolicy.decideTask(task)) {
            AntOrchardTaskDecision.COMPLETE -> {
                val actionResponse = finishTask(task)
                if (!AntOrchardRewardPolicy.isActionAccepted(actionResponse)) {
                    AntOrchardRewardOutcome.RETRY
                } else if (
                    AntOrchardRewardPolicy.isCompletionConfirmed(
                        listTasks(),
                        task.id
                    )
                ) {
                    AntOrchardRewardOutcome.CONFIRMED
                } else {
                    AntOrchardRewardOutcome.RETRY
                }
            }
            AntOrchardTaskDecision.CLAIM -> {
                val actionResponse = claimTask(task)
                if (!AntOrchardRewardPolicy.isActionAccepted(actionResponse)) {
                    AntOrchardRewardOutcome.RETRY
                } else if (
                    AntOrchardRewardPolicy.isRewardConfirmed(
                        listTasks(),
                        task.id
                    )
                ) {
                    AntOrchardRewardOutcome.CONFIRMED
                } else {
                    AntOrchardRewardOutcome.RETRY
                }
            }
            AntOrchardTaskDecision.SKIP_UNSAFE ->
                AntOrchardRewardOutcome.SKIPPED_UNSAFE
            AntOrchardTaskDecision.TERMINAL ->
                AntOrchardRewardOutcome.TERMINAL
            else -> AntOrchardRewardOutcome.RETRY
        }
    }
}
