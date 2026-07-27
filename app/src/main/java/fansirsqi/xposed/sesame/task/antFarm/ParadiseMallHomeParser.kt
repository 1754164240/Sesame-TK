package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONArray
import org.json.JSONObject

sealed class ParadiseMallHomeResult {
    data class RpcFailure(
        val code: String,
        val description: String
    ) : ParadiseMallHomeResult()

    data object SchemaChanged : ParadiseMallHomeResult()

    data object Empty : ParadiseMallHomeResult()

    data class Items(val items: JSONArray) : ParadiseMallHomeResult()
}

object ParadiseMallHomeParser {
    fun parse(response: String?): ParadiseMallHomeResult {
        if (response.isNullOrBlank()) {
            return ParadiseMallHomeResult.RpcFailure(
                code = "EMPTY_RPC_RESPONSE",
                description = "RPC返回为空"
            )
        }

        val json = runCatching { JSONObject(response) }.getOrElse {
            return ParadiseMallHomeResult.RpcFailure(
                code = "INVALID_RPC_RESPONSE",
                description = "RPC响应不是有效JSON"
            )
        }

        if (!isSuccess(json)) {
            return ParadiseMallHomeResult.RpcFailure(
                code = json.optString("resultCode", "UNKNOWN"),
                description = json.optString("resultDesc", json.optString("memo", "RPC调用失败"))
            )
        }

        val items = json.optJSONArray("mallItemSimpleList")
            ?: return ParadiseMallHomeResult.SchemaChanged
        return if (items.length() == 0) {
            ParadiseMallHomeResult.Empty
        } else {
            ParadiseMallHomeResult.Items(items)
        }
    }

    private fun isSuccess(json: JSONObject): Boolean {
        if (json.optBoolean("success") || json.optBoolean("isSuccess")) {
            return true
        }
        if (json.optInt("resultCode", Int.MIN_VALUE) == 200) {
            return true
        }
        val resultCode = json.optString("resultCode")
        if (resultCode.equals("SUCCESS", ignoreCase = true) || resultCode == "100") {
            return true
        }
        return json.optString("memo").equals("SUCCESS", ignoreCase = true)
    }
}
