package fansirsqi.xposed.sesame.task.antFishPond

import org.json.JSONObject

enum class FishPondTaskDecision {
    CLAIM,
    COMPLETE,
    WAIT,
    SKIP
}

data class FishPondTaskSnapshot(
    val type: String,
    val sceneCode: String,
    val status: String,
    val title: String,
    val adBizNo: String,
    val actionType: String
)

object FishPondPolicy {
    private val unsafeWords = listOf(
        "广告",
        "分享",
        "订阅",
        "邀请",
        "游戏"
    )
    private val unsafeTypeTokens = setOf(
        "AD",
        "ADVERT",
        "ADVERTISEMENT",
        "SHARE",
        "SUBSCRIBE",
        "INVITE",
        "GAME"
    )

    fun decideTask(snapshot: FishPondTaskSnapshot): FishPondTaskDecision {
        val typeTokens = snapshot.type
            .uppercase()
            .split(Regex("[^A-Z0-9]+"))
            .filter(String::isNotBlank)
        val unsafeTask = snapshot.adBizNo.isNotBlank() ||
            unsafeWords.any { snapshot.title.contains(it, ignoreCase = true) } ||
            typeTokens.any(unsafeTypeTokens::contains)
        if (unsafeTask) {
            return FishPondTaskDecision.SKIP
        }

        val actionType = snapshot.actionType.uppercase()
        val knownSafeTask = snapshot.sceneCode == "ANTFISHPOND_TASK" &&
            snapshot.type.startsWith("FISH_TASK_") &&
            actionType in setOf("VISIT", "GOFISH")
        if (!knownSafeTask) {
            return FishPondTaskDecision.SKIP
        }

        return when (snapshot.status.uppercase()) {
            "FINISHED", "TO_RECEIVE" -> FishPondTaskDecision.CLAIM
            "TODO", "TO_DO" -> {
                if (actionType == "VISIT") {
                    FishPondTaskDecision.COMPLETE
                } else {
                    FishPondTaskDecision.WAIT
                }
            }
            "RECEIVED", "DONE" -> FishPondTaskDecision.WAIT
            else -> FishPondTaskDecision.SKIP
        }
    }

    fun canContinueFishing(
        rodCount: Int,
        todayCount: Int,
        dailyLimit: Int,
        hasRiskToken: Boolean
    ): Boolean {
        return rodCount > 0 &&
            hasRiskToken &&
            (dailyLimit == 0 || todayCount < dailyLimit)
    }

    fun isRpcSuccess(response: JSONObject): Boolean {
        if (response.optBoolean("success", false)) {
            return true
        }

        val successCodes = setOf("SUCCESS", "100", "200", "0")
        if (response.optString("resultCode").uppercase() in successCodes ||
            response.optString("code").uppercase() in successCodes
        ) {
            return true
        }

        return response.optJSONObject("result")?.let(::isRpcSuccess) == true
    }
}
