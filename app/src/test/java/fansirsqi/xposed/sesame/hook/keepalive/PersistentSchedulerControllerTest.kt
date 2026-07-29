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
