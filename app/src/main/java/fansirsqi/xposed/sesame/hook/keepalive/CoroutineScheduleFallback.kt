package fansirsqi.xposed.sesame.hook.keepalive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class CoroutineScheduleFallback : ScheduleFallback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    override fun schedule(delayMillis: Long, dedupeKey: String, callback: () -> Unit) {
        jobs.remove(dedupeKey)?.cancel()
        val job = scope.launch {
            delay(delayMillis.coerceAtLeast(0L))
            callback()
        }
        jobs[dedupeKey] = job
        job.invokeOnCompletion {
            jobs.remove(dedupeKey, job)
        }
    }

    override fun cancel(dedupeKey: String) {
        jobs.remove(dedupeKey)?.cancel()
    }
}
