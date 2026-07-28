package fansirsqi.xposed.sesame.hook

import org.json.JSONObject

object ReferTokenParser {
    fun parse(params: JSONObject): String? {
        val businessParams = params.optJSONArray("requestData")
            ?.optJSONObject(0)
            ?: params
        return businessParams.optJSONObject("positionRequest")
            ?.optJSONObject("referInfo")
            ?.optString("referToken")
            ?.takeIf { it.isNotBlank() }
    }
}
