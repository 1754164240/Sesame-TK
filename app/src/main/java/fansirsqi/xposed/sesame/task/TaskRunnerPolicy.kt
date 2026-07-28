package fansirsqi.xposed.sesame.task

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

enum class RunnerExecutionPolicy {
    AWAIT_COMPLETION,
    START_ONLY
}

enum class TaskRunOutcome {
    COMPLETED,
    STARTED_BACKGROUND,
    TIMED_OUT,
    SKIPPED_OFFLINE,
    SKIPPED_FILTERED,
    SKIPPED_RUNNING,
    FAILED
}

data class TaskRunSnapshot(
    val completed: Int,
    val startedBackground: Int,
    val timedOut: Int,
    val skipped: Int,
    val failed: Int
)

class TaskRunCounter {
    private val completed = AtomicInteger(0)
    private val startedBackground = AtomicInteger(0)
    private val timedOut = AtomicInteger(0)
    private val skipped = AtomicInteger(0)
    private val failed = AtomicInteger(0)

    fun record(outcome: TaskRunOutcome) {
        when (outcome) {
            TaskRunOutcome.COMPLETED -> completed.incrementAndGet()
            TaskRunOutcome.STARTED_BACKGROUND -> startedBackground.incrementAndGet()
            TaskRunOutcome.TIMED_OUT -> timedOut.incrementAndGet()
            TaskRunOutcome.SKIPPED_OFFLINE,
            TaskRunOutcome.SKIPPED_FILTERED,
            TaskRunOutcome.SKIPPED_RUNNING -> skipped.incrementAndGet()
            TaskRunOutcome.FAILED -> failed.incrementAndGet()
        }
    }

    fun snapshot(): TaskRunSnapshot {
        return TaskRunSnapshot(
            completed = completed.get(),
            startedBackground = startedBackground.get(),
            timedOut = timedOut.get(),
            skipped = skipped.get(),
            failed = failed.get()
        )
    }

    fun reset() {
        completed.set(0)
        startedBackground.set(0)
        timedOut.set(0)
        skipped.set(0)
        failed.set(0)
    }
}

class TaskExecutionGate {
    private val running = AtomicBoolean(false)

    fun tryAcquire(): Boolean = running.compareAndSet(false, true)

    fun release() {
        running.set(false)
    }
}

object TaskRunnerPolicy {
    fun shouldStart(isOffline: Boolean, isManualRunning: Boolean): Boolean {
        return !isOffline && !isManualRunning
    }

    fun shouldScheduleNext(isActive: Boolean): Boolean = isActive
}
