package fansirsqi.xposed.sesame.task.antForest

internal class AntForestShieldRetryPolicy {
    private var attempted = false
    private var inFlight = false
    private var round = 0L
    private var attemptRound = 0L
    private val attemptedIds = mutableSetOf<String>()

    @Synchronized
    fun recordAttemptedIds(shieldIds: Set<String>) {
        // 领取或兑换可能晚于轮次重置返回，不能污染下一轮的去重记录。
        if (inFlight && attemptRound == round) attemptedIds.addAll(shieldIds)
    }

    @Synchronized
    fun startRound() {
        round++
        attempted = false
        attemptedIds.clear()
    }

    @Synchronized
    fun tryBegin(shieldIds: Set<String>): Boolean {
        if (inFlight || (attempted && shieldIds.all { it in attemptedIds })) return false
        attempted = true
        attemptedIds.addAll(shieldIds)
        attemptRound = round
        inFlight = true
        return true
    }

    @Synchronized
    fun finish() {
        inFlight = false
    }
}
