package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PersistentSchedulerControllerTest {

    @Test
    fun enabledPollUsesPersistentRegistryOnly() {
        val fixture = fixture(enabled = true)
        var legacyCalls = 0

        fixture.controller.schedulePoll(5_000L, "owner") { legacyCalls++ }

        assertEquals(0, legacyCalls)
        assertEquals(5_000L, fixture.registry.get("global:poll")?.triggerAtMillis)
    }

    @Test
    fun disabledPollClearsPersistentRecordAndUsesLegacyScheduler() {
        val fixture = fixture(enabled = false)
        fixture.registry.upsert(
            PersistentSchedule(
                dedupeKey = "global:poll",
                triggerAtMillis = 4_000L,
                ownerUserId = "owner"
            ),
            0L
        )
        var legacyCalls = 0

        fixture.controller.schedulePoll(5_000L, "owner") { legacyCalls++ }

        assertEquals(1, legacyCalls)
        assertNull(fixture.registry.get("global:poll"))
    }

    @Test
    fun disabledExecutionConfigurationCancelsExistingPoll() {
        val fixture = fixture(enabled = true)
        fixture.registry.upsert(
            PersistentSchedule(
                dedupeKey = "global:poll",
                triggerAtMillis = 4_000L,
                ownerUserId = "owner"
            ),
            0L
        )

        fixture.controller.cancelPoll()

        assertNull(fixture.registry.get("global:poll"))
    }

    @Test
    fun disablingWakeSchedulesClearsPersistentWakeRecordsAndUsesLegacyScheduler() {
        val fixture = fixture(enabled = false)
        fixture.registry.upsert(
            PersistentSchedule(
                dedupeKey = "global:midnight",
                kind = PersistentScheduleKind.DAILY_MIDNIGHT,
                triggerAtMillis = 4_000L,
                ownerUserId = "owner"
            ),
            0L
        )
        var legacyCalls = 0

        fixture.controller.replaceWakeSchedules(1_000L, listOf("06:50"), "owner") {
            legacyCalls++
        }

        assertEquals(1, legacyCalls)
        assertTrue(fixture.registry.all().isEmpty())
    }

    @Test
    fun disabledPersistentSchedulerClearsProbeAndUsesProcessScheduler() {
        val fixture = fixture(enabled = false)
        fixture.registry.upsert(
            PersistentSchedule(
                dedupeKey = "verification:probe:owner:3",
                kind = PersistentScheduleKind.VERIFICATION_PROBE,
                triggerAtMillis = 4_000L,
                ownerUserId = "owner"
            ),
            0L
        )
        var legacyCalls = 0

        fixture.controller.scheduleVerificationProbe(
            triggerAtMillis = 5_000L,
            ownerUserId = "owner",
            verificationGeneration = 3L,
            attempt = 2
        ) {
            legacyCalls++
        }

        assertEquals(1, legacyCalls)
        assertNull(fixture.registry.get("verification:probe:owner:3"))
    }

    @Test
    fun `持久服务不可用时所有调度入口立即回退进程内调度`() {
        val gateway = UnavailablePersistentScheduleGateway()
        val controller = PersistentSchedulerController(
            service = gateway,
            enabled = { true },
            nowProvider = { 1_000L }
        )
        var legacyPollCalls = 0
        var legacyWakeCalls = 0
        var legacyProbeCalls = 0

        controller.schedulePoll(5_000L, "owner") { legacyPollCalls++ }
        controller.replaceWakeSchedules(1_000L, listOf("06:50"), "owner") {
            legacyWakeCalls++
        }
        controller.scheduleVerificationProbe(
            triggerAtMillis = 5_000L,
            ownerUserId = "owner",
            verificationGeneration = 3L,
            attempt = 1
        ) {
            legacyProbeCalls++
        }

        assertEquals(1, legacyPollCalls)
        assertEquals(1, legacyWakeCalls)
        assertEquals(1, legacyProbeCalls)
        assertEquals(3, gateway.remoteCalls)
    }

    private fun fixture(enabled: Boolean): Fixture {
        val registry = PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())
        val coordinator = PersistentScheduleCoordinator(
            registry,
            object : ScheduleAlarmBackend {
                override fun arm(triggerAtMillis: Long): Boolean = true

                override fun cancel(): Boolean = true
            },
            object : ScheduleFallback {
                override fun schedule(delayMillis: Long, dedupeKey: String, callback: () -> Unit) = Unit
            },
            object : ScheduleTaskDispatcher {
                override fun dispatch(schedule: PersistentSchedule): ScheduleDispatchResult =
                    ScheduleDispatchResult.DEFERRED
            }
        )
        val service = PersistentScheduleService(registry, coordinator)
        val controller = PersistentSchedulerController(
            service = service,
            planner = PersistentSchedulePlanner(ZoneId.of("Asia/Shanghai")),
            enabled = { enabled },
            nowProvider = { 1_000L }
        )
        return Fixture(registry, controller)
    }

    private data class Fixture(
        val registry: PersistentScheduleRegistry,
        val controller: PersistentSchedulerController
    )
}

private class UnavailablePersistentScheduleGateway : PersistentScheduleGateway {
    var remoteCalls = 0

    private fun unavailable(): Nothing {
        remoteCalls++
        throw PersistentScheduleUnavailableException("测试持久服务不可用")
    }

    override fun register(
        schedule: PersistentSchedule,
        nowMillis: Long
    ): PersistentSchedule = unavailable()

    override fun replaceWakeSchedules(
        schedules: List<PersistentSchedule>,
        nowMillis: Long
    ): Unit = unavailable()

    override fun acknowledge(
        dedupeKey: String,
        generation: Long,
        nowMillis: Long
    ): Boolean = unavailable()

    override fun cancel(dedupeKey: String, nowMillis: Long): Boolean = unavailable()

    override fun reconcile(nowMillis: Long): ReconcileResult = unavailable()

    override fun get(dedupeKey: String): PersistentSchedule? = unavailable()
}
