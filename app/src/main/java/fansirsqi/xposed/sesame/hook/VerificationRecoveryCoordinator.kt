package fansirsqi.xposed.sesame.hook

class VerificationRecoveryCoordinator(
    private val schedule: (Long, String, () -> Unit) -> Unit,
    private val probe: (Long) -> VerificationProbeResult,
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
        val result = runCatching { probe(generation) }
            .getOrDefault(VerificationProbeResult(dispatched = false, successful = false))
        if (result.dispatched) {
            onAttemptChanged(generation, tracker.attemptCount + 1)
        }
        when (tracker.completeAttempt(generation, result)) {
            ProbeCompletion.SCHEDULE_NEXT -> scheduleNext(generation)
            ProbeCompletion.RECOVERED -> onRecovered(generation)
            ProbeCompletion.EXHAUSTED -> onExhausted(generation)
            ProbeCompletion.STALE -> Unit
        }
    }
}
