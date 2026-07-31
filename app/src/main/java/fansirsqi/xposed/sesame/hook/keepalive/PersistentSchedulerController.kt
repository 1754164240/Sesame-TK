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
            service.register(
                planner.globalPoll(triggerAtMillis, ownerUserId, allowForegroundLaunch()),
                nowMillis
            )
            return
        }

        service.cancel(PersistentScheduleKey.GLOBAL_POLL, nowMillis)
        legacySchedule()
    }

    fun replaceWakeSchedules(
        nowMillis: Long,
        rawTimes: Collection<String>?,
        ownerUserId: String?,
        legacySchedule: () -> Unit
    ) {
        if (enabled()) {
            service.replaceWakeSchedules(
                planner.wakeSchedules(
                    nowMillis,
                    rawTimes,
                    ownerUserId,
                    allowForegroundLaunch()
                ),
                nowMillis
            )
            return
        }

        service.replaceWakeSchedules(emptyList(), nowMillis)
        legacySchedule()
    }

    fun cancelPoll(nowMillis: Long = nowProvider()): Boolean =
        service.cancel(PersistentScheduleKey.GLOBAL_POLL, nowMillis)

    fun scheduleVerificationProbe(
        triggerAtMillis: Long,
        ownerUserId: String?,
        verificationGeneration: Long,
        attempt: Int,
        legacySchedule: () -> Unit
    ) {
        val nowMillis = nowProvider()
        if (enabled()) {
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
            return
        }
        service.cancel(
            PersistentScheduleKey.verificationProbe(ownerUserId, verificationGeneration),
            nowMillis
        )
        legacySchedule()
    }

    fun reconcile(nowMillis: Long = nowProvider()): ReconcileResult =
        service.reconcile(nowMillis)
}
