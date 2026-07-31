package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationStartupStatePolicyTest {

    @Test
    fun `新进程启动时清除上次遗留的验证阻断`() {
        val action = VerificationStartupStatePolicy.resolve(
            currentBlockReason = RpcBlockReason.NONE,
            currentGeneration = 0L,
            persistedGeneration = 7L
        )

        assertEquals(VerificationStartupAction.CLEAR_STALE, action)
    }

    @Test
    fun `同一进程同一代际继续当前验证阻断`() {
        val action = VerificationStartupStatePolicy.resolve(
            currentBlockReason = RpcBlockReason.VERIFICATION,
            currentGeneration = 7L,
            persistedGeneration = 7L
        )

        assertEquals(VerificationStartupAction.RESUME_CURRENT, action)
    }
}
