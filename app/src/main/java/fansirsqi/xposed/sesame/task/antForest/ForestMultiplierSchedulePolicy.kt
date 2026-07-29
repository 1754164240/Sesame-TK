package fansirsqi.xposed.sesame.task.antForest

import java.util.Calendar

object ForestMultiplierSchedulePolicy {
    private val pointPattern = Regex("""^\d{4}$""")
    private val rangePattern = Regex("""^\d{4}-\d{4}$""")

    fun scheduledPoints(entries: List<String?>): List<String> {
        return entries
            .mapNotNull(::normalizePoint)
            .distinct()
    }

    fun isRegularUseAllowed(
        nowMillis: Long,
        entries: List<String?>
    ): Boolean {
        val currentMinute = Calendar.getInstance().run {
            timeInMillis = nowMillis
            get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
        }
        return entries.any { entry ->
            val value = entry?.trim().orEmpty()
            if (!isValidRange(value)) {
                return@any false
            }
            val (start, end) = value.split("-", limit = 2)
            val startMinute = toMinuteOfDay(start)
            val endMinute = toMinuteOfDay(end)
            if (startMinute <= endMinute) {
                currentMinute in startMinute..endMinute
            } else {
                currentMinute >= startMinute || currentMinute <= endMinute
            }
        }
    }

    private fun normalizePoint(entry: String?): String? {
        val value = entry?.trim().orEmpty()
        return value.takeIf(::isValidPoint)
    }

    private fun isValidRange(value: String): Boolean {
        if (!rangePattern.matches(value)) {
            return false
        }
        val (start, end) = value.split("-", limit = 2)
        return isValidPoint(start) && isValidPoint(end)
    }

    private fun isValidPoint(value: String): Boolean {
        if (!pointPattern.matches(value)) {
            return false
        }
        val hour = value.substring(0, 2).toInt()
        val minute = value.substring(2, 4).toInt()
        return hour in 0..23 && minute in 0..59
    }

    private fun toMinuteOfDay(value: String): Int {
        return value.substring(0, 2).toInt() * 60 +
            value.substring(2, 4).toInt()
    }
}
