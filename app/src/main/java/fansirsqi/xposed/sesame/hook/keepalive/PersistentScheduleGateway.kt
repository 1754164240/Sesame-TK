package fansirsqi.xposed.sesame.hook.keepalive

interface PersistentScheduleGateway {
    fun register(schedule: PersistentSchedule, nowMillis: Long): PersistentSchedule

    fun replaceWakeSchedules(schedules: List<PersistentSchedule>, nowMillis: Long)

    fun acknowledge(dedupeKey: String, generation: Long, nowMillis: Long): Boolean

    fun cancel(dedupeKey: String, nowMillis: Long): Boolean

    fun reconcile(nowMillis: Long): ReconcileResult

    fun get(dedupeKey: String): PersistentSchedule?
}
