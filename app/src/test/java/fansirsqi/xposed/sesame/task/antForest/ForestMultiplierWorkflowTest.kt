package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.task.antForest.ForestMultiplierPolicyTest.Companion.activeHome
import fansirsqi.xposed.sesame.task.antForest.ForestMultiplierPolicyTest.Companion.bagResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestMultiplierWorkflowTest {

    @Test
    fun `主页状态不确定时禁止查询背包使用和补兑`() {
        var bagCalls = 0
        var exchangeCalls = 0
        var useCalls = 0
        val workflow = workflow(
            homes = ArrayDeque(listOf("""{"success":true}""")),
            bags = ArrayDeque(),
            queryBag = {
                bagCalls++
                """{"success":true,"forestPropVOList":[]}"""
            },
            exchange = {
                exchangeCalls++
                """{"success":true}"""
            },
            use = {
                useCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.run(
            enabled = true,
            allowExchange = true,
            limitedOnly = false,
            replaceRemainDays = 3
        )

        assertEquals(MultiplierOutcome.RETRY, result.outcome)
        assertEquals(0, bagCalls)
        assertEquals(0, exchangeCalls)
        assertEquals(0, useCalls)
    }

    @Test
    fun `背包无卡且允许补兑时回查背包再使用并确认主页`() {
        var exchangeCalls = 0
        var useCalls = 0
        val workflow = workflow(
            homes = ArrayDeque(
                listOf(
                    """{"success":true,"usingUserPropsNew":[]}""",
                    activeHome(
                        "SHAMO_ROB_EXPAND_CARD_1.5_1DAYS",
                        10_000L
                    )
                )
            ),
            bags = ArrayDeque(
                listOf(
                    """{"success":true,"forestPropVOList":[]}""",
                    bagResponse("SHAMO_ROB_EXPAND_CARD_1.5_1DAYS")
                )
            ),
            exchange = {
                exchangeCalls++
                """{"success":true}"""
            },
            use = {
                useCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.run(
            enabled = true,
            allowExchange = true,
            limitedOnly = false,
            replaceRemainDays = 0
        )

        assertEquals(MultiplierOutcome.CONFIRMED, result.outcome)
        assertTrue(result.exchanged)
        assertEquals(1, exchangeCalls)
        assertEquals(1, useCalls)
        assertEquals(1.5, result.confirmedActive?.factor ?: 0.0, 0.001)
        assertEquals(10_000L, result.confirmedActive?.endTime)
    }

    @Test
    fun `补兑后背包仍无卡时停止且不调用使用`() {
        var useCalls = 0
        val workflow = workflow(
            homes = ArrayDeque(
                listOf("""{"success":true,"usingUserPropsNew":[]}""")
            ),
            bags = ArrayDeque(
                listOf(
                    """{"success":true,"forestPropVOList":[]}""",
                    """{"success":true,"forestPropVOList":[]}"""
                )
            ),
            use = {
                useCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.run(
            enabled = true,
            allowExchange = true,
            limitedOnly = false,
            replaceRemainDays = 0
        )

        assertEquals(MultiplierOutcome.RETRY, result.outcome)
        assertTrue(result.exchanged)
        assertEquals(0, useCalls)
    }

    @Test
    fun `使用ACK后主页未出现目标倍率时保留重试`() {
        val workflow = workflow(
            homes = ArrayDeque(
                listOf(
                    """{"success":true,"usingUserPropsNew":[]}""",
                    """{"success":true,"usingUserPropsNew":[]}"""
                )
            ),
            bags = ArrayDeque(
                listOf(
                    bagResponse("SHAMO_ROB_EXPAND_CARD_1.5_1DAYS")
                )
            )
        )

        val result = workflow.run(
            enabled = true,
            allowExchange = false,
            limitedOnly = false,
            replaceRemainDays = 0
        )

        assertEquals(MultiplierOutcome.RETRY, result.outcome)
        assertFalse(result.exchanged)
        assertTrue(result.used)
    }

    @Test
    fun `已有生效卡未到替换阈值时不使用也不补兑`() {
        var exchangeCalls = 0
        var useCalls = 0
        val workflow = workflow(
            homes = ArrayDeque(
                listOf(
                    activeHome(
                        "VITALITY_ROB_EXPAND_CARD_1.1_3DAYS",
                        10 * DAY_MILLIS
                    )
                )
            ),
            bags = ArrayDeque(
                listOf(
                    bagResponse("SHAMO_ROB_EXPAND_CARD_1.5_1DAYS")
                )
            ),
            exchange = {
                exchangeCalls++
                """{"success":true}"""
            },
            use = {
                useCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.run(
            enabled = true,
            allowExchange = true,
            limitedOnly = false,
            replaceRemainDays = 3
        )

        assertEquals(MultiplierOutcome.NO_ACTION, result.outcome)
        assertEquals(0, exchangeCalls)
        assertEquals(0, useCalls)
    }

    @Test
    fun `不在普通卡使用时间时不调用使用`() {
        var useCalls = 0
        val workflow = workflow(
            homes = ArrayDeque(
                listOf("""{"success":true,"usingUserPropsNew":[]}""")
            ),
            bags = ArrayDeque(
                listOf(bagResponse("ROB_EXPAND_CARD_1.3"))
            ),
            use = {
                useCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.run(
            enabled = true,
            allowExchange = false,
            limitedOnly = false,
            replaceRemainDays = 0,
            regularUseAllowed = false
        )

        assertEquals(MultiplierOutcome.NO_ACTION, result.outcome)
        assertEquals(0, useCalls)
    }

    private fun workflow(
        homes: ArrayDeque<String>,
        bags: ArrayDeque<String>,
        queryBag: (() -> String)? = null,
        exchange: () -> String = { """{"success":true}""" },
        use: (MultiplierCardCandidate) -> String = {
            """{"success":true}"""
        }
    ): ForestMultiplierWorkflow {
        return ForestMultiplierWorkflow(
            queryHome = {
                check(homes.isNotEmpty()) {
                    "测试主页响应不足"
                }
                homes.removeFirst()
            },
            queryBag = queryBag ?: {
                check(bags.isNotEmpty()) {
                    "测试背包响应不足"
                }
                bags.removeFirst()
            },
            exchangeCard = exchange,
            useCard = use,
            nowMillis = { 1_000L }
        )
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
