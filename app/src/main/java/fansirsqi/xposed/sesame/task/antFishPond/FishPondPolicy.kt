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
    private const val DEFAULT_BROWSE_SECONDS = 15.0
    private const val MIN_BROWSE_SECONDS = 1.0
    private const val MAX_BROWSE_SECONDS = 120.0
    private val durationPattern =
        Regex("""(\d+(?:\.\d+)?)\s*(?:秒|s\b)""", RegexOption.IGNORE_CASE)

    fun decideTask(snapshot: FishPondTaskSnapshot): FishPondTaskDecision {
        if (snapshot.type.isBlank() || snapshot.sceneCode.isBlank()) {
            return FishPondTaskDecision.SKIP
        }

        return when (snapshot.status.uppercase()) {
            "FINISHED", "TO_RECEIVE" -> FishPondTaskDecision.CLAIM
            "TODO", "TO_DO" -> {
                if (snapshot.actionType.equals("GOFISH", ignoreCase = true)) {
                    FishPondTaskDecision.WAIT
                } else {
                    FishPondTaskDecision.COMPLETE
                }
            }
            "RECEIVED", "DONE" -> FishPondTaskDecision.WAIT
            else -> FishPondTaskDecision.SKIP
        }
    }

    fun browseDurationMillis(task: JSONObject): Long {
        val displayConfig = task.optJSONObject("taskDisplayConfig")
        val configuredSeconds = displayConfig
            ?.optJSONObject("floatBallConfig")
            ?.optDouble("floatBallDuration", Double.NaN)
            ?.takeIf { it.isFinite() && it > 0.0 }
        val textSeconds = sequenceOf(
            displayConfig?.optString("desc"),
            displayConfig?.optString("title"),
            displayConfig?.optJSONObject("subTitle")?.optString("desc"),
            task.optString("taskTitle"),
            task.optString("title")
        )
            .filterNotNull()
            .mapNotNull { text ->
                durationPattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            }
            .firstOrNull()
        val seconds = (configuredSeconds ?: textSeconds ?: DEFAULT_BROWSE_SECONDS)
            .coerceIn(MIN_BROWSE_SECONDS, MAX_BROWSE_SECONDS)
        return (seconds * 1_000.0).toLong()
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
