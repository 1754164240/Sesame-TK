package fansirsqi.xposed.sesame.hook.rpc.intervallimit

import java.util.concurrent.ConcurrentHashMap

class RpcIntervalLimiter(
    private val globalLimit: IntervalLimit,
    private val nowMillis: () -> Long,
    private val sleepMillis: (Long) -> Unit
) {
    private val methodLimits = ConcurrentHashMap<String, IntervalLimit>()

    fun put(method: String, limit: IntervalLimit) {
        methodLimits[method] = limit
    }

    fun putIfAbsent(method: String, limit: IntervalLimit): Boolean =
        methodLimits.putIfAbsent(method, limit) == null

    fun clear() {
        methodLimits.clear()
        synchronized(globalLimit) {
            globalLimit.time = 0L
        }
    }

    fun enter(method: String) {
        synchronized(globalLimit) {
            waitFor(globalLimit, updateTime = false)
            methodLimits[method]?.let { methodLimit ->
                synchronized(methodLimit) {
                    waitFor(methodLimit, updateTime = true)
                }
            }
            globalLimit.time = nowMillis()
        }
    }

    private fun waitFor(limit: IntervalLimit, updateTime: Boolean) {
        val interval = limit.interval ?: 0
        val remaining = interval - (nowMillis() - limit.time)
        if (remaining > 0L) {
            sleepMillis(remaining)
        }
        if (updateTime) {
            limit.time = nowMillis()
        }
    }
}
