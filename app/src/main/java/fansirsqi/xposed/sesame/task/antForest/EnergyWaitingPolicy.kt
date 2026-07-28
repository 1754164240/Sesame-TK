package fansirsqi.xposed.sesame.task.antForest

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    private var latest: T? = null
    private var writerActive = false
    private var batchDepth = 0

    @Synchronized
    fun beginBatch() {
        batchDepth++
    }

    @Synchronized
    fun endBatch(): Boolean {
        if (batchDepth > 0) {
            batchDepth--
        }
        if (batchDepth > 0 || latest == null || writerActive) {
            return false
        }
        writerActive = true
        return true
    }

    @Synchronized
    fun submit(snapshot: T): Boolean {
        latest = snapshot
        if (batchDepth > 0 || writerActive) {
            return false
        }
        writerActive = true
        return true
    }

    @Synchronized
    fun peekLatest(): T? = latest

    @Synchronized
    fun takeLatest(): T? {
        if (batchDepth > 0) {
            return null
        }
        return latest.also { latest = null }
    }

    @Synchronized
    fun finish(): Boolean {
        writerActive = false
        if (batchDepth > 0 || latest == null) {
            return false
        }
        writerActive = true
        return true
    }
}

class WaitingTimeAnomalySummary {
    private val counts = ConcurrentHashMap<EnergyWaitingTimeResult, AtomicInteger>()

    fun record(result: EnergyWaitingTimeResult) {
        if (result != EnergyWaitingTimeResult.VALID) {
            counts.computeIfAbsent(result) { AtomicInteger() }.incrementAndGet()
        }
    }

    fun snapshot(): Map<EnergyWaitingTimeResult, Int> {
        return counts.mapValues { it.value.get() }
    }

    fun describe(): String {
        val names = mapOf(
            EnergyWaitingTimeResult.EXPIRED to "已过期",
            EnergyWaitingTimeResult.TOO_FAR to "超远未来",
            EnergyWaitingTimeResult.CROSS_DAY to "跨日异常"
        )
        return names.entries.mapNotNull { (result, name) ->
            counts[result]?.get()?.takeIf { it > 0 }?.let { "$name${it}个" }
        }.joinToString("，")
    }
}
