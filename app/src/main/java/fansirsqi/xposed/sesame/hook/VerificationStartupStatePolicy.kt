package fansirsqi.xposed.sesame.hook

enum class VerificationStartupAction {
    CLEAR_STALE,
    RESUME_CURRENT
}

object VerificationStartupStatePolicy {
    fun resolve(
        currentBlockReason: RpcBlockReason,
        currentGeneration: Long,
        persistedGeneration: Long
    ): VerificationStartupAction =
        if (
            currentBlockReason == RpcBlockReason.VERIFICATION &&
            currentGeneration == persistedGeneration
        ) {
            VerificationStartupAction.RESUME_CURRENT
        } else {
            VerificationStartupAction.CLEAR_STALE
        }
}
