package fansirsqi.xposed.sesame.task.antForest

enum class GoldBallOutcome {
    CONFIRMED,
    NO_ACTION,
    RETRY
}

data class GoldBallResult(
    val outcome: GoldBallOutcome,
    val collected: Int,
    val friendRecorded: Boolean
)

class ForestGoldBallWorkflow(
    private val recordWateredFriend: (String) -> Unit
) {
    fun handle(response: String, friendId: String? = null): GoldBallResult {
        val parsed = ForestMultiplierPolicy.parseCollectedEnergy(response)
        if (!parsed.recognized) {
            return GoldBallResult(GoldBallOutcome.RETRY, 0, false)
        }
        if (parsed.collected <= 0) {
            return GoldBallResult(GoldBallOutcome.NO_ACTION, 0, false)
        }
        val normalizedFriendId = friendId.orEmpty().trim()
        val shouldRecord = ForestMultiplierPolicy.shouldRecordWateredFriend(
            parsed,
            normalizedFriendId
        )
        if (shouldRecord) {
            recordWateredFriend(normalizedFriendId)
        }
        return GoldBallResult(
            outcome = GoldBallOutcome.CONFIRMED,
            collected = parsed.collected,
            friendRecorded = shouldRecord
        )
    }
}
