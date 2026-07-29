package fansirsqi.xposed.sesame.hook.keepalive

class PersistentScheduleService(
    private val registry: PersistentScheduleRegistry,
    private val coordinator: PersistentScheduleCoordinator
) : PersistentScheduleGateway {
    override fun register(schedule: PersistentSchedule, nowMillis: Long): PersistentSchedule {
        val registered = registry.upsert(schedule, nowMillis)
        coordinator.reconcile(nowMillis)
        return registered
    }

    override fun replaceWakeSchedules(schedules: List<PersistentSchedule>, nowMillis: Long) {
        val replacementKeys = schedules
            .filter { it.kind.isWakeSchedule() }
            .mapTo(linkedSetOf()) { it.dedupeKey.trim() }

        registry.all()
            .filter { it.kind.isWakeSchedule() && it.dedupeKey !in replacementKeys }
            .forEach { registry.cancel(it.dedupeKey) }

        schedules
            .filter { it.kind.isWakeSchedule() }
            .forEach { registry.upsert(it, nowMillis) }

        coordinator.reconcile(nowMillis)
    }

    override fun acknowledge(dedupeKey: String, generation: Long, nowMillis: Long): Boolean {
        val completed = registry.complete(dedupeKey, generation)
        coordinator.reconcile(nowMillis)
        return completed
    }

    override fun cancel(dedupeKey: String, nowMillis: Long): Boolean {
        val cancelled = registry.cancel(dedupeKey)
        coordinator.reconcile(nowMillis)
        return cancelled
    }

    fun onAlarm(nowMillis: Long): DispatchBatchResult = coordinator.onAlarm(nowMillis)

    fun replanWakeSchedules(
        nowMillis: Long,
        planner: PersistentSchedulePlanner = PersistentSchedulePlanner()
    ): ReconcileResult {
        registry.all().forEach { existing ->
            val replacement = when (existing.kind) {
                PersistentScheduleKind.DAILY_MIDNIGHT ->
                    planner.wakeSchedules(
                        nowMillis,
                        emptyList(),
                        existing.ownerUserId,
                        PersistentLaunchPolicy.isForegroundLaunchEnabledInPayload(existing)
                    ).firstOrNull()

                PersistentScheduleKind.CUSTOM_WAKE -> {
                    val rawTime = existing.dedupeKey
                        .takeIf { it.startsWith(PersistentScheduleKey.CUSTOM_WAKE_PREFIX) }
                        ?.removePrefix(PersistentScheduleKey.CUSTOM_WAKE_PREFIX)
                    rawTime?.let {
                        planner.wakeSchedules(
                            nowMillis,
                            listOf(it),
                            existing.ownerUserId,
                            PersistentLaunchPolicy.isForegroundLaunchEnabledInPayload(existing)
                        ).firstOrNull { schedule ->
                            schedule.kind == PersistentScheduleKind.CUSTOM_WAKE
                        }
                    }
                }

                else -> null
            }
            replacement?.let {
                registry.upsert(
                    it.copy(
                        payloadJson = existing.payloadJson,
                        ownerUserId = existing.ownerUserId
                    ),
                    nowMillis
                )
            }
        }
        return coordinator.forceReconcile(nowMillis)
    }

    fun forceReconcile(nowMillis: Long): ReconcileResult =
        coordinator.forceReconcile(nowMillis)

    override fun reconcile(nowMillis: Long): ReconcileResult = coordinator.reconcile(nowMillis)

    override fun get(dedupeKey: String): PersistentSchedule? = registry.get(dedupeKey)

    private fun PersistentScheduleKind.isWakeSchedule(): Boolean =
        this == PersistentScheduleKind.DAILY_MIDNIGHT ||
            this == PersistentScheduleKind.CUSTOM_WAKE
}
