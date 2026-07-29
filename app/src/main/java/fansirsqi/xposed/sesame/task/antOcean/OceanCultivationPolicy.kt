package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONObject

data class OceanCultivation(
    val cultivationCode: String,
    val projectCode: String,
    val name: String,
    val energy: Int,
    val templateSubType: String,
    val available: Boolean
)

object OceanCultivationPolicy {
    @JvmStatic
    fun parse(item: JSONObject?): OceanCultivation? {
        if (item == null) return null
        val cultivationCode = item.optString("cultivationCode").trim()
        val projectCode = item.optJSONObject("projectConfigVO")
            ?.optString("code")
            .orEmpty()
            .trim()
        if (cultivationCode.isEmpty() || projectCode.isEmpty()) return null

        return OceanCultivation(
            cultivationCode = cultivationCode,
            projectCode = projectCode,
            name = item.optString("cultivationName", cultivationCode),
            energy = item.optInt("energy", 0).coerceAtLeast(0),
            templateSubType = item.optString("templateSubType"),
            available = item.optString("applyAction").equals("AVAILABLE", ignoreCase = true)
        )
    }
}
