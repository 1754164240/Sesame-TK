package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class RpcDispatchMetricsTest {

    @Test
    fun `快照只统计最近十秒的请求和最大在途数`() {
        var now = 1_000L
        val metrics = RpcDispatchMetrics(nowMillis = { now })

        metrics.onStarted("old.method")
        metrics.onCompleted()
        now = 12_000L
        metrics.onStarted("forest.query")
        metrics.onStarted("sports.query")
        metrics.onCompleted()

        val snapshot = metrics.snapshot(windowMillis = 10_000L)

        assertEquals(2, snapshot.totalRequests)
        assertEquals(2, snapshot.maxInFlight)
        assertEquals(
            listOf("forest.query" to 1, "sports.query" to 1),
            snapshot.methodCounts
        )
    }
}
