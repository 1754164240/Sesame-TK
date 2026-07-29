package fansirsqi.xposed.sesame.hook

import org.json.JSONArray
import org.json.JSONObject

object ReferTokenParser {
    fun parse(params: JSONObject): String? {
        val businessParams = extractBusinessParams(params.opt("requestData")) ?: params
        return businessParams.optJSONObject("positionRequest")
            ?.optJSONObject("referInfo")
            ?.optString("referToken")
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractBusinessParams(value: Any?): JSONObject? {
        return when (value) {
            is JSONArray -> value.optJSONObject(0)
            is JSONObject -> value
            is String -> parseStringValue(value)
            else -> null
        }
    }

    private fun parseStringValue(value: String): JSONObject? {
        val content = value.trim()
        if (content.isEmpty()) {
            return null
        }
        return runCatching {
            when {
                content.startsWith("[") -> JSONArray(content).optJSONObject(0)
                content.startsWith("{") -> JSONObject(content)
                else -> null
            }
        }.getOrNull()
    }
}
