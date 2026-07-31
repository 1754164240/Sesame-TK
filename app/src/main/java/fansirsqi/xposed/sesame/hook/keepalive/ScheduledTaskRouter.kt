package fansirsqi.xposed.sesame.hook.keepalive

import java.util.concurrent.ConcurrentHashMap

interface ScheduledRouteEnvironment {
    fun isTargetProcess(): Boolean

    fun currentOwnerUserId(): String?

    fun requestExecution(schedule: PersistentSchedule): Boolean

    fun sendToTarget(schedule: PersistentSchedule): Boolean

    fun launchTarget(): Boolean
}

class ScheduledTaskRouter(
    private val environment: ScheduledRouteEnvironment,
    private val foregroundLaunchEnabled: (PersistentSchedule) -> Boolean
) : ScheduleTaskDispatcher {
    private val launchedVerificationCycles = ConcurrentHashMap.newKeySet<String>()

    override fun dispatch(schedule: PersistentSchedule): ScheduleDispatchResult {
        if (schedule.kind == PersistentScheduleKind.UNKNOWN) {
            return ScheduleDispatchResult.UNSUPPORTED
        }

        if (environment.isTargetProcess()) {
            val owner = schedule.ownerUserId?.trim().orEmpty()
            val currentOwner = environment.currentOwnerUserId()?.trim().orEmpty()
            if (owner.isNotEmpty() && currentOwner.isNotEmpty() && owner != currentOwner) {
                return ScheduleDispatchResult.SKIPPED_ACCOUNT
            }
            return if (environment.requestExecution(schedule)) {
                ScheduleDispatchResult.ROUTED
            } else {
                ScheduleDispatchResult.RETRY
            }
        }

        if (!environment.sendToTarget(schedule)) {
            return ScheduleDispatchResult.RETRY
        }
        val shouldLaunch = PersistentLaunchPolicy.shouldLaunchTarget(
            foregroundLaunchEnabled(schedule),
            schedule
        ) && (
            schedule.kind != PersistentScheduleKind.VERIFICATION_PROBE ||
                launchedVerificationCycles.add(schedule.dedupeKey)
            )
        if (shouldLaunch) {
            environment.launchTarget()
        }

        // 发送广播只表示请求已投递，必须等待目标进程按 generation 确认。
        return ScheduleDispatchResult.DEFERRED
    }
}
