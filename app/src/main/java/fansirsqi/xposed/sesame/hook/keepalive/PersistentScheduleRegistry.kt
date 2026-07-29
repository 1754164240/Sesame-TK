package fansirsqi.xposed.sesame.hook.keepalive

class PersistentScheduleRegistry(
    private val storage: PersistentScheduleStorage
) {
    private val schedules = linkedMapOf<String, PersistentSchedule>()

    @Volatile
    private var unsavedChanges = false

    init {
        storage.load().forEach { schedule ->
            val key = schedule.dedupeKey.trim()
            if (key.isNotEmpty()) {
                schedules[key] = schedule.copy(dedupeKey = key)
            }
        }
    }

    @Synchronized
    fun upsert(schedule: PersistentSchedule, nowMillis: Long): PersistentSchedule {
        val key = schedule.dedupeKey.trim()
        require(key.isNotEmpty()) { "dedupeKey 不能为空" }
        val current = schedules[key]
        val updated = schedule.copy(
            dedupeKey = key,
            state = PersistentScheduleState.PENDING,
            generation = (current?.generation ?: 0L) + 1L,
            leaseUntilMillis = 0L,
            updatedAtMillis = nowMillis
        )
        schedules[key] = updated
        persist()
        return updated.copy()
    }

    @Synchronized
    fun claimDue(
        nowMillis: Long,
        windowMillis: Long,
        leaseMillis: Long
    ): List<PersistentSchedule> {
        val deadline = nowMillis + windowMillis.coerceAtLeast(0L)
        val due = schedules.values
            .filter {
                it.state == PersistentScheduleState.PENDING &&
                    it.triggerAtMillis <= deadline
            }
            .sortedWith(compareBy<PersistentSchedule> { it.triggerAtMillis }.thenBy { it.dedupeKey })

        due.forEach { schedule ->
            val claimed = schedule.copy(
                state = PersistentScheduleState.CLAIMED,
                leaseUntilMillis = nowMillis + leaseMillis.coerceAtLeast(0L),
                updatedAtMillis = nowMillis
            )
            schedules[schedule.dedupeKey] = claimed
        }
        if (due.isNotEmpty()) persist()
        return due.map { schedules.getValue(it.dedupeKey).copy() }
    }

    @Synchronized
    fun recoverExpiredClaims(nowMillis: Long): Int {
        var recovered = 0
        schedules.values.toList().forEach { schedule ->
            if (
                schedule.state == PersistentScheduleState.CLAIMED &&
                schedule.leaseUntilMillis <= nowMillis
            ) {
                schedules[schedule.dedupeKey] = schedule.copy(
                    state = PersistentScheduleState.PENDING,
                    leaseUntilMillis = 0L,
                    updatedAtMillis = nowMillis
                )
                recovered++
            }
        }
        if (recovered > 0) persist()
        return recovered
    }

    @Synchronized
    fun complete(dedupeKey: String, generation: Long): Boolean {
        val key = dedupeKey.trim()
        val current = schedules[key] ?: return false
        if (current.generation != generation) return false
        schedules.remove(key)
        persist()
        return true
    }

    @Synchronized
    fun retry(
        dedupeKey: String,
        generation: Long,
        triggerAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        val key = dedupeKey.trim()
        val current = schedules[key] ?: return false
        if (current.generation != generation) return false
        schedules[key] = current.copy(
            state = PersistentScheduleState.PENDING,
            triggerAtMillis = triggerAtMillis,
            leaseUntilMillis = 0L,
            updatedAtMillis = nowMillis
        )
        persist()
        return true
    }

    @Synchronized
    fun cancel(dedupeKey: String): Boolean {
        val removed = schedules.remove(dedupeKey.trim()) ?: return false
        persist()
        return removed.dedupeKey.isNotEmpty()
    }

    @Synchronized
    fun nextPending(): PersistentSchedule? =
        schedules.values
            .asSequence()
            .filter { it.state == PersistentScheduleState.PENDING }
            .minWithOrNull(compareBy<PersistentSchedule> { it.triggerAtMillis }.thenBy { it.dedupeKey })
            ?.copy()

    @Synchronized
    fun get(dedupeKey: String): PersistentSchedule? = schedules[dedupeKey.trim()]?.copy()

    @Synchronized
    fun all(): List<PersistentSchedule> = schedules.values.map { it.copy() }

    fun hasUnsavedChanges(): Boolean = unsavedChanges

    @Synchronized
    fun flush(): Boolean {
        persist()
        return !unsavedChanges
    }

    private fun persist() {
        unsavedChanges = !storage.save(schedules.values.map { it.copy() })
    }
}
