package fansirsqi.xposed.sesame.hook

class VerificationRecoveryCoordinator(
    private val schedule: (Long, String, () -> Unit) -> Unit,
    private val probe: (Long) -> Boolean,
    private val onRecovered: (Long) -> Unit,
    private val onExhausted: (Long) -> Unit,
    private val onAttemptChanged: (Long, Int) -> Unit = { _, _ -> },
    private val tracker: VerificationProbeTracker = VerificationProbeTracker()
) {
    @Synchronized
    fun start(generation: Long, initialAttemptCount: Int = 0) {
        tracker.startCycle(generation, initialAttemptCount)
        if (initialAttemptCount < VerificationProbeTracker.DEFAULT_MAX_ATTEMPTS) {
            scheduleNext(generation)
        }
    }

    private fun scheduleNext(generation: Long) {
        schedule(
            tracker.intervalMillis,
            "人工验证恢复探测:$generation:${tracker.attemptCount + 1}"
        ) {
            runScheduledAttempt(generation)
        }
    }

    fun runScheduledAttempt(generation: Long) {
        if (!tracker.tryBeginAttempt(generation)) {
            return
        }
        onAttemptChanged(generation, tracker.attemptCount)
        val successful = runCatching { probe(generation) }.getOrDefault(false)
        when (tracker.completeAttempt(generation, successful)) {
            ProbeCompletion.SCHEDULE_NEXT -> scheduleNext(generation)
            ProbeCompletion.RECOVERED -> onRecovered(generation)
            ProbeCompletion.EXHAUSTED -> onExhausted(generation)
            ProbeCompletion.STALE -> Unit
        }
    }
}
