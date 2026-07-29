package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONObject

data class FarmGameReadOnlySnapshot(
    val recognized: Boolean,
    val remainingGameCount: Int,
    val levelThreeRewardReceived: Boolean
)

enum class FarmGameFeedDecision {
    RECEIVE_REWARD,
    RESERVE_FOR_GAME
}

object FarmGameReadOnlyPolicy {

    fun parse(response: String): FarmGameReadOnlySnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return unrecognized()
        if (!isSuccess(root)) {
            return unrecognized()
        }
        if (
            !root.has("remainingGameCount") ||
            root.isNull("remainingGameCount")
        ) {
            return unrecognized()
        }
        val remainingCount = root.optString("remainingGameCount")
            .toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: return unrecognized()
        val gameAward = root.optJSONObject("gameAward")
            ?: return unrecognized()
        if (
            !gameAward.has("level3Get") ||
            gameAward.opt("level3Get") !is Boolean
        ) {
            return unrecognized()
        }
        return FarmGameReadOnlySnapshot(
            recognized = true,
            remainingGameCount = remainingCount,
            levelThreeRewardReceived =
                gameAward.optBoolean("level3Get")
        )
    }

    fun decideFeedRewardAfterAcceleratedFeed(
        scoreSubmissionAllowed: Boolean,
        gameFinished: Boolean,
        foodStock: Int,
        foodStockLimit: Int,
        expectedGameReward: Int
    ): FarmGameFeedDecision {
        if (!scoreSubmissionAllowed || gameFinished) {
            return FarmGameFeedDecision.RECEIVE_REWARD
        }
        val reserveThreshold =
            foodStockLimit - expectedGameReward.coerceAtLeast(0)
        return if (foodStock < reserveThreshold) {
            FarmGameFeedDecision.RECEIVE_REWARD
        } else {
            FarmGameFeedDecision.RESERVE_FOR_GAME
        }
    }

    private fun isSuccess(root: JSONObject): Boolean {
        if (root.has("success")) {
            return root.optBoolean("success", false)
        }
        return root.optString("resultCode").uppercase() in
            setOf("SUCCESS", "100", "200", "0")
    }

    private fun unrecognized(): FarmGameReadOnlySnapshot {
        return FarmGameReadOnlySnapshot(false, 0, false)
    }
}

class FarmGameReadOnlyWorkflow(
    private val queryGame: (String) -> String
) {

    fun inspect(gameTypes: List<String>): List<FarmGameReadOnlySnapshot> {
        return gameTypes.asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .map { gameType ->
                FarmGameReadOnlyPolicy.parse(queryGame(gameType))
            }
            .toList()
    }
}
