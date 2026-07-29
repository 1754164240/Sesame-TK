package fansirsqi.xposed.sesame.task.antCooperate

import org.json.JSONObject

enum class CooperateWaterOutcome {
    CONFIRMED,
    RETRY,
    REJECTED
}

data class CooperateWaterConfirmation(
    val outcome: CooperateWaterOutcome,
    val confirmedAmount: Int = 0
)

object CooperateWaterPolicy {
    fun normalRemaining(response: JSONObject, cooperationId: String): Int? {
        val direct = response.optJSONObject("cooperatePlant")
        if (direct != null && direct.optString("cooperationId") == cooperationId && direct.has("waterDayLimit")) {
            return direct.optInt("waterDayLimit").takeIf { it >= 0 }
        }
        val plants = response.optJSONArray("cooperatePlants") ?: return null
        for (index in 0 until plants.length()) {
            val plant = plants.optJSONObject(index) ?: continue
            if (plant.optString("cooperationId") == cooperationId && plant.has("waterDayLimit")) {
                return plant.optInt("waterDayLimit").takeIf { it >= 0 }
            }
        }
        return null
    }

    fun loveTodayAmount(response: JSONObject, userId: String): Int? {
        val todayWaterMap = response.optJSONObject("teamInfo")
            ?.optJSONObject("waterInfo")
            ?.optJSONObject("todayWaterMap")
            ?: return null
        return todayWaterMap.optInt(userId, 0).takeIf { it >= 0 }
    }

    fun teamRemaining(response: JSONObject): Int? {
        val count = response.optJSONObject("combineHandlerVOMap")
            ?.optJSONObject("teamCanWaterCount")
            ?: return null
        if (!count.has("waterCount")) return null
        return count.optInt("waterCount").takeIf { it >= 0 }
    }

    fun confirmNormal(before: Int?, after: JSONObject, cooperationId: String): CooperateWaterConfirmation {
        return confirmDecrease(before, normalRemaining(after, cooperationId))
    }

    fun confirmLove(before: Int?, after: JSONObject, userId: String): CooperateWaterConfirmation {
        return confirmIncrease(before, loveTodayAmount(after, userId))
    }

    fun confirmTeam(before: Int?, after: JSONObject): CooperateWaterConfirmation {
        return confirmDecrease(before, teamRemaining(after))
    }

    private fun confirmDecrease(before: Int?, after: Int?): CooperateWaterConfirmation {
        if (before == null || after == null || after >= before) {
            return CooperateWaterConfirmation(CooperateWaterOutcome.RETRY)
        }
        return CooperateWaterConfirmation(CooperateWaterOutcome.CONFIRMED, before - after)
    }

    private fun confirmIncrease(before: Int?, after: Int?): CooperateWaterConfirmation {
        if (before == null || after == null || after <= before) {
            return CooperateWaterConfirmation(CooperateWaterOutcome.RETRY)
        }
        return CooperateWaterConfirmation(CooperateWaterOutcome.CONFIRMED, after - before)
    }
}
