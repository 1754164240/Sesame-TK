package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationRecoverySnapshotTest {

    @Test
    fun `重开探测保留首次验证触发时间`() {
        val snapshot = VerificationRecoverySnapshot(
            generation = 3L,
            attemptCount = 12,
            ownerUserId = "owner",
            triggeredAtMillis = 1_234L,
            method = "rpc.method",
            context = null
        )

        val restarted = snapshot.restart(4L)

        assertEquals(4L, restarted.generation)
        assertEquals(0, restarted.attemptCount)
        assertEquals(1_234L, restarted.triggeredAtMillis)
    }
}
