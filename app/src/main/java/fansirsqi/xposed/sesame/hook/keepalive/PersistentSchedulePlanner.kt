package fansirsqi.xposed.sesame.hook.keepalive

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PersistentSchedulePlanner(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun globalPoll(
        triggerAtMillis: Long,
        ownerUserId: String?,
        allowForegroundLaunch: Boolean = false
    ): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = PersistentScheduleKey.GLOBAL_POLL,
            kind = PersistentScheduleKind.GLOBAL_POLL,
            triggerAtMillis = triggerAtMillis,
            payloadJson = launchPayload(allowForegroundLaunch),
            ownerUserId = ownerUserId
        )

    fun wakeSchedules(
        nowMillis: Long,
        rawTimes: Collection<String>?,
        ownerUserId: String?,
        allowForegroundLaunch: Boolean = false
    ): List<PersistentSchedule> {
        if (rawTimes?.contains(DISABLED_VALUE) == true) return emptyList()

        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val result = mutableListOf(
            PersistentSchedule(
                dedupeKey = PersistentScheduleKey.DAILY_MIDNIGHT,
                kind = PersistentScheduleKind.DAILY_MIDNIGHT,
                triggerAtMillis = nextOccurrence(nowMillis, LocalTime.MIDNIGHT),
                payloadJson = launchPayload(allowForegroundLaunch),
                ownerUserId = ownerUserId
            )
        )
        val seenKeys = linkedSetOf<String>()

        rawTimes.orEmpty().forEach { rawTime ->
            val key = PersistentScheduleKey.customWake(rawTime) ?: return@forEach
            if (!seenKeys.add(key)) return@forEach
            val time = LocalTime.parse(key.removePrefix(PersistentScheduleKey.CUSTOM_WAKE_PREFIX))
            result.add(
                PersistentSchedule(
                    dedupeKey = key,
                    kind = PersistentScheduleKind.CUSTOM_WAKE,
                    triggerAtMillis = nextOccurrence(now.toInstant().toEpochMilli(), time),
                    payloadJson = launchPayload(allowForegroundLaunch),
                    ownerUserId = ownerUserId
                )
            )
        }

        return result
    }

    fun verificationProbe(
        triggerAtMillis: Long,
        ownerUserId: String?,
        verificationGeneration: Long,
        attempt: Int,
        allowForegroundLaunch: Boolean
    ): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = PersistentScheduleKey.verificationProbe(
                ownerUserId,
                verificationGeneration
            ),
            kind = PersistentScheduleKind.VERIFICATION_PROBE,
            triggerAtMillis = triggerAtMillis,
            payloadJson = JSONObject()
                .put("launchTarget", true)
                .put("allowPersistentForegroundLaunch", allowForegroundLaunch)
                .put("verificationGeneration", verificationGeneration)
                .put("attempt", attempt)
                .toString(),
            ownerUserId = ownerUserId
        )

    private fun nextOccurrence(nowMillis: Long, time: LocalTime): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        var candidate = LocalDateTime.of(now.toLocalDate(), time).atZone(zoneId)
        if (!candidate.isAfter(now)) {
            candidate = LocalDateTime.of(now.toLocalDate().plusDays(1), time).atZone(zoneId)
        }
        return candidate.toInstant().toEpochMilli()
    }

    private fun launchPayload(allowForegroundLaunch: Boolean): String =
        if (allowForegroundLaunch) {
            """{"launchTarget":true,"allowPersistentForegroundLaunch":true}"""
        } else {
            """{"launchTarget":true,"allowPersistentForegroundLaunch":false}"""
        }

    companion object {
        private const val DISABLED_VALUE = "-1"
    }
}
