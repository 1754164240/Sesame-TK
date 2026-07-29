package fansirsqi.xposed.sesame.hook.keepalive

import kotlin.math.max

interface ScheduleAlarmBackend {
    fun arm(triggerAtMillis: Long): Boolean

    fun cancel(): Boolean
}

interface ScheduleFallback {
    fun schedule(delayMillis: Long, dedupeKey: String, callback: () -> Unit)

    fun cancel(dedupeKey: String) = Unit
}

interface ScheduleTaskDispatcher {
    fun dispatch(schedule: PersistentSchedule): ScheduleDispatchResult
}

data class ReconcileResult(
    val nextTriggerAtMillis: Long?,
    val usedFallback: Boolean,
    val recoveredClaims: Int
)

data class DispatchBatchResult(
    val claimedCount: Int,
    val completedCount: Int,
    val retriedCount: Int,
    val nextTriggerAtMillis: Long?
)

class PersistentScheduleCoordinator(
    private val registry: PersistentScheduleRegistry,
    private val alarmBackend: ScheduleAlarmBackend,
    private val fallback: ScheduleFallback,
    private val dispatcher: ScheduleTaskDispatcher,
    private val dueWindowMillis: Long = DEFAULT_DUE_WINDOW_MILLIS,
    private val leaseMillis: Long = DEFAULT_LEASE_MILLIS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS
) {
    private var armedTriggerAtMillis: Long? = null
    private var fallbackTriggerAtMillis: Long? = null

    @Synchronized
    fun reconcile(nowMillis: Long): ReconcileResult {
        val recovered = registry.recoverExpiredClaims(nowMillis)
        val next = registry.nextPending()
        if (next == null) {
            if (armedTriggerAtMillis != null || fallbackTriggerAtMillis != null) {
                alarmBackend.cancel()
            }
            if (fallbackTriggerAtMillis != null) {
                fallback.cancel(FALLBACK_KEY)
            }
            armedTriggerAtMillis = null
            fallbackTriggerAtMillis = null
            return ReconcileResult(null, usedFallback = false, recoveredClaims = recovered)
        }

        val target = next.triggerAtMillis
        if (armedTriggerAtMillis == target) {
            return ReconcileResult(target, usedFallback = false, recoveredClaims = recovered)
        }
        if (fallbackTriggerAtMillis == target) {
            return ReconcileResult(target, usedFallback = true, recoveredClaims = recovered)
        }

        if (alarmBackend.arm(target)) {
            if (fallbackTriggerAtMillis != null) {
                fallback.cancel(FALLBACK_KEY)
            }
            armedTriggerAtMillis = target
            fallbackTriggerAtMillis = null
            return ReconcileResult(target, usedFallback = false, recoveredClaims = recovered)
        }

        val delay = max(0L, target - nowMillis)
        fallback.schedule(delay, FALLBACK_KEY) {
            onAlarm(System.currentTimeMillis())
        }
        armedTriggerAtMillis = null
        fallbackTriggerAtMillis = target
        return ReconcileResult(target, usedFallback = true, recoveredClaims = recovered)
    }

    @Synchronized
    fun forceReconcile(nowMillis: Long): ReconcileResult {
        if (armedTriggerAtMillis != null) {
            alarmBackend.cancel()
        }
        if (fallbackTriggerAtMillis != null) {
            fallback.cancel(FALLBACK_KEY)
        }
        armedTriggerAtMillis = null
        fallbackTriggerAtMillis = null
        return reconcile(nowMillis)
    }

    @Synchronized
    fun onAlarm(nowMillis: Long): DispatchBatchResult {
        if (fallbackTriggerAtMillis != null) {
            fallback.cancel(FALLBACK_KEY)
        }
        armedTriggerAtMillis = null
        fallbackTriggerAtMillis = null
        registry.recoverExpiredClaims(nowMillis)
        val claimed = registry.claimDue(nowMillis, dueWindowMillis, leaseMillis)
        var completed = 0
        var retried = 0

        claimed.forEach { schedule ->
            when (dispatcher.dispatch(schedule)) {
                ScheduleDispatchResult.ROUTED,
                ScheduleDispatchResult.SKIPPED_ACCOUNT -> {
                    if (registry.complete(schedule.dedupeKey, schedule.generation)) completed++
                }

                ScheduleDispatchResult.DEFERRED,
                ScheduleDispatchResult.RETRY,
                ScheduleDispatchResult.UNSUPPORTED -> {
                    if (
                        registry.retry(
                            schedule.dedupeKey,
                            schedule.generation,
                            nowMillis + retryDelayMillis,
                            nowMillis
                        )
                    ) {
                        retried++
                    }
                }
            }
        }
        val next = reconcile(nowMillis).nextTriggerAtMillis
        return DispatchBatchResult(claimed.size, completed, retried, next)
    }

    companion object {
        const val FALLBACK_KEY = "persistent-schedule-fallback"
        const val DEFAULT_DUE_WINDOW_MILLIS = 1_000L
        const val DEFAULT_LEASE_MILLIS = 30_000L
        const val DEFAULT_RETRY_DELAY_MILLIS = 60_000L
    }
}
