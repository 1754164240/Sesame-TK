package fansirsqi.xposed.sesame.hook

import android.content.Context
import androidx.core.content.edit

data class VerificationRecoverySnapshot(
    val generation: Long,
    val attemptCount: Int,
    val ownerUserId: String,
    val triggeredAtMillis: Long,
    val method: String,
    val context: RpcRequestContext?
) {
    fun restart(newGeneration: Long): VerificationRecoverySnapshot =
        copy(generation = newGeneration, attemptCount = 0)
}

class VerificationRecoveryStateStore(
    context: Context,
    ownerUserId: String = ""
) {
    private val preferences = (context.applicationContext ?: context)
        .getSharedPreferences(
            "$PREFERENCES_NAME:${ownerUserId.hashCode()}",
            Context.MODE_PRIVATE
        )

    fun load(): VerificationRecoverySnapshot? {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            return null
        }
        val generation = preferences.getLong(KEY_GENERATION, 0L)
        if (generation <= 0L) {
            return null
        }
        val traceId = preferences.getString(KEY_TRACE_ID, null)
        val source = preferences.getString(KEY_SOURCE, null)
        val stage = preferences.getString(KEY_STAGE, null)
        val context = if (traceId != null && source != null && stage != null) {
            RpcRequestContext(
                traceId = traceId,
                source = source,
                stage = stage,
                taskName = preferences.getString(KEY_TASK_NAME, null),
                configId = preferences.getString(KEY_CONFIG_ID, null),
                taskId = preferences.getString(KEY_TASK_ID, null),
                targetBusiness = preferences.getString(KEY_TARGET_BUSINESS, null)
            )
        } else {
            null
        }
        return VerificationRecoverySnapshot(
            generation = generation,
            attemptCount = preferences.getInt(KEY_ATTEMPT_COUNT, 0),
            ownerUserId = preferences.getString(KEY_OWNER_USER_ID, "").orEmpty(),
            triggeredAtMillis = preferences.getLong(KEY_TRIGGERED_AT_MILLIS, 0L),
            method = preferences.getString(KEY_METHOD, "").orEmpty(),
            context = context
        )
    }

    fun save(snapshot: VerificationRecoverySnapshot) {
        preferences.edit(commit = true) {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_GENERATION, snapshot.generation)
            putInt(KEY_ATTEMPT_COUNT, snapshot.attemptCount)
            putString(KEY_OWNER_USER_ID, snapshot.ownerUserId)
            putLong(KEY_TRIGGERED_AT_MILLIS, snapshot.triggeredAtMillis)
            putString(KEY_METHOD, snapshot.method)
            putString(KEY_TRACE_ID, snapshot.context?.traceId)
            putString(KEY_SOURCE, snapshot.context?.source)
            putString(KEY_STAGE, snapshot.context?.stage)
            putString(KEY_TASK_NAME, snapshot.context?.taskName)
            putString(KEY_CONFIG_ID, snapshot.context?.configId)
            putString(KEY_TASK_ID, snapshot.context?.taskId)
            putString(KEY_TARGET_BUSINESS, snapshot.context?.targetBusiness)
        }
    }

    fun updateAttempt(generation: Long, attemptCount: Int) {
        if (
            preferences.getBoolean(KEY_ACTIVE, false) &&
            preferences.getLong(KEY_GENERATION, 0L) == generation
        ) {
            preferences.edit(commit = true) {
                putInt(KEY_ATTEMPT_COUNT, attemptCount)
            }
        }
    }

    fun clear() {
        preferences.edit(commit = true) { clear() }
    }

    companion object {
        private const val PREFERENCES_NAME = "sesame_verification_recovery"
        private const val KEY_ACTIVE = "active"
        private const val KEY_GENERATION = "generation"
        private const val KEY_ATTEMPT_COUNT = "attemptCount"
        private const val KEY_OWNER_USER_ID = "ownerUserId"
        private const val KEY_TRIGGERED_AT_MILLIS = "triggeredAtMillis"
        private const val KEY_METHOD = "method"
        private const val KEY_TRACE_ID = "traceId"
        private const val KEY_SOURCE = "source"
        private const val KEY_STAGE = "stage"
        private const val KEY_TASK_NAME = "taskName"
        private const val KEY_CONFIG_ID = "configId"
        private const val KEY_TASK_ID = "taskId"
        private const val KEY_TARGET_BUSINESS = "targetBusiness"
    }
}
