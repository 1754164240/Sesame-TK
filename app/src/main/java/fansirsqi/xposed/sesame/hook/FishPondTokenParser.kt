package fansirsqi.xposed.sesame.hook

import org.json.JSONArray
import org.json.JSONObject

object FishPondTokenParser {
    fun parse(params: JSONObject): String? {
        directToken(params)?.let { return it }

        val requestData = params.opt("requestData") ?: return null
        val businessParams = when (requestData) {
            is JSONArray -> requestData.optJSONObject(0)
            is JSONObject -> requestData
            is String -> parseStringRequest(requestData)
            else -> null
        }
        return businessParams?.let(::directToken)
    }

    private fun parseStringRequest(requestData: String): JSONObject? {
        val content = requestData.trim()
        if (content.isEmpty()) {
            return null
        }
        return runCatching {
            if (content.startsWith("[")) {
                JSONArray(content).optJSONObject(0)
            } else {
                JSONObject(content)
            }
        }.getOrNull()
    }

    private fun directToken(params: JSONObject): String? {
        return params.optString("riskToken")
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
