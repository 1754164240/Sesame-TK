package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun `请求管理器返回结构化空响应避免JSON解析异常`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/hook/RequestManager.kt").readText()

        assertTrue(sourceText.contains("EMPTY_RPC_RESPONSE"))
        assertTrue(sourceText.contains("VERIFICATION_REQUIRED_RESPONSE"))
    }
}
