package fansirsqi.xposed.sesame.task.antOcean

enum class OceanTaskDecision {
    ANSWER,
    FINISH_RPC,
    SKIP_GAME,
    SKIP_AD,
    SKIP_FINANCIAL,
    SKIP_BUSINESS_ACTION,
    SKIP_UNKNOWN
}

object OceanTaskSafetyPolicy {
    private val gameSignals = listOf("GAME", "MINI_GAME", "小游戏", "玩游戏", "游戏中心")
    private val adSignals = listOf("LIGHT_AD", "AD_TASK", "ADVERTISEMENT", "广告", "看视频")
    private val financialSignals = listOf(
        "ORDER", "PURCHASE", "RECHARGE", "LOAN", "INVEST", "WITHDRAW",
        "下单", "购买", "充值", "借贷", "理财", "提现", "兑换"
    )
    private val businessSignals = listOf(
        "CLEAN_", "HELP_CLEAN", "FRIEND_RUBBISH", "COMBINE_FISH", "REPAIR_SEA",
        "清理海域", "清理自己", "清理好友", "合成鱼", "修复海域"
    )

    @JvmStatic
    fun classify(taskType: String, title: String, actionType: String): OceanTaskDecision {
        val type = taskType.trim().uppercase()
        val action = actionType.trim().uppercase()
        val combined = "$type ${title.trim()} $action"
        return when {
            containsAny(combined, gameSignals) -> OceanTaskDecision.SKIP_GAME
            containsAny(combined, adSignals) -> OceanTaskDecision.SKIP_AD
            containsAny(combined, financialSignals) -> OceanTaskDecision.SKIP_FINANCIAL
            title.contains("答题") || type.contains("QUESTION") -> OceanTaskDecision.ANSWER
            containsAny(combined, businessSignals) -> OceanTaskDecision.SKIP_BUSINESS_ACTION
            action == "VISIT_AUTO_FINISH" && (type.contains("BROWSE") || type.contains("VISIT")) -> {
                OceanTaskDecision.FINISH_RPC
            }
            else -> OceanTaskDecision.SKIP_UNKNOWN
        }
    }

    private fun containsAny(value: String, signals: List<String>): Boolean {
        return signals.any { value.contains(it, ignoreCase = true) }
    }
}
