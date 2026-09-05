package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcDispatchGateTest {
    @Test
    fun `限流等待中发生验证则禁止发送`() {
        var blocked = false
        assertFalse(RpcDispatchGate.awaitPermission({ blocked }) { blocked = true })
    }

    @Test
    fun `已经暂停时不再进入等待`() {
        var waited = false
        assertFalse(RpcDispatchGate.awaitPermission({ true }) { waited = true })
        assertFalse(waited)
    }

    @Test
    fun `正常等待结束后允许发送`() {
        assertTrue(RpcDispatchGate.awaitPermission({ false }) {})
    }
}
