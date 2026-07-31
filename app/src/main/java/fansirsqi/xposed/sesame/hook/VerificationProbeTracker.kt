package fansirsqi.xposed.sesame.hook

enum class ProbeCompletion {
    SCHEDULE_NEXT,
    RECOVERED,
    EXHAUSTED,
    STALE
}

data class VerificationProbeResult(
    val dispatched: Boolean,
    val successful: Boolean
)

class VerificationProbeTracker(
    val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
) {
    private var generation: Long = 0L
    private var inFlight = false

    var attemptCount: Int = 0
        private set

    @Synchronized
    fun startCycle(newGeneration: Long, initialAttemptCount: Int = 0) {
        generation = newGeneration
        attemptCount = initialAttemptCount.coerceIn(0, maxAttempts)
        inFlight = false
    }

    @Synchronized
    fun tryBeginAttempt(expectedGeneration: Long): Boolean {
        if (expectedGeneration != generation || inFlight || attemptCount >= maxAttempts) {
            return false
        }
        inFlight = true
        return true
    }

    @Synchronized
    fun completeAttempt(
        expectedGeneration: Long,
        result: VerificationProbeResult
    ): ProbeCompletion {
        if (expectedGeneration != generation) {
            return ProbeCompletion.STALE
        }
        inFlight = false
        if (!result.dispatched) {
            return ProbeCompletion.SCHEDULE_NEXT
        }
        attemptCount++
        if (result.successful) {
            return ProbeCompletion.RECOVERED
        }
        return if (attemptCount >= maxAttempts) {
            ProbeCompletion.EXHAUSTED
        } else {
            ProbeCompletion.SCHEDULE_NEXT
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 10_000L
        const val DEFAULT_MAX_ATTEMPTS = 30
    }
}
