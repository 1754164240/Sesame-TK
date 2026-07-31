package fansirsqi.xposed.sesame.hook.keepalive

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.io.Serializable

enum class PersistentScheduleKind {
    GLOBAL_POLL,
    DAILY_MIDNIGHT,
    CUSTOM_WAKE,
    VERIFICATION_PROBE,
    UNKNOWN
}

enum class PersistentScheduleState {
    PENDING,
    CLAIMED
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersistentSchedule(
    var dedupeKey: String = "",
    var kind: PersistentScheduleKind = PersistentScheduleKind.GLOBAL_POLL,
    var triggerAtMillis: Long = 0L,
    var payloadJson: String = "{}",
    var ownerUserId: String? = null,
    var state: PersistentScheduleState = PersistentScheduleState.PENDING,
    var generation: Long = 0L,
    var leaseUntilMillis: Long = 0L,
    var updatedAtMillis: Long = 0L
) : Serializable

enum class ScheduleDispatchResult {
    ROUTED,
    DEFERRED,
    RETRY,
    SKIPPED_ACCOUNT,
    UNSUPPORTED
}
