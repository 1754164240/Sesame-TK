package fansirsqi.xposed.sesame.hook.keepalive

class PersistentExecutionRequestHandler(
    private val scheduleProvider: (String) -> PersistentSchedule?,
    private val currentOwnerProvider: () -> String?,
    private val requestExecution: (PersistentSchedule) -> Boolean,
    private val acknowledge: (String, Long) -> Boolean
) {
    fun handle(dedupeKey: String?, generation: Long, requestedOwnerUserId: String?): Boolean {
        val key = dedupeKey?.trim().orEmpty()
        if (key.isEmpty() || generation <= 0L) return false

        val schedule = scheduleProvider(key) ?: return false
        if (
            schedule.dedupeKey != key ||
            schedule.generation != generation ||
            schedule.kind == PersistentScheduleKind.UNKNOWN
        ) {
            return false
        }

        val owner = schedule.ownerUserId?.trim().orEmpty()
        if (owner.isNotEmpty()) {
            if (requestedOwnerUserId?.trim() != owner) return false
            if (currentOwnerProvider()?.trim() != owner) return false
        }

        if (!requestExecution(schedule)) return false
        return acknowledge(key, generation)
    }
}
