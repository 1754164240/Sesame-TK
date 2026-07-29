package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestManagerTest {

    @Test
    fun `空响应不会交给调用方解析成空JSON`() {
        assertTrue(RequestManager.isEmptyRpcResponse(""))
        assertTrue(RequestManager.isEmptyRpcResponse("   "))
        assertFalse(RequestManager.isEmptyRpcResponse("""{"success":true}"""))
    }

    @Test
    fun `验证错误会触发离线熔断`() {
        assertTrue(RequestManager.isVerificationRequired("1009", "为保障您的正常访问，请进行验证后继续。"))
        assertTrue(RequestManager.isVerificationRequired("1009", "为了保障您的操作安全，请进行验证后继续"))
        assertFalse(RequestManager.isVerificationRequired("200", "成功"))
    }

    @Test
    fun `请求管理器公开统一响应分类边界`() {
        assertEquals(
            RpcFailureKind.RETRYABLE,
            RequestManager.classifyResponse(RequestManager.EMPTY_RPC_RESPONSE).kind
        )
        assertEquals(
            RpcFailureKind.VERIFICATION_REQUIRED,
            RequestManager.classifyResponse(RequestManager.VERIFICATION_REQUIRED_RESPONSE).kind
        )
    }
}
