package fansirsqi.xposed.sesame.hook.keepalive

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
        if (PersistentLaunchPolicy.shouldLaunchTarget(foregroundLaunchEnabled(schedule), schedule)) {
            environment.launchTarget()
        }

        // 发送广播只表示请求已投递，必须等待目标进程按 generation 确认。
        return ScheduleDispatchResult.DEFERRED
    }
}
