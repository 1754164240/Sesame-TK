package fansirsqi.xposed.sesame.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogDayTracker(
    initialMillis: Long = System.currentTimeMillis(),
    private val zoneId: ZoneId = ZoneId.of("GMT+8")
) {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    private var appliedDay = dayKey(initialMillis)

    @Synchronized
    fun refreshIfCrossDay(
        nowMillis: Long = System.currentTimeMillis(),
        refresh: () -> Boolean
    ): Boolean {
        val currentDay = dayKey(nowMillis)
        if (currentDay == appliedDay) {
            return false
        }
        if (!refresh()) {
            return false
        }
        appliedDay = currentDay
        return true
    }

    private fun dayKey(timeMillis: Long): String {
        return formatter.format(Instant.ofEpochMilli(timeMillis).atZone(zoneId))
    }
}
