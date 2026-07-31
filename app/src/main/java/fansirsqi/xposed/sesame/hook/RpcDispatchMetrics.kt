package fansirsqi.xposed.sesame.hook

data class RpcDispatchSnapshot(
    val totalRequests: Int,
    val maxInFlight: Int,
    val methodCounts: List<Pair<String, Int>>
)

class RpcDispatchMetrics(
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class DispatchEvent(
        val timestamp: Long,
        val method: String,
        val inFlight: Int
    )

    private val events = ArrayDeque<DispatchEvent>()
    private var currentInFlight = 0

    @Synchronized
    fun onStarted(method: String) {
        currentInFlight++
        events.addLast(
            DispatchEvent(
                timestamp = nowMillis(),
                method = method,
                inFlight = currentInFlight
            )
        )
    }

    @Synchronized
    fun onCompleted() {
        currentInFlight = (currentInFlight - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun snapshot(windowMillis: Long = DEFAULT_WINDOW_MILLIS): RpcDispatchSnapshot {
        val cutoff = nowMillis() - windowMillis.coerceAtLeast(0L)
        while (events.firstOrNull()?.timestamp?.let { it < cutoff } == true) {
            events.removeFirst()
        }
        val counts = events
            .groupingBy(DispatchEvent::method)
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key }
            )
            .take(MAX_LOGGED_METHODS)
            .map { it.key to it.value }
        return RpcDispatchSnapshot(
            totalRequests = events.size,
            maxInFlight = events.maxOfOrNull(DispatchEvent::inFlight) ?: 0,
            methodCounts = counts
        )
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 10_000L
        private const val MAX_LOGGED_METHODS = 5
    }
}
