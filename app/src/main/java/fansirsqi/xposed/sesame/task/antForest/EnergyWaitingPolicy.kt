package fansirsqi.xposed.sesame.task.antForest

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class EnergyWaitingTimeResult {
    VALID,
    EXPIRED,
    TOO_FAR,
    CROSS_DAY
}

object EnergyWaitingTimePolicy {
    const val MAX_WAIT_TIME_MS = 8 * 60 * 60 * 1000L

    fun validate(produceTime: Long, currentTime: Long): EnergyWaitingTimeResult {
        if (produceTime <= currentTime) {
            return EnergyWaitingTimeResult.EXPIRED
        }
        if (produceTime - currentTime > MAX_WAIT_TIME_MS) {
            return EnergyWaitingTimeResult.TOO_FAR
        }
        if (!isSameDay(produceTime, currentTime)) {
            return EnergyWaitingTimeResult.CROSS_DAY
        }
        return EnergyWaitingTimeResult.VALID
    }

    private fun isSameDay(first: Long, second: Long): Boolean {
        val firstCalendar = Calendar.getInstance().apply { timeInMillis = first }
        val secondCalendar = Calendar.getInstance().apply { timeInMillis = second }
        return firstCalendar.get(Calendar.ERA) == secondCalendar.get(Calendar.ERA) &&
            firstCalendar.get(Calendar.YEAR) == secondCalendar.get(Calendar.YEAR) &&
            firstCalendar.get(Calendar.DAY_OF_YEAR) == secondCalendar.get(Calendar.DAY_OF_YEAR)
    }
}

class UniqueTaskRegistry<T> {
    private val tasks = ConcurrentHashMap<String, T>()

    fun register(taskId: String, task: T): Boolean {
        return tasks.putIfAbsent(taskId, task) == null
    }

    fun remove(taskId: String, task: T): Boolean {
        return tasks.remove(taskId, task)
    }

    fun remove(taskId: String): T? = tasks.remove(taskId)
}

enum class WaitingCollectDecision {
    REMOVE_COMPLETE,
    REMOVE_TERMINAL,
    RETRY
}

object EnergyWaitingResultPolicy {
    fun decide(result: CollectResult): WaitingCollectDecision {
        if (result.success && result.energyCount > 0) {
            return WaitingCollectDecision.REMOVE_COMPLETE
        }
        if (
            result.hasShield ||
            result.hasBomb ||
            result.message.contains("用户无可收取的能量球") ||
            result.message.contains("无法查询用户能量信息")
        ) {
            return WaitingCollectDecision.REMOVE_TERMINAL
        }
        return WaitingCollectDecision.RETRY
    }
}

class LatestSnapshotQueue<T> {
    private val latest = AtomicReference<T?>(null)
    private val writerActive = AtomicBoolean(false)

    fun submit(snapshot: T): Boolean {
        latest.set(snapshot)
        return writerActive.compareAndSet(false, true)
    }

    fun takeLatest(): T? = latest.getAndSet(null)

    fun finish(): Boolean {
        writerActive.set(false)
        return latest.get() != null && writerActive.compareAndSet(false, true)
    }
}
