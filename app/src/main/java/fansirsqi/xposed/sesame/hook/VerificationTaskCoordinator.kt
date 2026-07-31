package fansirsqi.xposed.sesame.hook

class VerificationTaskCoordinator(
    private val stopAction: () -> Unit
) {
    private val stoppedGenerations = linkedSetOf<Long>()
    private val recoveredGenerations = linkedSetOf<Long>()

    @Synchronized
    fun stopForVerification(generation: Long): Boolean {
        if (!stoppedGenerations.add(generation)) return false
        stopAction()
        return true
    }

    @Synchronized
    fun claimRecovery(generation: Long): Boolean =
        recoveredGenerations.add(generation)
}
