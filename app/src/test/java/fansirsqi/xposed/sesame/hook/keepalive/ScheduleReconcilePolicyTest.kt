package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleReconcilePolicyTest {

    @Test
    fun supportedUnlockedSystemEventsRequestReconcile() {
        assertTrue(ScheduleReconcilePolicy.shouldReconcile("android.intent.action.BOOT_COMPLETED"))
        assertTrue(ScheduleReconcilePolicy.shouldReconcile("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(ScheduleReconcilePolicy.shouldReconcile("android.intent.action.TIME_SET"))
        assertTrue(ScheduleReconcilePolicy.shouldReconcile("android.intent.action.TIMEZONE_CHANGED"))
        assertTrue(
            ScheduleReconcilePolicy.shouldReconcile(
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
            )
        )
    }

    @Test
    fun lockedBootAndUnknownEventsDoNotReadCredentialStorage() {
        assertFalse(ScheduleReconcilePolicy.shouldReconcile("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertFalse(ScheduleReconcilePolicy.shouldReconcile("other"))
        assertFalse(ScheduleReconcilePolicy.shouldReconcile(null))
    }
}
