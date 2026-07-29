package fansirsqi.xposed.sesame.task.antMember

data class MemberTaskCandidate(
    val configId: String,
    val title: String,
    val targetBusiness: String,
    val adBizId: String = ""
)

enum class MemberTaskDecision {
    EXECUTE_BROWSE,
    FINISH_AD,
    VERIFY_ONLY,
    CLAIM_ONLY,
    SKIP_REAL_GAME,
    SKIP_AD,
    SKIP_FINANCIAL,
    SKIP_UNSUPPORTED
}

object MemberTaskSafetyPolicy {
    private val browseConfigIds = setOf(
        "600202500151482",
        "600202400075770",
        "600202500163188",
        "600202400066231",
        "600202300028189",
        "600202300020561",
        "600202300002546",
        "600202400073337",
        "600202500136682",
        "600202300040463",
        "600202400104923",
        "600202400081445",
        "600202600208739",
        "600202500195828",
        "600202600200069",
        "600202400098334",
        "600202400102692",
        "600202500160908",
        "600202300043597",
        "600202500154335",
        "600202400066415",
        "600202400072292"
    )
    private val financialKeywords = setOf(
        "提现",
        "借款",
        "借一笔",
        "借呗",
        "贷款",
        "充值",
        "下单",
        "购买",
        "支付",
        "开通额度",
        "现金兑换"
    )
    private val gameKeywords = setOf(
        "玩游戏",
        "游戏通关",
        "完成游戏",
        "游戏订单",
        "玩任意游戏"
    )

    fun classify(candidate: MemberTaskCandidate): MemberTaskDecision {
        val text = "${candidate.title} ${candidate.targetBusiness}"
        if (financialKeywords.any(text::contains)) {
            return MemberTaskDecision.SKIP_FINANCIAL
        }
        if (gameKeywords.any(text::contains)) {
            return MemberTaskDecision.SKIP_REAL_GAME
        }
        if (candidate.adBizId.isNotBlank()) {
            return MemberTaskDecision.SKIP_AD
        }
        if (candidate.configId !in browseConfigIds) {
            return MemberTaskDecision.SKIP_UNSUPPORTED
        }

        val targetParts = candidate.targetBusiness.split("#")
        val targetType = targetParts.firstOrNull().orEmpty().uppercase()
        return when {
            targetType == "CALL_APP" &&
                targetParts.getOrNull(1).orEmpty().isNotBlank() ->
                MemberTaskDecision.VERIFY_ONLY

            targetType == "BROWSE" &&
                targetParts.getOrNull(1).orEmpty().isNotBlank() &&
                targetParts.getOrNull(2).orEmpty().isNotBlank() ->
                MemberTaskDecision.EXECUTE_BROWSE

            else -> MemberTaskDecision.SKIP_UNSUPPORTED
        }
    }
}
