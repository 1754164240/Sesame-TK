package fansirsqi.xposed.sesame.task.antMember

class GameCenterP2eRewardWorkflow(
    private val queryHome: () -> String,
    private val signInAction: (GameCenterP2eSignInSnapshot) -> String,
    private val drawGoldAction: () -> String,
    private val queryCashTierPage: () -> String,
    private val isActionSuccess: (String) -> Boolean,
    private val pauseAfterAction: () -> Unit = {}
) {

    fun signIn(): GameCenterRewardOutcome {
        val initial = querySignInSnapshot()
        if (!initial.recognized) {
            return retry("P2E签到查询失败或结构未知")
        }
        if (initial.riskLimited) {
            return noAction("P2E签到业务受限")
        }
        if (initial.signedIn) {
            return noAction("P2E今日已签到")
        }
        if (initial.date.isBlank() || initial.signSequenceId.isBlank()) {
            return retry("P2E签到动态参数缺失")
        }
        if (!performAction { signInAction(initial) }) {
            return retry("P2E签到请求失败")
        }
        pauseAfterAction()
        val refreshed = runCatching { queryHome() }.getOrNull().orEmpty()
        return if (GameCenterRewardPolicy.isP2eSignInConfirmed(refreshed)) {
            confirmed("P2E签到服务端状态已确认")
        } else {
            retry("P2E签到后状态未刷新")
        }
    }

    fun drawGold(): GameCenterRewardOutcome {
        val initial = queryDrawSnapshot()
        if (!initial.recognized) {
            return retry("P2E抽金币查询失败或结构未知")
        }
        if (initial.riskLimited) {
            return noAction("P2E抽金币业务受限")
        }
        if (GameCenterRewardPolicy.isP2eDrawConfirmed(initial)) {
            return noAction("P2E今日已抽金币")
        }
        if (initial.status !in setOf("NOT_DRAWN", "FULFILL_FAILED")) {
            return retry("P2E抽金币状态未知")
        }
        if (!performAction(drawGoldAction)) {
            return retry("P2E抽金币请求失败")
        }
        pauseAfterAction()
        val refreshed = queryDrawSnapshot()
        return if (GameCenterRewardPolicy.isP2eDrawConfirmed(refreshed)) {
            confirmed("P2E抽金币服务端状态已确认")
        } else {
            retry("P2E抽金币后状态未刷新")
        }
    }

    fun queryCashTiers(): GameCenterCashTierSnapshot {
        val response = runCatching { queryCashTierPage() }.getOrNull().orEmpty()
        return GameCenterRewardPolicy.parseCashTiers(response)
    }

    private fun querySignInSnapshot(): GameCenterP2eSignInSnapshot {
        val response = runCatching { queryHome() }.getOrNull().orEmpty()
        return GameCenterRewardPolicy.parseP2eSignIn(response)
    }

    private fun queryDrawSnapshot(): GameCenterP2eDrawSnapshot {
        val response = runCatching { queryHome() }.getOrNull().orEmpty()
        return GameCenterRewardPolicy.parseP2eDraw(response)
    }

    private fun performAction(action: () -> String): Boolean {
        val response = runCatching(action).getOrNull() ?: return false
        return runCatching { isActionSuccess(response) }.getOrDefault(false)
    }

    private fun confirmed(message: String): GameCenterRewardOutcome {
        return GameCenterRewardOutcome(GameCenterRewardState.CONFIRMED, message)
    }

    private fun noAction(message: String): GameCenterRewardOutcome {
        return GameCenterRewardOutcome(GameCenterRewardState.NO_ACTION, message)
    }

    private fun retry(message: String): GameCenterRewardOutcome {
        return GameCenterRewardOutcome(GameCenterRewardState.RETRY, message)
    }
}
