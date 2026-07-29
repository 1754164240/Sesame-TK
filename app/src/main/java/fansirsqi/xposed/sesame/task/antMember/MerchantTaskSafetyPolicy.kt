package fansirsqi.xposed.sesame.task.antMember

enum class MerchantTaskDecision {
    CONTINUE,
    SKIP_AD
}

object MerchantTaskSafetyPolicy {
    fun classify(hasAdBusinessId: Boolean): MerchantTaskDecision {
        return if (hasAdBusinessId) {
            MerchantTaskDecision.SKIP_AD
        } else {
            MerchantTaskDecision.CONTINUE
        }
    }
}
