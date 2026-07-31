package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationProbeTrackerTest {

    @Test
    fun `探测固定十秒且最多三十次真实请求`() {
        val tracker = VerificationProbeTracker()
        tracker.startCycle(7L)

        assertEquals(10_000L, tracker.intervalMillis)
        repeat(30) {
            assertTrue(tracker.tryBeginAttempt(7L))
            val result = tracker.completeAttempt(
                7L,
                VerificationProbeResult(dispatched = true, successful = false)
            )
            val expected = if (it == 29) ProbeCompletion.EXHAUSTED else ProbeCompletion.SCHEDULE_NEXT
            assertEquals(expected, result)
        }

        assertFalse(tracker.tryBeginAttempt(7L))
        assertEquals(30, tracker.attemptCount)
    }

    @Test
    fun `同一代际只允许一个在途探测且旧代际结果无效`() {
        val tracker = VerificationProbeTracker()
        tracker.startCycle(1L)
        assertTrue(tracker.tryBeginAttempt(1L))
        assertFalse(tracker.tryBeginAttempt(1L))

        tracker.startCycle(2L)
        assertEquals(
            ProbeCompletion.STALE,
            tracker.completeAttempt(
                1L,
                VerificationProbeResult(dispatched = true, successful = true)
            )
        )
        assertTrue(tracker.tryBeginAttempt(2L))
        assertEquals(
            ProbeCompletion.RECOVERED,
            tracker.completeAttempt(
                2L,
                VerificationProbeResult(dispatched = true, successful = true)
            )
        )
    }

    @Test
    fun `请求未投递时不消耗探测次数`() {
        val tracker = VerificationProbeTracker()
        tracker.startCycle(9L)

        assertTrue(tracker.tryBeginAttempt(9L))
        assertEquals(
            ProbeCompletion.SCHEDULE_NEXT,
            tracker.completeAttempt(
                9L,
                VerificationProbeResult(dispatched = false, successful = false)
            )
        )
        assertEquals(0, tracker.attemptCount)
    }
}
