package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class RpcFailureClassifierTest {
    @Test
    fun `明确成功响应分类为成功`() {
        assertEquals(
            RpcFailureKind.SUCCESS,
            RpcFailureClassifier.classify("""{"success":true,"resultCode":"SUCCESS"}""").kind
        )
        assertEquals(
            RpcFailureKind.SUCCESS,
            RpcFailureClassifier.classify("""{"code":100000000}""").kind
        )
    }

    @Test
    fun `离线和人工验证分开分类`() {
        assertEquals(
            RpcFailureKind.OFFLINE,
            RpcFailureClassifier.classify("""{"success":false,"resultCode":"I07","resultDesc":"离线模式"}""").kind
        )
        assertEquals(
            RpcFailureKind.VERIFICATION_REQUIRED,
            RpcFailureClassifier.classify("""{"success":false,"resultCode":"1009","resultDesc":"请进行验证后继续"}""").kind
        )
        assertEquals(
            RpcFailureKind.VERIFICATION_REQUIRED,
            RpcFailureClassifier.classify("""{"success":false,"resultCode":"LIMIT","resultDesc":"访问频繁，请进行验证后继续"}""").kind
        )
    }

    @Test
    fun `频率限制不归入离线恢复`() {
        assertEquals(
            RpcFailureKind.FREQUENCY_LIMITED,
            RpcFailureClassifier.classify("""{"success":false,"resultCode":"BIZ_LIMIT","resultDesc":"操作频繁，请稍后再试"}""").kind
        )
        assertEquals(
            RpcFailureKind.FREQUENCY_LIMITED,
            RpcFailureClassifier.classify("""{"success":false,"resData":{"retCode":"217","sspErrorCode":"61002"}}""").kind
        )
    }

    @Test
    fun `空响应畸形响应和网络超时可重试`() {
        assertEquals(RpcFailureKind.RETRYABLE, RpcFailureClassifier.classify(" ").kind)
        assertEquals(RpcFailureKind.RETRYABLE, RpcFailureClassifier.classify("not-json").kind)
        assertEquals(
            RpcFailureKind.RETRYABLE,
            RpcFailureClassifier.classify("""{"success":false,"errorMessage":"network timeout"}""").kind
        )
    }

    @Test
    fun `无法识别的明确失败保持未知`() {
        val failure = RpcFailureClassifier.classify(
            """{"success":false,"resultCode":"NEW_FAILURE","resultDesc":"服务拒绝"}"""
        )

        assertEquals(RpcFailureKind.UNKNOWN, failure.kind)
        assertEquals("NEW_FAILURE", failure.code)
        assertEquals("服务拒绝", failure.message)
    }
}
