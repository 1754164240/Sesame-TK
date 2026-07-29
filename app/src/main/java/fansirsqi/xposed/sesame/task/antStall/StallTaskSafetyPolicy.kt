package fansirsqi.xposed.sesame.task.antStall

enum class StallTaskDecision {
    FINISH_RPC,
    HANDLE_QA,
    HANDLE_INVITE,
    SKIP_GAME,
    SKIP_AD,
    SKIP_FINANCIAL,
    SKIP_UNKNOWN
}

object StallTaskSafetyPolicy {
    private val gameSignals = listOf("GAME", "MINI_GAME", "NONGCHANGLEYUAN", "小游戏", "玩游戏", "农场乐园")
    private val adSignals = listOf("LIGHT_AD", "AD_TASK", "ADVERTISEMENT", "看广告", "广告任务")
    private val financialSignals = listOf(
        "DIANTAO", "ORDER", "PURCHASE", "RECHARGE", "LOAN", "INVEST", "WITHDRAW",
        "提现", "下单", "购买", "充值", "借贷", "理财", "兑换"
    )
    private val safeFinishTypes = setOf("ANTSTALL_NORMAL_OPEN_NOTICE", "TIANJIASHOUYE")

    fun classify(taskType: String, title: String, actionType: String): StallTaskDecision {
        val type = taskType.trim().uppercase()
        val action = actionType.trim().uppercase()
        val combined = "$type ${title.trim()} $action"
        return when {
            containsAny(combined, gameSignals) -> StallTaskDecision.SKIP_GAME
            containsAny(combined, adSignals) -> StallTaskDecision.SKIP_AD
            containsAny(combined, financialSignals) -> StallTaskDecision.SKIP_FINANCIAL
            type == "ANTSTALL_XLIGHT_VARIABLE_AWARD" -> StallTaskDecision.SKIP_AD
            type == "ANTSTALL_NORMAL_DAILY_QA" -> StallTaskDecision.HANDLE_QA
            type == "ANTSTALL_NORMAL_INVITE_REGISTER" -> StallTaskDecision.HANDLE_INVITE
            type in safeFinishTypes && action == "VISIT_AUTO_FINISH" -> StallTaskDecision.FINISH_RPC
            else -> StallTaskDecision.SKIP_UNKNOWN
        }
    }

    private fun containsAny(value: String, signals: List<String>): Boolean {
        return signals.any { value.contains(it, ignoreCase = true) }
    }
}
