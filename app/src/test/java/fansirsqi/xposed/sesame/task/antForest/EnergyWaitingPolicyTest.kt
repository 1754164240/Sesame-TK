package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EnergyWaitingPolicyTest {

    @Test
    fun `过期和超远未来时间不会创建蹲点`() {
        val now = localTime(2026, Calendar.JULY, 27, 12, 0)

        assertEquals(
            EnergyWaitingTimeResult.EXPIRED,
            EnergyWaitingTimePolicy.validate(now - 1, now)
        )
        assertEquals(
            EnergyWaitingTimeResult.TOO_FAR,
            EnergyWaitingTimePolicy.validate(now + 8 * 60 * 60 * 1000L + 1, now)
        )
    }

    @Test
    fun `跨日时间即使不足八小时也不会创建蹲点`() {
        val now = localTime(2026, Calendar.JULY, 27, 23, 30)
        val nextDay = localTime(2026, Calendar.JULY, 28, 0, 10)

        assertEquals(
            EnergyWaitingTimeResult.CROSS_DAY,
            EnergyWaitingTimePolicy.validate(nextDay, now)
        )
    }

    @Test
    fun `同一天八小时内的未来时间允许创建蹲点`() {
        val now = localTime(2026, Calendar.JULY, 27, 12, 0)

        assertEquals(
            EnergyWaitingTimeResult.VALID,
            EnergyWaitingTimePolicy.validate(now + 30 * 60 * 1000L, now)
        )
    }

    @Test
    fun `同一任务只能占用一个运行槽`() {
        val registry = UniqueTaskRegistry<String>()

        assertTrue(registry.register("task-1", "job-a"))
        assertFalse(registry.register("task-1", "job-b"))
        assertFalse(registry.remove("task-1", "job-b"))
        assertTrue(registry.remove("task-1", "job-a"))
        assertTrue(registry.register("task-1", "job-b"))
    }

    @Test
    fun `无可收取能量球属于移除终态`() {
        assertEquals(
            WaitingCollectDecision.REMOVE_TERMINAL,
            EnergyWaitingResultPolicy.decide(
                CollectResult(
                    success = false,
                    userName = "测试用户",
                    message = "用户无可收取的能量球"
                )
            )
        )
    }

    @Test
    fun `连续提交持久化快照时只保留最新一份`() {
        val queue = LatestSnapshotQueue<List<String>>()

        assertTrue(queue.submit(listOf("first")))
        assertFalse(queue.submit(listOf("latest")))
        assertEquals(listOf("latest"), queue.takeLatest())
        assertNull(queue.takeLatest())
        assertFalse(queue.finish())
    }

    @Test
    fun `扫描批次结束前不启动写入并只保存最终快照`() {
        val queue = LatestSnapshotQueue<List<String>>()

        queue.beginBatch()
        assertFalse(queue.submit(listOf("first")))
        assertFalse(queue.submit(listOf("second")))
        assertEquals(listOf("second"), queue.peekLatest())
        assertTrue(queue.endBatch())
        assertEquals(listOf("second"), queue.takeLatest())
        assertFalse(queue.finish())
    }

    @Test
    fun `时间异常按扫描批次汇总`() {
        val summary = WaitingTimeAnomalySummary()

        summary.record(EnergyWaitingTimeResult.TOO_FAR)
        summary.record(EnergyWaitingTimeResult.TOO_FAR)
        summary.record(EnergyWaitingTimeResult.CROSS_DAY)

        assertEquals(2, summary.snapshot()[EnergyWaitingTimeResult.TOO_FAR])
        assertEquals(1, summary.snapshot()[EnergyWaitingTimeResult.CROSS_DAY])
        assertTrue(summary.describe().contains("超远未来2个"))
        assertTrue(summary.describe().contains("跨日异常1个"))
    }

    private fun localTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
    }
}
