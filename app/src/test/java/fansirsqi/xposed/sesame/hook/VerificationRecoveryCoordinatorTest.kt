package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationRecoveryCoordinatorTest {

    @Test
    fun `探测成功只恢复一次并取消后续探测`() {
        val scheduled = ArrayDeque<() -> Unit>()
        var probeCount = 0
        val recovered = mutableListOf<Long>()
        val coordinator = VerificationRecoveryCoordinator(
            schedule = { delay, _, block ->
                assertEquals(10_000L, delay)
                scheduled.addLast(block)
            },
            probe = {
                probeCount++
                probeCount == 2
            },
            onRecovered = recovered::add,
            onExhausted = {}
        )

        coordinator.start(5L)
        scheduled.removeFirst().invoke()
        scheduled.removeFirst().invoke()

        assertEquals(2, probeCount)
        assertEquals(listOf(5L), recovered)
        assertEquals(0, scheduled.size)
    }

    @Test
    fun `三十次失败后只通知一次耗尽`() {
        val scheduled = ArrayDeque<() -> Unit>()
        val exhausted = mutableListOf<Long>()
        val coordinator = VerificationRecoveryCoordinator(
            schedule = { _, _, block -> scheduled.addLast(block) },
            probe = { false },
            onRecovered = {},
            onExhausted = exhausted::add
        )

        coordinator.start(8L)
        repeat(30) {
            scheduled.removeFirst().invoke()
        }

        assertEquals(listOf(8L), exhausted)
        assertEquals(0, scheduled.size)
    }
}
