package fansirsqi.xposed.sesame.hook.keepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

interface ExactAlarmAccess {
    fun canScheduleExactAlarms(): Boolean

    fun armExact(triggerAtMillis: Long)

    fun cancelExact()
}

class SystemAlarmBackend(
    private val access: ExactAlarmAccess
) : ScheduleAlarmBackend {
    constructor(context: Context) : this(AndroidExactAlarmAccess(context))

    override fun arm(triggerAtMillis: Long): Boolean =
        runCatching {
            if (!access.canScheduleExactAlarms()) return false
            access.armExact(triggerAtMillis)
            true
        }.getOrDefault(false)

    override fun cancel(): Boolean =
        runCatching {
            access.cancelExact()
            true
        }.getOrDefault(false)
}

private class AndroidExactAlarmAccess(context: Context) : ExactAlarmAccess {
    private val applicationContext = context.applicationContext ?: context
    private val alarmManager =
        applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

    override fun armExact(triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent()
        )
    }

    override fun cancelExact() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            Intent()
                .setClassName(applicationContext, SCHEDULED_TRIGGER_RECEIVER)
                .setAction(ACTION_PERSISTENT_SCHEDULE_TRIGGER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val REQUEST_CODE = 24_071
        private const val ACTION_PERSISTENT_SCHEDULE_TRIGGER =
            "fansirsqi.xposed.sesame.action.PERSISTENT_SCHEDULE_TRIGGER"
        private const val SCHEDULED_TRIGGER_RECEIVER =
            "fansirsqi.xposed.sesame.hook.keepalive.ScheduledTriggerReceiver"
    }
}
