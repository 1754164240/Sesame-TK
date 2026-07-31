package fansirsqi.xposed.sesame.hook.keepalive

import java.util.Locale

object PersistentScheduleKey {
    const val GLOBAL_POLL = "global:poll"
    const val DAILY_MIDNIGHT = "global:midnight"
    const val CUSTOM_WAKE_PREFIX = "global:wakeup:"
    private const val VERIFICATION_PROBE_PREFIX = "verification:probe:"

    @JvmStatic
    fun verificationProbe(ownerUserId: String?, generation: Long): String {
        val owner = ownerUserId?.trim().orEmpty().ifEmpty { "unknown" }
        return "$VERIFICATION_PROBE_PREFIX$owner:$generation"
    }

    @JvmStatic
    fun customWake(rawTime: String?): String? {
        val digits = rawTime?.trim()?.replace(":", "").orEmpty()
        if (digits.length != 4 || digits.any { !it.isDigit() }) return null
        val hour = digits.substring(0, 2).toInt()
        val minute = digits.substring(2, 4).toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return CUSTOM_WAKE_PREFIX + String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    }
}
