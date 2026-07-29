package fansirsqi.xposed.sesame.task.antMember

enum class SesameCreditRewardOutcome {
    CONFIRMED,
    TERMINAL,
    SKIPPED_UNSAFE,
    RETRY
}

class SesameCreditRewardWorkflow(
    private val queryNextDayAward: suspend () -> String,
    private val claimNextDayAward: suspend (String) -> String,
    private val queryTimeLimitedTask: suspend () -> String,
    private val claimTimeLimitedReward: suspend (String) -> String,
    private val queryFeedback: suspend () -> String,
    private val collectFeedback: suspend (Set<String>) -> String,
    private val queryTasks: suspend () -> String,
    completeTask: suspend (SesameCreditTaskState) -> String
) {
    private val submitTask = completeTask

    suspend fun claimNextDayAward(
        award: SesameAlchemyNextDayAward
    ): SesameCreditRewardOutcome {
        if (!award.available) return SesameCreditRewardOutcome.TERMINAL
        if (award.awardId.isBlank()) return SesameCreditRewardOutcome.RETRY
        val actionResponse = claimNextDayAward(award.awardId)
        if (!SesameCreditRewardPolicy.isActionAccepted(actionResponse)) {
            return SesameCreditRewardOutcome.RETRY
        }
        return if (
            SesameCreditRewardPolicy.isNextDayAwardConfirmed(
                queryNextDayAward(),
                award.awardId
            )
        ) {
            SesameCreditRewardOutcome.CONFIRMED
        } else {
            SesameCreditRewardOutcome.RETRY
        }
    }

    suspend fun claimTimeLimitedReward(
        task: SesameAlchemyTimeLimitedTask
    ): SesameCreditRewardOutcome {
        if (!task.claimable) return SesameCreditRewardOutcome.TERMINAL
        val actionResponse = claimTimeLimitedReward(task.templateId)
        if (!SesameCreditRewardPolicy.isActionAccepted(actionResponse)) {
            return SesameCreditRewardOutcome.RETRY
        }
        return if (
            SesameCreditRewardPolicy.isTimeLimitedRewardConfirmed(
                queryTimeLimitedTask(),
                task.templateId
            )
        ) {
            SesameCreditRewardOutcome.CONFIRMED
        } else {
            SesameCreditRewardOutcome.RETRY
        }
    }

    suspend fun collectFeedback(
        items: List<SesameCreditFeedback>
    ): SesameCreditRewardOutcome {
        val targetIds = items
            .filter { it.status.equals("UNCLAIMED", true) }
            .map(SesameCreditFeedback::id)
            .filter(String::isNotBlank)
            .toSet()
        if (targetIds.isEmpty()) return SesameCreditRewardOutcome.TERMINAL
        val actionResponse = collectFeedback(targetIds)
        if (!SesameCreditRewardPolicy.isActionAccepted(actionResponse)) {
            return SesameCreditRewardOutcome.RETRY
        }
        return if (
            SesameCreditRewardPolicy.isFeedbackCollectionConfirmed(
                queryFeedback(),
                targetIds
            )
        ) {
            SesameCreditRewardOutcome.CONFIRMED
        } else {
            SesameCreditRewardOutcome.RETRY
        }
    }

    suspend fun completeTask(
        task: SesameCreditTaskState
    ): SesameCreditRewardOutcome {
        return when (SesameCreditRewardPolicy.decideTask(task)) {
            SesameCreditTaskDecision.TERMINAL ->
                SesameCreditRewardOutcome.TERMINAL
            SesameCreditTaskDecision.EXECUTE_FREE ->
                executeAndConfirmTask(task)
            else -> SesameCreditRewardOutcome.SKIPPED_UNSAFE
        }
    }

    private suspend fun executeAndConfirmTask(
        task: SesameCreditTaskState
    ): SesameCreditRewardOutcome {
        val isZhimaPigeon = task.templateId ==
            SesameCreditRewardPolicy.ZHIMA_PIGEON_TEMPLATE_ID
        val beforeFeedback = if (isZhimaPigeon) {
            SesameCreditRewardPolicy.parseFeedback(queryFeedback())
        } else {
            SesameCreditFeedbackSnapshot(false, emptyList())
        }
        val actionResponse = submitTask(task)
        if (!SesameCreditRewardPolicy.isActionAccepted(actionResponse)) {
            return SesameCreditRewardOutcome.RETRY
        }
        val taskResponse = queryTasks()
        val confirmed = if (isZhimaPigeon) {
            val afterFeedback = SesameCreditRewardPolicy.parseFeedback(
                queryFeedback()
            )
            SesameCreditRewardPolicy.isZhimaPigeonCompletionConfirmed(
                taskResponse = taskResponse,
                templateId = task.templateId,
                beforePotentialTotal = if (beforeFeedback.recognized) {
                    beforeFeedback.potentialTotal
                } else {
                    Int.MAX_VALUE
                },
                afterPotentialTotal = if (afterFeedback.recognized) {
                    afterFeedback.potentialTotal
                } else {
                    Int.MIN_VALUE
                }
            )
        } else {
            SesameCreditRewardPolicy.isTaskCompletionConfirmed(
                taskResponse,
                task.templateId
            )
        }
        return if (confirmed) {
            SesameCreditRewardOutcome.CONFIRMED
        } else {
            SesameCreditRewardOutcome.RETRY
        }
    }
}
