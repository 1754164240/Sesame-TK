package fansirsqi.xposed.sesame.hook.keepalive

object ScheduleReconcilePolicy {
    private val supportedActions = setOf(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    )

    @JvmStatic
    fun shouldReconcile(action: String?): Boolean = action in supportedActions
}
