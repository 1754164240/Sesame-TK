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
    SKIP_UNSUPPORTED
}

object MemberTaskSafetyPolicy {
    private val blockedTitleKeywords = setOf(
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

    fun classify(candidate: MemberTaskCandidate): MemberTaskDecision {
        if (blockedTitleKeywords.any(candidate.title::contains)) {
            return MemberTaskDecision.SKIP_UNSUPPORTED
        }

        if (candidate.adBizId.isNotBlank()) {
            return MemberTaskDecision.FINISH_AD
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
