package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestAutomationPolicyTest {

    @Test
    fun `浇水新增配置保持兼容且高影响动作默认关闭`() {
        val fields = AntForest().fields

        assertTrue(fields["wateringEnabled"]?.value as Boolean)
        assertFalse(fields["waterFriendEnergyFirst"]?.value as Boolean)
        assertFalse(fields["notifyRandomWatering"]?.value as Boolean)
    }

    @Test
    fun `保护罩续用阈值默认二十四小时且限制在一周内`() {
        val threshold = AntForest().fields["shieldRenewThresholdHours"] as IntegerModelField

        assertEquals(24, threshold.value)
        assertEquals(0, threshold.minLimit)
        assertEquals(168, threshold.maxLimit)
    }

    @Test
    fun `浇水总开关和优先执行状态共同决定执行顺序`() {
        assertFalse(
            ForestWateringPolicy.shouldRunBeforeCollect(
                enabled = false,
                prioritized = true,
                alreadyExecuted = false
            )
        )
        assertFalse(
            ForestWateringPolicy.shouldRunBeforeCollect(
                enabled = true,
                prioritized = false,
                alreadyExecuted = false
            )
        )
        assertTrue(
            ForestWateringPolicy.shouldRunBeforeCollect(
                enabled = true,
                prioritized = true,
                alreadyExecuted = false
            )
        )
        assertFalse(
            ForestWateringPolicy.shouldRunBeforeCollect(
                enabled = true,
                prioritized = true,
                alreadyExecuted = true
            )
        )
    }

    @Test
    fun `名单浇水和随机返水使用独立通知开关`() {
        assertTrue(
            ForestWateringPolicy.shouldNotify(
                configuredWatering = true,
                configuredNotify = true,
                randomNotify = false
            )
        )
        assertFalse(
            ForestWateringPolicy.shouldNotify(
                configuredWatering = false,
                configuredNotify = true,
                randomNotify = false
            )
        )
        assertTrue(
            ForestWateringPolicy.shouldNotify(
                configuredWatering = false,
                configuredNotify = false,
                randomNotify = true
            )
        )
    }

    @Test
    fun `保护罩按小时阈值续用并拒绝过旧异常时间`() {
        val now = 2_000_000_000_000L
        val hour = 60L * 60L * 1000L

        assertTrue(ForestShieldPolicy.shouldRenew(0L, now, 24))
        assertTrue(ForestShieldPolicy.shouldRenew(now - hour, now, 24))
        assertTrue(ForestShieldPolicy.shouldRenew(now + 24 * hour, now, 24))
        assertFalse(ForestShieldPolicy.shouldRenew(now + 25 * hour, now, 24))
        assertFalse(
            ForestShieldPolicy.shouldRenew(
                now - 366L * 24L * hour,
                now,
                24
            )
        )
    }

    @Test
    fun `保护罩识别兼容分组和活动类型`() {
        assertTrue(ForestShieldPolicy.isShield("shield", "UNKNOWN_PROP"))
        assertTrue(ForestShieldPolicy.isShield("", "DFYC_ENERGY_SHIELD"))
        assertFalse(ForestShieldPolicy.isShield("boost", "BUBBLE_BOOST"))
    }
}
