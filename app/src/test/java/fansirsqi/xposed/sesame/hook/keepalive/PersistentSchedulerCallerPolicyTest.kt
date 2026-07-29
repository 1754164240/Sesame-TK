package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentSchedulerCallerPolicyTest {

    @Test
    fun moduleAndTargetPackagesAreAllowed() {
        assertTrue(
            PersistentSchedulerCallerPolicy.isAllowed(
                listOf("fansirsqi.xposed.sesame"),
                "fansirsqi.xposed.sesame",
                "com.eg.android.AlipayGphone"
            )
        )
        assertTrue(
            PersistentSchedulerCallerPolicy.isAllowed(
                listOf("com.eg.android.AlipayGphone"),
                "fansirsqi.xposed.sesame",
                "com.eg.android.AlipayGphone"
            )
        )
    }

    @Test
    fun unrelatedOrUnknownCallerIsRejected() {
        assertFalse(
            PersistentSchedulerCallerPolicy.isAllowed(
                listOf("other.app"),
                "fansirsqi.xposed.sesame",
                "com.eg.android.AlipayGphone"
            )
        )
        assertFalse(
            PersistentSchedulerCallerPolicy.isAllowed(
                emptyList(),
                "fansirsqi.xposed.sesame",
                "com.eg.android.AlipayGphone"
            )
        )
    }
}
