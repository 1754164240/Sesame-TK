package fansirsqi.xposed.sesame.task.antFarm

enum class FarmGameOutcome {
    CONFIRMED,
    RETRY,
    NO_ACTION
}

data class FarmGameRunResult(
    val outcome: FarmGameOutcome,
    val before: FarmGameReadOnlySnapshot?,
    val after: FarmGameReadOnlySnapshot?,
    val message: String
)

class FarmGameWorkflow(
    private val queryGame: (String) -> String,
    private val submitScore: (String) -> String,
    private val pauseAfterAction: () -> Unit,
    private val isActionSuccess: (String) -> Boolean
) {

    fun play(gameType: String): FarmGameRunResult {
        val before = querySnapshot(gameType)
            ?: return retry(null, null, "游戏初始化状态未知")
        if (
            before.remainingGameCount <= 0 ||
            before.levelThreeRewardReceived
        ) {
            return FarmGameRunResult(
                FarmGameOutcome.NO_ACTION,
                before,
                before,
                "游戏次数已用完或奖励已领满"
            )
        }
        val submitResponse = runCatching { submitScore(gameType) }.getOrNull()
        if (
            submitResponse == null ||
            !runCatching { isActionSuccess(submitResponse) }.getOrDefault(false)
        ) {
            return retry(before, null, "游戏成绩提交失败")
        }
        pauseAfterAction()
        val after = querySnapshot(gameType)
            ?: return retry(before, null, "提交后游戏状态未知")
        val advanced =
            after.remainingGameCount < before.remainingGameCount ||
                (
                    !before.levelThreeRewardReceived &&
                        after.levelThreeRewardReceived
                    )
        return if (advanced) {
            FarmGameRunResult(
                FarmGameOutcome.CONFIRMED,
                before,
                after,
                "服务端游戏状态已推进"
            )
        } else {
            retry(before, after, "提交后游戏状态无进展")
        }
    }

    private fun querySnapshot(gameType: String): FarmGameReadOnlySnapshot? {
        val response = runCatching { queryGame(gameType) }.getOrNull()
            ?: return null
        return FarmGameReadOnlyPolicy.parse(response)
            .takeIf(FarmGameReadOnlySnapshot::recognized)
    }

    private fun retry(
        before: FarmGameReadOnlySnapshot?,
        after: FarmGameReadOnlySnapshot?,
        message: String
    ): FarmGameRunResult {
        return FarmGameRunResult(
            FarmGameOutcome.RETRY,
            before,
            after,
            message
        )
    }
}
