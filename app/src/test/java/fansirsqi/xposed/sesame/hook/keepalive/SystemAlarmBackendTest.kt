package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemAlarmBackendTest {

    @Test
    fun allowedExactAlarmArmsRequestedTime() {
        val access = FakeExactAlarmAccess()
        val backend = SystemAlarmBackend(access)

        assertTrue(backend.arm(5_000L))
        assertEquals(listOf(5_000L), access.armedTimes)
    }

    @Test
    fun missingExactAlarmPermissionKeepsTaskForFallback() {
        val access = FakeExactAlarmAccess(exactAlarmAllowed = false)
        val backend = SystemAlarmBackend(access)

        assertFalse(backend.arm(5_000L))
        assertTrue(access.armedTimes.isEmpty())
    }

    @Test
    fun platformFailuresReturnFalseInsteadOfDroppingTask() {
        val access = FakeExactAlarmAccess(throwOnArm = true, throwOnCancel = true)
        val backend = SystemAlarmBackend(access)

        assertFalse(backend.arm(5_000L))
        assertFalse(backend.cancel())
    }
}

private class FakeExactAlarmAccess(
    private val exactAlarmAllowed: Boolean = true,
    private val throwOnArm: Boolean = false,
    private val throwOnCancel: Boolean = false
) : ExactAlarmAccess {
    val armedTimes = mutableListOf<Long>()

    override fun canScheduleExactAlarms(): Boolean = exactAlarmAllowed

    override fun armExact(triggerAtMillis: Long) {
        if (throwOnArm) error("arm failed")
        armedTimes.add(triggerAtMillis)
    }

    override fun cancelExact() {
        if (throwOnCancel) error("cancel failed")
    }
}
