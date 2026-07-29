package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestMultiplierPolicyTest {

    @Test
    fun `金球结果只有明确正数可用于更新好友状态`() {
        val cases = listOf(
            "" to CollectedEnergyResult(false, 0),
            "not-json" to CollectedEnergyResult(false, 0),
            """{"success":true}""" to CollectedEnergyResult(false, 0),
            """{"success":true,"bubbles":[]}""" to
                CollectedEnergyResult(true, 0),
            """{"success":true,"bubbles":[{"collectedEnergy":0}]}""" to
                CollectedEnergyResult(true, 0),
            """{"success":true,"bubbles":[{"collectedEnergy":12}]}""" to
                CollectedEnergyResult(true, 12)
        )

        cases.forEach { (response, expected) ->
            val actual = ForestMultiplierPolicy.parseCollectedEnergy(response)
            assertEquals(expected, actual)
            assertEquals(
                expected.collected > 0,
                ForestMultiplierPolicy.shouldRecordWateredFriend(
                    actual,
                    "friend-1"
                )
            )
        }
        assertFalse(
            ForestMultiplierPolicy.shouldRecordWateredFriend(
                CollectedEnergyResult(true, 12),
                ""
            )
        )
    }

    @Test
    fun `主页缺少使用中道具字段时状态不确定`() {
        val snapshot = ForestMultiplierPolicy.parseActiveMultiplier(
            """{"success":true}""",
            nowMillis = 1_000L
        )

        assertEquals(ActiveMultiplierState.INCONCLUSIVE, snapshot.state)
    }

    @Test
    fun `主页明确空数组时确认无生效卡`() {
        val snapshot = ForestMultiplierPolicy.parseActiveMultiplier(
            """{"success":true,"usingUserPropsNew":[]}""",
            nowMillis = 1_000L
        )

        assertEquals(ActiveMultiplierState.CONFIRMED_NONE, snapshot.state)
    }

    @Test
    fun `主页解析生效卡倍率和结束时间`() {
        val snapshot = ForestMultiplierPolicy.parseActiveMultiplier(
            activeHome(
                propType = "SHAMO_ROB_EXPAND_CARD_1.5_1DAYS",
                endTime = 10_000L
            ),
            nowMillis = 1_000L
        )

        assertEquals(ActiveMultiplierState.ACTIVE, snapshot.state)
        assertEquals(1.5, snapshot.factor, 0.001)
        assertEquals(10_000L, snapshot.endTime)
    }

    @Test
    fun `生效卡未到替换阈值时不选择更高倍率候选`() {
        val active = ActiveMultiplierSnapshot(
            state = ActiveMultiplierState.ACTIVE,
            factor = 1.1,
            endTime = 10 * DAY_MILLIS,
            propType = "VITALITY_ROB_EXPAND_CARD_1.1_3DAYS"
        )
        val bag = ForestMultiplierPolicy.parseBag(
            bagResponse("SHAMO_ROB_EXPAND_CARD_1.5_1DAYS")
        )

        val selected = ForestMultiplierPolicy.selectCandidate(
            bag = bag,
            active = active,
            limitedOnly = false,
            replaceRemainDays = 3,
            nowMillis = 0L
        )

        assertTrue(bag.recognized)
        assertEquals(null, selected)
    }

    @Test
    fun `非限时卡不在允许时间时不选择但限时卡仍可选择`() {
        val active = ActiveMultiplierSnapshot(
            state = ActiveMultiplierState.CONFIRMED_NONE
        )
        val regularBag = ForestMultiplierPolicy.parseBag(
            bagResponse("ROB_EXPAND_CARD_1.3")
        )
        val limitedBag = ForestMultiplierPolicy.parseBag(
            bagResponse("LIMIT_TIME_ROB_EXPAND_CARD_1.5")
        )

        assertEquals(
            null,
            ForestMultiplierPolicy.selectCandidate(
                bag = regularBag,
                active = active,
                limitedOnly = false,
                replaceRemainDays = 0,
                nowMillis = 0L,
                regularUseAllowed = false
            )
        )
        assertEquals(
            "LIMIT_TIME_ROB_EXPAND_CARD_1.5",
            ForestMultiplierPolicy.selectCandidate(
                bag = limitedBag,
                active = active,
                limitedOnly = false,
                replaceRemainDays = 0,
                nowMillis = 0L,
                regularUseAllowed = false
            )?.propType
        )
    }

    @Test
    fun `只从用户选中的正数收好友倍卡SKU中补兑`() {
        val selected = linkedMapOf<String, Int?>(
            "shield" to 1,
            "disabled-card" to 0,
            "multiplier-card" to 2
        )
        val names = mapOf(
            "shield" to "限时保护罩",
            "disabled-card" to "限时1.1倍收好友能量卡",
            "multiplier-card" to "限时1.5倍收好友能量卡\n价格500活力值"
        )

        val skuId = ForestMultiplierPolicy.selectExchangeSku(selected) {
            names[it]
        }

        assertEquals("multiplier-card", skuId)
        assertEquals(
            null,
            ForestMultiplierPolicy.selectExchangeSku(
                linkedMapOf("shield" to 1),
                names::get
            )
        )
    }

    @Test
    fun `收好友倍卡属于支持续用确认的道具`() {
        assertTrue(
            ForestMultiplierPolicy.isRenewablePropType(
                "VITALITY_ROB_EXPAND_CARD_1.1_3DAYS"
            )
        )
        assertFalse(
            ForestMultiplierPolicy.isRenewablePropType("ENERGY_RAIN_CHANCE")
        )
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        fun activeHome(propType: String, endTime: Long): String {
            return """
                {
                  "success": true,
                  "usingUserPropsNew": [{
                    "propGroup": "robExpandCard",
                    "propType": "$propType",
                    "propName": "收好友能量卡",
                    "endTime": $endTime
                  }]
                }
            """.trimIndent()
        }

        fun bagResponse(vararg propTypes: String): String {
            val props = propTypes.mapIndexed { index, propType ->
                """
                {
                  "holdsNum": 1,
                  "propIdList": ["prop-$index"],
                  "recentExpireTime": 999999999,
                  "propConfigVO": {
                    "propGroup": "robExpandCard",
                    "propType": "$propType",
                    "propName": "候选卡-$index"
                  }
                }
                """.trimIndent()
            }.joinToString(",")
            return """{"success":true,"forestPropVOList":[$props]}"""
        }
    }
}
