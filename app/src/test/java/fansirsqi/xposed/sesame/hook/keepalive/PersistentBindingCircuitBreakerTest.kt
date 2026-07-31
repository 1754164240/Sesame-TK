package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentBindingCircuitBreakerTest {

    @Test
    fun `同一进程只允许一个绑定周期且失败后保持熔断`() {
        val breaker = PersistentBindingCircuitBreaker()

        assertTrue(breaker.tryAcquireBinding())
        assertFalse(breaker.tryAcquireBinding())

        breaker.onBindingFailed()

        assertTrue(breaker.isOpen())
        assertFalse(breaker.tryAcquireBinding())
    }

    @Test
    fun `绑定成功后允许后续断线重连`() {
        val breaker = PersistentBindingCircuitBreaker()

        assertTrue(breaker.tryAcquireBinding())
        breaker.onConnected()

        assertFalse(breaker.isOpen())
        assertTrue(breaker.tryAcquireBinding())
    }
}
