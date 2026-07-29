package fansirsqi.xposed.sesame.task.antCooperate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CooperateWaterPolicyTest {
    @Test
    fun `普通合种只在同一项目剩余额度降低时确认`() {
        val after = JSONObject().put(
            "cooperatePlant",
            JSONObject().put("cooperationId", "COOP_1").put("waterDayLimit", 70)
        )

        assertEquals(
            CooperateWaterConfirmation(CooperateWaterOutcome.CONFIRMED, 30),
            CooperateWaterPolicy.confirmNormal(100, after, "COOP_1")
        )
        assertEquals(
            CooperateWaterOutcome.RETRY,
            CooperateWaterPolicy.confirmNormal(100, after, "OTHER").outcome
        )
        assertEquals(
            CooperateWaterOutcome.RETRY,
            CooperateWaterPolicy.confirmNormal(70, after, "COOP_1").outcome
        )
    }

    @Test
    fun `普通合种列表快照按项目身份读取`() {
        val response = JSONObject().put(
            "cooperatePlants",
            JSONArray()
                .put(JSONObject().put("cooperationId", "COOP_1").put("waterDayLimit", 50))
                .put(JSONObject().put("cooperationId", "COOP_2").put("waterDayLimit", 80))
        )

        assertEquals(80, CooperateWaterPolicy.normalRemaining(response, "COOP_2"))
        assertNull(CooperateWaterPolicy.normalRemaining(response, "COOP_3"))
    }

    @Test
    fun `真爱合种只在当前用户今日浇水量增加时确认`() {
        val after = JSONObject().put(
            "teamInfo",
            JSONObject().put(
                "waterInfo",
                JSONObject().put("todayWaterMap", JSONObject().put("USER_1", 20))
            )
        )

        assertEquals(
            CooperateWaterConfirmation(CooperateWaterOutcome.CONFIRMED, 20),
            CooperateWaterPolicy.confirmLove(0, after, "USER_1")
        )
        assertEquals(
            CooperateWaterOutcome.RETRY,
            CooperateWaterPolicy.confirmLove(20, after, "USER_1").outcome
        )
        assertEquals(
            CooperateWaterOutcome.RETRY,
            CooperateWaterPolicy.confirmLove(0, JSONObject(), "USER_1").outcome
        )
        assertEquals(
            0,
            CooperateWaterPolicy.loveTodayAmount(
                JSONObject().put(
                    "teamInfo",
                    JSONObject().put(
                        "waterInfo",
                        JSONObject().put("todayWaterMap", JSONObject())
                    )
                ),
                "USER_1"
            )
        )
    }

    @Test
    fun `组队合种只在服务端剩余额度降低时确认`() {
        val after = JSONObject().put(
            "combineHandlerVOMap",
            JSONObject().put(
                "teamCanWaterCount",
                JSONObject().put("waterCount", 60)
            )
        )

        assertEquals(100, CooperateWaterPolicy.teamRemaining(
            JSONObject(after.toString()).also {
                it.getJSONObject("combineHandlerVOMap")
                    .getJSONObject("teamCanWaterCount")
                    .put("waterCount", 100)
            }
        ))
        assertEquals(
            CooperateWaterConfirmation(CooperateWaterOutcome.CONFIRMED, 40),
            CooperateWaterPolicy.confirmTeam(100, after)
        )
        assertEquals(
            CooperateWaterOutcome.RETRY,
            CooperateWaterPolicy.confirmTeam(60, after).outcome
        )
        assertEquals(
            CooperateWaterOutcome.RETRY,
            CooperateWaterPolicy.confirmTeam(null, after).outcome
        )
    }
}
