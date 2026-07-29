package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ForestMultiplierSchedulePolicyTest {

    @Test
    fun `单点配置只进入定时列表不直接开放普通卡`() {
        val entries = listOf("0700", "0730", "0700", "2360", "invalid")
        val now = todayAt(7, 0)

        assertEquals(
            listOf("0700", "0730"),
            ForestMultiplierSchedulePolicy.scheduledPoints(entries)
        )
        assertFalse(
            ForestMultiplierSchedulePolicy.isRegularUseAllowed(now, entries)
        )
    }

    @Test
    fun `范围配置只在窗口内开放普通卡`() {
        val entries = listOf("0700-0730", "1200", "2200-0100")

        assertTrue(
            ForestMultiplierSchedulePolicy.isRegularUseAllowed(
                todayAt(7, 15),
                entries
            )
        )
        assertTrue(
            ForestMultiplierSchedulePolicy.isRegularUseAllowed(
                todayAt(23, 0),
                entries
            )
        )
        assertFalse(
            ForestMultiplierSchedulePolicy.isRegularUseAllowed(
                todayAt(8, 0),
                entries
            )
        )
    }

    private fun todayAt(hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
