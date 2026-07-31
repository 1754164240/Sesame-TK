package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationTaskCoordinatorTest {

    @Test
    fun `同一代际只停止一次且只认领一次恢复`() {
        var stopCount = 0
        val coordinator = VerificationTaskCoordinator {
            stopCount++
        }

        assertTrue(coordinator.stopForVerification(7L))
        assertFalse(coordinator.stopForVerification(7L))
        assertEquals(1, stopCount)
        assertTrue(coordinator.claimRecovery(7L))
        assertFalse(coordinator.claimRecovery(7L))
    }

    @Test
    fun `新代际可以重新停止和恢复`() {
        var stopCount = 0
        val coordinator = VerificationTaskCoordinator {
            stopCount++
        }

        assertTrue(coordinator.stopForVerification(2L))
        assertTrue(coordinator.claimRecovery(2L))
        assertTrue(coordinator.stopForVerification(3L))
        assertTrue(coordinator.claimRecovery(3L))
        assertEquals(2, stopCount)
    }
}
