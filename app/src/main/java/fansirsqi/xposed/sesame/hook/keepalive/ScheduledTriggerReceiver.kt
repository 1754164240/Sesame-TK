package fansirsqi.xposed.sesame.hook.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ScheduledTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val applicationContext = context.applicationContext ?: context
        val wakeLock = acquireWakeLock(applicationContext)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MILLIS) {
                    PersistentSchedulerRuntime.moduleService(applicationContext)
                        .onAlarm(System.currentTimeMillis())
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
            } finally {
                releaseWakeLock(wakeLock)
                pendingResult.finish()
            }
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? =
        runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
        }.getOrNull()

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock.release()
        }
    }

    companion object {
        private const val TAG = "ScheduledTriggerReceiver"
        private const val RECEIVER_TIMEOUT_MILLIS = 8_000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 9_000L
        private const val WAKE_LOCK_TAG = "Sesame:PersistentSchedule"
    }
}
