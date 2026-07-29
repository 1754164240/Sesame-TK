package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject

enum class YebExpGoldSignState {
    PENDING,
    SIGNED,
    UNKNOWN
}

object YebExpGoldPolicy {
    private val successCodes = setOf("SUCCESS", "100", "200", "0")

    fun signState(response: String): YebExpGoldSignState {
        val root = parse(response) ?: return YebExpGoldSignState.UNKNOWN
        val containers = containers(root)
        if (hasExplicitFailure(containers)) {
            return YebExpGoldSignState.UNKNOWN
        }
        for (container in containers) {
            val signList = container.optJSONObject("signInData")
                ?.optJSONArray("list")
                ?: continue
            for (index in 0 until signList.length()) {
                val item = signList.optJSONObject(index) ?: continue
                val signInfo = item.optJSONObject("signInfo") ?: continue
                val isToday = signInfo.optString("signDateDesc")
                    .equals("TODAY", true) ||
                    item.optString("displayDate").contains("今天")
                if (!isToday) {
                    continue
                }
                return when (
                    signInfo.optString("signStatus").trim().uppercase()
                ) {
                    "TO_SIGNED", "UNSIGNED" ->
                        YebExpGoldSignState.PENDING

                    "" -> YebExpGoldSignState.UNKNOWN
                    else -> YebExpGoldSignState.SIGNED
                }
            }
        }
        return YebExpGoldSignState.UNKNOWN
    }

    fun pendingVoucherCount(response: String): Int? {
        val root = parse(response) ?: return null
        val containers = containers(root)
        if (hasExplicitFailure(containers)) {
            return null
        }
        val equityList = containers
            .firstNotNullOfOrNull { it.optJSONArray("equityList") }
            ?: return null
        var count = 0
        for (index in 0 until equityList.length()) {
            val voucher = equityList.optJSONObject(index) ?: continue
            if (
                listOf(
                    voucher.optString("equityStatus"),
                    voucher.optString("equityVoucherStatus"),
                    voucher.optString("finEquityStatus")
                ).any { it.equals("CAN_USE", true) }
            ) {
                count++
            }
        }
        return count
    }

    fun isActionAccepted(response: String): Boolean {
        val root = parse(response) ?: return false
        val containers = containers(root)
        if (hasExplicitFailure(containers)) {
            return false
        }
        var markerFound = false
        for (container in containers) {
            if (container.has("success")) {
                markerFound = true
                if (!container.optBoolean("success", false)) {
                    return false
                }
            }
            for (key in listOf("resultCode", "code")) {
                if (!container.has(key)) {
                    continue
                }
                markerFound = true
                if (
                    container.optString(key).trim().uppercase() !in
                    successCodes
                ) {
                    return false
                }
            }
        }
        return markerFound
    }

    private fun parse(response: String): JSONObject? =
        runCatching { JSONObject(response) }.getOrNull()

    private fun containers(root: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val pending = ArrayDeque<JSONObject>()
        pending += root
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            result += current
            for (key in listOf("data", "result", "resultData")) {
                current.optJSONObject(key)?.let(pending::addLast)
            }
        }
        return result
    }

    private fun hasExplicitFailure(containers: List<JSONObject>): Boolean {
        for (container in containers) {
            if (
                container.has("success") &&
                !container.optBoolean("success", false)
            ) {
                return true
            }
            for (key in listOf("resultCode", "code")) {
                if (
                    container.has(key) &&
                    container.optString(key).trim().uppercase() !in
                    successCodes
                ) {
                    return true
                }
            }
        }
        return false
    }
}
