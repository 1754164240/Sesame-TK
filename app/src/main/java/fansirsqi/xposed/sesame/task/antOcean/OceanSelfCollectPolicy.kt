package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONObject

object OceanSelfCollectPolicy {
    @JvmStatic
    fun energyOf(bubble: JSONObject): Int? {
        return parseEnergy(bubble.opt("fullEnergy"))
            ?: parseEnergy(bubble.opt("energy"))
    }

    @JvmStatic
    fun shouldCollect(bubble: JSONObject, threshold: Int): Boolean {
        if (bubble.optString("channel") != "ocean" || bubble.optString("collectStatus") != "AVAILABLE") {
            return false
        }
        val energy = energyOf(bubble) ?: return false
        return energy >= threshold.coerceAtLeast(0)
    }

    private fun parseEnergy(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt().takeIf { it >= 0 }
            is String -> value.trim().toIntOrNull()?.takeIf { it >= 0 }
            else -> null
        }
    }
}
