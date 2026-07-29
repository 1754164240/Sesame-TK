package fansirsqi.xposed.sesame.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LogDayTrackerTest {

    private val zoneId = ZoneId.of("GMT+8")
    private val firstDay = time(2026, 7, 28, 23, 59)
    private val nextDay = time(2026, 7, 29, 0, 1)

    @Test
    fun `同日不刷新且跨日成功后只刷新一次`() {
        val tracker = LogDayTracker(firstDay, zoneId)
        var refreshCount = 0

        assertFalse(tracker.refreshIfCrossDay(firstDay + 30_000) {
            refreshCount++
            true
        })
        assertTrue(tracker.refreshIfCrossDay(nextDay) {
            refreshCount++
            true
        })
        assertFalse(tracker.refreshIfCrossDay(nextDay + 60_000) {
            refreshCount++
            true
        })
        assertEquals(1, refreshCount)
    }

    @Test
    fun `跨日刷新失败会在后续写入时重试`() {
        val tracker = LogDayTracker(firstDay, zoneId)
        var refreshCount = 0

        assertFalse(tracker.refreshIfCrossDay(nextDay) {
            refreshCount++
            false
        })
        assertTrue(tracker.refreshIfCrossDay(nextDay + 1_000) {
            refreshCount++
            true
        })
        assertEquals(2, refreshCount)
    }

    private fun time(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
