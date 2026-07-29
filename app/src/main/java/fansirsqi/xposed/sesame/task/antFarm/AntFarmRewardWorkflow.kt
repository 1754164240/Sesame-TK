package fansirsqi.xposed.sesame.task.antFarm

enum class FarmRewardOutcome {
    CONFIRMED,
    RETRY,
    SKIPPED_CAPACITY
}

data class FarmRewardClaim(
    val candidate: FarmRewardCandidate,
    val outcome: FarmRewardOutcome
)

data class FarmRewardRunResult(
    val claims: List<FarmRewardClaim>,
    val confirmedAmount: Int
)

class AntFarmRewardWorkflow(
    private val receiveFarmTaskAward: suspend (String) -> String,
    private val listFarmTask: suspend () -> String,
    private val receiveZhimaNpcFarmTaskAward: suspend (String) -> String,
    private val listZhimaNpcFarmTask: suspend () -> String,
    private val receiveParadiseLimitedActivityAward:
        suspend (String, Int) -> String,
    private val queryParadiseLimitedActivity: suspend () -> String
) {

    suspend fun claimParadiseReward(
        taskType: String,
        awardCount: Int
    ): FarmRewardOutcome {
        if (taskType.isBlank()) {
            return FarmRewardOutcome.RETRY
        }
        receiveParadiseLimitedActivityAward(taskType, awardCount)
        return if (
            AntFarmRewardPolicy.isParadiseRewardConfirmed(
                queryParadiseLimitedActivity(),
                taskType
            )
        ) {
            FarmRewardOutcome.CONFIRMED
        } else {
            FarmRewardOutcome.RETRY
        }
    }

    suspend fun claimZhimaNpcReward(taskId: String): FarmRewardOutcome {
        if (taskId.isBlank()) {
            return FarmRewardOutcome.RETRY
        }
        receiveZhimaNpcFarmTaskAward(taskId)
        return if (
            AntFarmRewardPolicy.isTaskReceived(
                listZhimaNpcFarmTask(),
                taskId
            )
        ) {
            FarmRewardOutcome.CONFIRMED
        } else {
            FarmRewardOutcome.RETRY
        }
    }

    suspend fun claimFarmRewards(
        candidates: List<FarmRewardCandidate>,
        remainingCapacity: Int
    ): FarmRewardRunResult {
        val selected = AntFarmRewardPolicy.selectWithinCapacity(
            candidates,
            remainingCapacity
        )
        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
        val claims = candidates.map { candidate ->
            if (candidate.id !in selectedIds) {
                FarmRewardClaim(
                    candidate = candidate,
                    outcome = FarmRewardOutcome.SKIPPED_CAPACITY
                )
            } else {
                receiveFarmTaskAward(candidate.id)
                val queryResponse = listFarmTask()
                FarmRewardClaim(
                    candidate = candidate,
                    outcome = if (
                        AntFarmRewardPolicy.isTaskReceived(
                            queryResponse,
                            candidate.id
                        )
                    ) {
                        FarmRewardOutcome.CONFIRMED
                    } else {
                        FarmRewardOutcome.RETRY
                    }
                )
            }
        }
        return FarmRewardRunResult(
            claims = claims,
            confirmedAmount = claims
                .filter { it.outcome == FarmRewardOutcome.CONFIRMED }
                .sumOf { it.candidate.amount }
        )
    }
}
