package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PersistentScheduleServiceTest {

    @Test
    fun registerPersistsAndCoordinatesEarliestTask() {
        val fixture = fixture()

        val registered = fixture.service.register(schedule("global:poll", 5_000L), nowMillis = 1_000L)

        assertEquals(1L, registered.generation)
        assertEquals(registered, fixture.registry.get("global:poll"))
        assertEquals(listOf(5_000L), fixture.alarm.armedTimes)
    }

    @Test
    fun replaceWakeSchedulesCancelsStaleCustomKeys() {
        val fixture = fixture()
        fixture.registry.upsert(
            schedule("global:wakeup:06:50", 5_000L, PersistentScheduleKind.CUSTOM_WAKE),
            0L
        )
        fixture.registry.upsert(
            schedule("global:wakeup:23:50", 6_000L, PersistentScheduleKind.CUSTOM_WAKE),
            0L
        )
        fixture.registry.upsert(schedule("global:poll", 7_000L), 0L)

        fixture.service.replaceWakeSchedules(
            listOf(
                schedule("global:midnight", 10_000L, PersistentScheduleKind.DAILY_MIDNIGHT),
                schedule("global:wakeup:06:50", 11_000L, PersistentScheduleKind.CUSTOM_WAKE)
            ),
            nowMillis = 1_000L
        )

        assertNull(fixture.registry.get("global:wakeup:23:50"))
        assertEquals(11_000L, fixture.registry.get("global:wakeup:06:50")?.triggerAtMillis)
        assertTrue(fixture.registry.get("global:midnight") != null)
        assertTrue(fixture.registry.get("global:poll") != null)
    }

    @Test
    fun acknowledgeCompletesOnlyMatchingGeneration() {
        val fixture = fixture()
        val first = fixture.service.register(schedule("global:poll", 5_000L), 0L)
        val second = fixture.service.register(schedule("global:poll", 6_000L), 1L)

        assertFalse(fixture.service.acknowledge(first.dedupeKey, first.generation, 2L))
        assertTrue(fixture.service.acknowledge(second.dedupeKey, second.generation, 2L))
        assertNull(fixture.registry.get("global:poll"))
    }

    @Test
    fun clockChangeReplansWakeSchedulesWithoutChangingPoll() {
        val fixture = fixture()
        fixture.registry.upsert(
            schedule("global:midnight", 1_000L, PersistentScheduleKind.DAILY_MIDNIGHT),
            0L
        )
        fixture.registry.upsert(
            schedule("global:wakeup:06:50", 2_000L, PersistentScheduleKind.CUSTOM_WAKE),
            0L
        )
        fixture.registry.upsert(schedule("global:poll", 3_000L), 0L)
        val now = Instant.parse("2026-07-29T02:00:00Z").toEpochMilli()

        fixture.service.replanWakeSchedules(
            now,
            PersistentSchedulePlanner(ZoneId.of("Asia/Shanghai"))
        )

        assertEquals(
            Instant.parse("2026-07-29T16:00:00Z").toEpochMilli(),
            fixture.registry.get("global:midnight")?.triggerAtMillis
        )
        assertEquals(
            Instant.parse("2026-07-29T22:50:00Z").toEpochMilli(),
            fixture.registry.get("global:wakeup:06:50")?.triggerAtMillis
        )
        assertEquals(3_000L, fixture.registry.get("global:poll")?.triggerAtMillis)
    }

    private fun fixture(): Fixture {
        val registry = PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())
        val alarm = ServiceAlarmBackend()
        val coordinator = PersistentScheduleCoordinator(
            registry = registry,
            alarmBackend = alarm,
            fallback = object : ScheduleFallback {
                override fun schedule(delayMillis: Long, dedupeKey: String, callback: () -> Unit) = Unit
            },
            dispatcher = object : ScheduleTaskDispatcher {
                override fun dispatch(schedule: PersistentSchedule): ScheduleDispatchResult =
                    ScheduleDispatchResult.ROUTED
            }
        )
        return Fixture(registry, alarm, PersistentScheduleService(registry, coordinator))
    }

    private fun schedule(
        key: String,
        triggerAtMillis: Long,
        kind: PersistentScheduleKind = PersistentScheduleKind.GLOBAL_POLL
    ): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = key,
            kind = kind,
            triggerAtMillis = triggerAtMillis,
            ownerUserId = "owner"
        )

    private data class Fixture(
        val registry: PersistentScheduleRegistry,
        val alarm: ServiceAlarmBackend,
        val service: PersistentScheduleService
    )
}

private class ServiceAlarmBackend : ScheduleAlarmBackend {
    val armedTimes = mutableListOf<Long>()

    override fun arm(triggerAtMillis: Long): Boolean {
        armedTimes.add(triggerAtMillis)
        return true
    }

    override fun cancel(): Boolean = true
}
