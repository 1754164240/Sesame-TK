package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentBindingFailureClassifierTest {

    @Test
    fun `区分绑定返回false和连接超时`() {
        assertEquals(
            PersistentBindingFailureKind.BIND_RETURNED_FALSE,
            PersistentBindingFailureClassifier.classify(
                IllegalStateException("bindService返回false: component=test")
            )
        )
        assertEquals(
            PersistentBindingFailureKind.CONNECTION_TIMEOUT,
            PersistentBindingFailureClassifier.classify(
                IllegalStateException("等待持久调度服务连接超时")
            )
        )
    }

    @Test
    fun `识别被包装的安全异常`() {
        assertEquals(
            PersistentBindingFailureKind.SECURITY_REJECTED,
            PersistentBindingFailureClassifier.classify(
                IllegalStateException("绑定被拒绝", SecurityException("denied"))
            )
        )
    }
}
