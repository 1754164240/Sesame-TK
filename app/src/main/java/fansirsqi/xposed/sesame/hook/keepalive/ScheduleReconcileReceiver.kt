package fansirsqi.xposed.sesame.hook.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ScheduleReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (!ScheduleReconcilePolicy.shouldReconcile(action)) {
            if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                Log.record(TAG, "锁屏启动阶段跳过凭据存储调度恢复")
            }
            return
        }

        val pendingResult = goAsync()
        val applicationContext = context.applicationContext ?: context
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MILLIS) {
                    val service = PersistentSchedulerRuntime.moduleService(applicationContext)
                    val nowMillis = System.currentTimeMillis()
                    if (
                        action == Intent.ACTION_TIME_CHANGED ||
                        action == Intent.ACTION_TIMEZONE_CHANGED
                    ) {
                        service.replanWakeSchedules(nowMillis)
                    } else {
                        service.forceReconcile(nowMillis)
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleReconcileReceiver"
        private const val RECEIVER_TIMEOUT_MILLIS = 8_000L
    }
}
