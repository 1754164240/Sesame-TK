package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistentScheduleKeyTest {

    @Test
    fun customWakeTimeUsesCanonicalStableKey() {
        assertEquals("global:wakeup:06:50", PersistentScheduleKey.customWake("0650"))
        assertEquals("global:wakeup:06:50", PersistentScheduleKey.customWake("06:50"))
        assertEquals("global:wakeup:23:59", PersistentScheduleKey.customWake("2359"))
    }

    @Test
    fun invalidCustomWakeTimeIsRejected() {
        assertNull(PersistentScheduleKey.customWake(""))
        assertNull(PersistentScheduleKey.customWake("2500"))
        assertNull(PersistentScheduleKey.customWake("12:99"))
        assertNull(PersistentScheduleKey.customWake("-1"))
    }
}
