package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject

enum class GoldTicketOutcome {
    CONFIRMED,
    NO_ACTION,
    RETRY
}

data class GoldTicketSignSnapshot(
    val recognized: Boolean,
    val signed: Boolean
)

data class GoldTicketBalanceSnapshot(
    val recognized: Boolean,
    val availableAmount: Int
)

object GoldTicketPolicy {

    fun parseSign(response: String): GoldTicketSignSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return GoldTicketSignSnapshot(false, false)
        if (!isSuccess(root)) {
            return GoldTicketSignSnapshot(false, false)
        }
        val sign = root.optJSONObject("result")
            ?.optJSONObject("sign")
            ?: return GoldTicketSignSnapshot(false, false)
        if (
            !sign.has("todayHasSigned") ||
            sign.opt("todayHasSigned") !is Boolean
        ) {
            return GoldTicketSignSnapshot(false, false)
        }
        return GoldTicketSignSnapshot(
            recognized = true,
            signed = sign.optBoolean("todayHasSigned")
        )
    }

    fun parseBalance(response: String): GoldTicketBalanceSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return GoldTicketBalanceSnapshot(false, 0)
        if (!isSuccess(root)) {
            return GoldTicketBalanceSnapshot(false, 0)
        }
        val assetInfo = root.optJSONObject("result")
            ?.optJSONObject("assetInfo")
            ?: return GoldTicketBalanceSnapshot(false, 0)
        if (
            !assetInfo.has("availableAmount") ||
            assetInfo.isNull("availableAmount")
        ) {
            return GoldTicketBalanceSnapshot(false, 0)
        }
        val amount = assetInfo.optString("availableAmount")
            .toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: return GoldTicketBalanceSnapshot(false, 0)
        return GoldTicketBalanceSnapshot(true, amount)
    }

    fun isActionAccepted(response: String): Boolean {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return false
        return isSuccess(root)
    }

    private fun isSuccess(root: JSONObject): Boolean {
        if (root.has("success")) {
            return root.optBoolean("success", false)
        }
        return root.optString("resultCode").uppercase() in
            setOf("SUCCESS", "100", "200", "0")
    }
}
