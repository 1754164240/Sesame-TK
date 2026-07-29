package fansirsqi.xposed.sesame.task.antMember

enum class GameCenterRewardState {
    CONFIRMED,
    NO_ACTION,
    RETRY
}

data class GameCenterRewardOutcome(
    val state: GameCenterRewardState,
    val message: String,
    val targetIds: Set<String> = emptySet()
)

class GameCenterRewardWorkflow(
    private val querySignIn: () -> String,
    private val signInAction: () -> String,
    private val queryPointBalls: () -> String,
    private val collectPointBallsAction: () -> String,
    private val queryStickers: () -> String,
    private val receiveStickersAction: (Set<String>) -> String,
    private val isActionSuccess: (String) -> Boolean,
    private val pauseAfterAction: () -> Unit = {}
) {

    fun signIn(): GameCenterRewardOutcome {
        val initial = querySignInSnapshot()
        if (!initial.recognized) {
            return retry("签到查询失败或结构未知")
        }
        if (initial.signedIn) {
            return noAction("今日已签到")
        }
        if (!performAction(signInAction)) {
            return retry("签到请求失败")
        }
        pauseAfterAction()
        val refreshed = runCatching { querySignIn() }.getOrNull().orEmpty()
        return if (GameCenterRewardPolicy.isSignInConfirmed(refreshed)) {
            confirmed("签到服务端状态已确认")
        } else {
            retry("签到后状态未刷新")
        }
    }

    fun collectPointBalls(): GameCenterRewardOutcome {
        val initial = queryPointBallSnapshot()
        if (!initial.recognized) {
            return retry("乐豆查询失败或结构未知")
        }
        if (initial.pendingIds.isEmpty()) {
            return noAction("暂无可领取乐豆")
        }
        if (!performAction(collectPointBallsAction)) {
            return retry("乐豆领取请求失败", initial.pendingIds)
        }
        pauseAfterAction()
        val refreshed = queryPointBallSnapshot()
        return if (
            GameCenterRewardPolicy.isPointBallCollectionConfirmed(initial, refreshed)
        ) {
            confirmed("乐豆服务端状态已确认", initial.pendingIds)
        } else {
            retry("乐豆领取后状态未刷新", initial.pendingIds)
        }
    }

    fun collectStickers(): GameCenterRewardOutcome {
        val initial = queryStickerSnapshot()
        if (!initial.recognized) {
            return retry("贴纸查询失败或结构未知")
        }
        if (initial.hasUnidentifiedItems) {
            return retry("贴纸列表包含未知记录")
        }
        if (initial.receivableIds.isEmpty()) {
            return noAction("暂无可领取贴纸")
        }
        val targetIds = initial.receivableIds
        if (!performAction { receiveStickersAction(targetIds) }) {
            return retry("贴纸领取请求失败", targetIds)
        }
        pauseAfterAction()
        val refreshed = runCatching { queryStickers() }.getOrNull().orEmpty()
        return if (
            GameCenterRewardPolicy.isStickerCollectionConfirmed(refreshed, targetIds)
        ) {
            confirmed("贴纸服务端状态已确认", targetIds)
        } else {
            retry("贴纸领取后状态未刷新", targetIds)
        }
    }

    private fun querySignInSnapshot(): GameCenterSignInSnapshot {
        val response = runCatching { querySignIn() }.getOrNull().orEmpty()
        return GameCenterRewardPolicy.parseSignIn(response)
    }

    private fun queryPointBallSnapshot(): GameCenterPointBallSnapshot {
        val response = runCatching { queryPointBalls() }.getOrNull().orEmpty()
        return GameCenterRewardPolicy.parsePointBalls(response)
    }

    private fun queryStickerSnapshot(): GameCenterStickerSnapshot {
        val response = runCatching { queryStickers() }.getOrNull().orEmpty()
        return GameCenterRewardPolicy.parseStickers(response)
    }

    private fun performAction(action: () -> String): Boolean {
        val response = runCatching(action).getOrNull() ?: return false
        return runCatching { isActionSuccess(response) }.getOrDefault(false)
    }

    private fun confirmed(
        message: String,
        targetIds: Set<String> = emptySet()
    ): GameCenterRewardOutcome {
        return GameCenterRewardOutcome(
            state = GameCenterRewardState.CONFIRMED,
            message = message,
            targetIds = targetIds
        )
    }

    private fun noAction(message: String): GameCenterRewardOutcome {
        return GameCenterRewardOutcome(
            state = GameCenterRewardState.NO_ACTION,
            message = message
        )
    }

    private fun retry(
        message: String,
        targetIds: Set<String> = emptySet()
    ): GameCenterRewardOutcome {
        return GameCenterRewardOutcome(
            state = GameCenterRewardState.RETRY,
            message = message,
            targetIds = targetIds
        )
    }
}
