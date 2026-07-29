package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentReceiverWorkerTest {

    @Test
    fun alarmInvokesPersistentServiceOnce() {
        val alarmTimes = mutableListOf<Long>()
        val worker = PersistentReceiverWorker(
            onAlarm = { alarmTimes.add(it) },
            onReconcile = {}
        )

        worker.handleAlarm(5_000L)

        assertEquals(listOf(5_000L), alarmTimes)
    }

    @Test
    fun supportedUnlockedEventReconciles() {
        val reconcileTimes = mutableListOf<Long>()
        val worker = PersistentReceiverWorker(
            onAlarm = {},
            onReconcile = { reconcileTimes.add(it) }
        )

        val handled = worker.handleReconcile(
            "android.intent.action.BOOT_COMPLETED",
            6_000L
        )

        assertTrue(handled)
        assertEquals(listOf(6_000L), reconcileTimes)
    }

    @Test
    fun lockedBootDoesNotReadCredentialBackedRegistry() {
        var reconcileCount = 0
        val worker = PersistentReceiverWorker(
            onAlarm = {},
            onReconcile = { reconcileCount++ }
        )

        val handled = worker.handleReconcile(
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            6_000L
        )

        assertFalse(handled)
        assertEquals(0, reconcileCount)
    }
}
