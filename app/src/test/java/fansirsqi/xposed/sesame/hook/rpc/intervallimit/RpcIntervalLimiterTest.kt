package fansirsqi.xposed.sesame.hook.rpc.intervallimit

import org.junit.Assert.assertEquals
import org.junit.Test

class RpcIntervalLimiterTest {

    @Test
    fun `等待结束后使用实际放行时间更新时间戳`() {
        var now = 1_000L
        val sleeps = mutableListOf<Long>()
        val global = DefaultIntervalLimit(500).apply { time = 800L }
        val limiter = RpcIntervalLimiter(
            globalLimit = global,
            nowMillis = { now },
            sleepMillis = {
                sleeps += it
                now += it
            }
        )

        limiter.enter("method")

        assertEquals(listOf(300L), sleeps)
        assertEquals(1_300L, global.time)
    }

    @Test
    fun `方法专用间隔和全局间隔同时生效且最终更新时间一致`() {
        var now = 1_000L
        val sleeps = mutableListOf<Long>()
        val global = DefaultIntervalLimit(500).apply { time = 900L }
        val method = DefaultIntervalLimit(700).apply { time = 1_200L }
        val limiter = RpcIntervalLimiter(
            globalLimit = global,
            nowMillis = { now },
            sleepMillis = {
                sleeps += it
                now += it
            }
        )
        limiter.put("special", method)

        limiter.enter("special")

        assertEquals(listOf(400L, 500L), sleeps)
        assertEquals(1_900L, global.time)
        assertEquals(1_900L, method.time)
    }
}
