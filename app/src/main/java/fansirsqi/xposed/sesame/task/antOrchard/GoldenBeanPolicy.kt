package fansirsqi.xposed.sesame.task.antOrchard

import org.json.JSONObject

enum class GoldenBeanTaskDecision {
    FORTUNE_DRAW,
    COMPLETE,
    CLAIM,
    WAIT,
    SKIP
}

data class GoldenBeanTaskSnapshot(
    val type: String,
    val sceneCode: String,
    val status: String,
    val actionType: String,
    val title: String
)

object GoldenBeanPolicy {
    private val passiveTaskKeywords = listOf(
        "XIANSHANGZHIFU",
        "XIANXIAZHIFU",
        "YUEBAO",
        "线上支付",
        "到店支付",
        "余额宝"
    )
    private val exchangeKeywords = listOf(
        "MANURE_EXCHANGE",
        "肥料兑换"
    )
    private val gameKeywords = listOf(
        "GOLDENBEAN_GAME_",
        "JINDOULEYUAN",
        "GOLDEN_BEAN_TASK_WAKUANG",
        "游戏",
        "乐园",
        "闯关",
        "挑战"
    )

    fun decide(snapshot: GoldenBeanTaskSnapshot): GoldenBeanTaskDecision {
        val status = snapshot.status.uppercase()
        if (snapshot.type.isBlank() || snapshot.sceneCode.isBlank()) {
            return GoldenBeanTaskDecision.SKIP
        }
        if (status == "FINISHED" || status == "TO_RECEIVE") {
            return GoldenBeanTaskDecision.CLAIM
        }
        if (status == "RECEIVED" || status == "DONE") {
            return GoldenBeanTaskDecision.WAIT
        }
        if (status != "TODO" && status != "TO_DO") {
            return GoldenBeanTaskDecision.SKIP
        }

        val searchable =
            "${snapshot.type}|${snapshot.actionType}|${snapshot.title}".uppercase()
        val taskIdentity =
            "${snapshot.type}|${snapshot.actionType}".uppercase()
        if (passiveTaskKeywords.any(searchable::contains)) {
            return GoldenBeanTaskDecision.WAIT
        }
        if (exchangeKeywords.any(searchable::contains)) {
            return GoldenBeanTaskDecision.SKIP
        }
        if (snapshot.type.equals("FORTUNE_DRAW", ignoreCase = true) ||
            snapshot.actionType.equals("FORTUNE_DRAW", ignoreCase = true)
        ) {
            return GoldenBeanTaskDecision.FORTUNE_DRAW
        }
        if (snapshot.actionType.equals("PUSH_SUBSCRIBE", ignoreCase = true) ||
            snapshot.actionType.equals("GAMECENTER_TRIGGER", ignoreCase = true) ||
            gameKeywords.any(taskIdentity::contains)
        ) {
            return GoldenBeanTaskDecision.COMPLETE
        }
        return GoldenBeanTaskDecision.SKIP
    }

    fun isRpcSuccess(response: JSONObject): Boolean {
        if (response.has("success") && !response.optBoolean("success", false)) {
            return false
        }
        if (response.optBoolean("success", false)) {
            return true
        }
        val successCodes = setOf("100", "SUCCESS", "100000000", "0")
        if (response.optString("resultCode").uppercase() in successCodes ||
            response.optString("code").uppercase() in successCodes
        ) {
            return true
        }
        return response.optJSONObject("result")?.let(::isRpcSuccess) == true
    }
}
