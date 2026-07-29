package fansirsqi.xposed.sesame.hook.keepalive

class PersistentReceiverWorker(
    private val onAlarm: (Long) -> Unit,
    private val onReconcile: (Long) -> Unit
) {
    fun handleAlarm(nowMillis: Long) {
        onAlarm(nowMillis)
    }

    fun handleReconcile(action: String?, nowMillis: Long): Boolean {
        if (!ScheduleReconcilePolicy.shouldReconcile(action)) return false
        onReconcile(nowMillis)
        return true
    }
}
