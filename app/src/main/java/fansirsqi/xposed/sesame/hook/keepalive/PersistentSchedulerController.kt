package fansirsqi.xposed.sesame.hook.keepalive

class PersistentSchedulerController(
    private val service: PersistentScheduleGateway,
    private val planner: PersistentSchedulePlanner = PersistentSchedulePlanner(),
    private val enabled: () -> Boolean,
    private val allowForegroundLaunch: () -> Boolean = { false },
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    fun schedulePoll(
        triggerAtMillis: Long,
        ownerUserId: String?,
        legacySchedule: () -> Unit
    ) {
        val nowMillis = nowProvider()
        if (enabled()) {
            try {
                service.register(
                    planner.globalPoll(triggerAtMillis, ownerUserId, allowForegroundLaunch()),
                    nowMillis
                )
            } catch (_: PersistentScheduleUnavailableException) {
                legacySchedule()
            }
            return
        }

        try {
            service.cancel(PersistentScheduleKey.GLOBAL_POLL, nowMillis)
        } catch (_: PersistentScheduleUnavailableException) {
            // 持久服务不可用时无需清理远端记录
        }
        legacySchedule()
    }

    fun replaceWakeSchedules(
        nowMillis: Long,
        rawTimes: Collection<String>?,
        ownerUserId: String?,
        legacySchedule: () -> Unit
    ) {
        if (enabled()) {
            try {
                service.replaceWakeSchedules(
                    planner.wakeSchedules(
                        nowMillis,
                        rawTimes,
                        ownerUserId,
                        allowForegroundLaunch()
                    ),
                    nowMillis
                )
            } catch (_: PersistentScheduleUnavailableException) {
                legacySchedule()
            }
            return
        }

        try {
            service.replaceWakeSchedules(emptyList(), nowMillis)
        } catch (_: PersistentScheduleUnavailableException) {
            // 持久服务不可用时无需清理远端记录
        }
        legacySchedule()
    }

    fun cancelPoll(nowMillis: Long = nowProvider()): Boolean =
        try {
            service.cancel(PersistentScheduleKey.GLOBAL_POLL, nowMillis)
        } catch (_: PersistentScheduleUnavailableException) {
            false
        }

    fun scheduleVerificationProbe(
        triggerAtMillis: Long,
        ownerUserId: String?,
        verificationGeneration: Long,
        attempt: Int,
        legacySchedule: () -> Unit
    ) {
        val nowMillis = nowProvider()
        if (enabled()) {
            try {
                service.register(
                    planner.verificationProbe(
                        triggerAtMillis = triggerAtMillis,
                        ownerUserId = ownerUserId,
                        verificationGeneration = verificationGeneration,
                        attempt = attempt,
                        allowForegroundLaunch = allowForegroundLaunch()
                    ),
                    nowMillis
                )
            } catch (_: PersistentScheduleUnavailableException) {
                legacySchedule()
            }
            return
        }
        try {
            service.cancel(
                PersistentScheduleKey.verificationProbe(ownerUserId, verificationGeneration),
                nowMillis
            )
        } catch (_: PersistentScheduleUnavailableException) {
            // 持久服务不可用时无需清理远端记录
        }
        legacySchedule()
    }

    fun reconcile(nowMillis: Long = nowProvider()): ReconcileResult =
        try {
            service.reconcile(nowMillis)
        } catch (_: PersistentScheduleUnavailableException) {
            ReconcileResult(
                nextTriggerAtMillis = null,
                usedFallback = true,
                recoveredClaims = 0
            )
        }
}
