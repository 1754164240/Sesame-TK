package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcBridgeDispatchPolicyTest {

    @Test
    fun `验证阻断时普通业务和直接调用均被Bridge拒绝`() {
        assertTrue(
            RpcBridgeDispatchPolicy.shouldBlock(
                offline = true,
                purpose = RpcRequestPurpose.BUSINESS,
                requestGeneration = null,
                currentGeneration = 3L,
                blockReason = RpcBlockReason.VERIFICATION
            )
        )
        assertTrue(
            RpcBridgeDispatchPolicy.shouldBlock(
                offline = true,
                purpose = null,
                requestGeneration = null,
                currentGeneration = 3L,
                blockReason = RpcBlockReason.VERIFICATION
            )
        )
    }

    @Test
    fun `验证阻断时只放行当前代际探针`() {
        assertFalse(
            RpcBridgeDispatchPolicy.shouldBlock(
                offline = true,
                purpose = RpcRequestPurpose.VERIFICATION_PROBE,
                requestGeneration = 4L,
                currentGeneration = 4L,
                blockReason = RpcBlockReason.VERIFICATION
            )
        )
        assertTrue(
            RpcBridgeDispatchPolicy.shouldBlock(
                offline = true,
                purpose = RpcRequestPurpose.VERIFICATION_PROBE,
                requestGeneration = 3L,
                currentGeneration = 4L,
                blockReason = RpcBlockReason.VERIFICATION
            )
        )
    }

    @Test
    fun `未离线时不拦截普通业务`() {
        assertFalse(
            RpcBridgeDispatchPolicy.shouldBlock(
                offline = false,
                purpose = RpcRequestPurpose.BUSINESS,
                requestGeneration = null,
                currentGeneration = 0L,
                blockReason = RpcBlockReason.NONE
            )
        )
    }
}
