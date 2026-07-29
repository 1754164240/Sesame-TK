package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PersistentSchedulePlannerTest {

    @Test
    fun wakeSchedulesUseStableKeysAndNextOccurrences() {
        val planner = PersistentSchedulePlanner(ZoneId.of("Asia/Shanghai"))
        val now = Instant.parse("2026-07-29T02:00:00Z").toEpochMilli()

        val schedules = planner.wakeSchedules(now, listOf("0650", "23:50"), "owner")

        assertEquals(
            listOf("global:midnight", "global:wakeup:06:50", "global:wakeup:23:50"),
            schedules.map { it.dedupeKey }
        )
        assertEquals(
            Instant.parse("2026-07-29T16:00:00Z").toEpochMilli(),
            schedules.first().triggerAtMillis
        )
        assertTrue(schedules.all { it.ownerUserId == "owner" })
    }

    @Test
    fun disabledWakeListProducesNoPersistentSchedules() {
        val planner = PersistentSchedulePlanner(ZoneId.of("Asia/Shanghai"))

        assertTrue(planner.wakeSchedules(1_000L, listOf("-1"), "owner").isEmpty())
    }

    @Test
    fun pollScheduleUsesStableKeyAndRequestedTime() {
        val planner = PersistentSchedulePlanner(ZoneId.of("Asia/Shanghai"))

        val schedule = planner.globalPoll(5_000L, "owner", allowForegroundLaunch = true)

        assertEquals("global:poll", schedule.dedupeKey)
        assertEquals(5_000L, schedule.triggerAtMillis)
        assertEquals(PersistentScheduleKind.GLOBAL_POLL, schedule.kind)
        assertTrue(PersistentLaunchPolicy.isForegroundLaunchEnabledInPayload(schedule))
    }
}
