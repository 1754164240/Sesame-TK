package fansirsqi.xposed.sesame.task.antFishPond

import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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

data class FishPondAdConfig(
    val querySpaceCode: String,
    val exposureSpaceCode: String,
    val pageUrl: String
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

    fun extractAdConfig(task: JSONObject): FishPondAdConfig {
        val targetUrl = task.optJSONObject("taskDisplayConfig")
            ?.optString("targetUrl")
            .orEmpty()
        val query = targetUrl.substringAfter('?', "")
        val params = query.split('&')
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    decode(entry.substring(0, separator)) to
                        decode(entry.substring(separator + 1))
                }
            }
            .toMap()
        return FishPondAdConfig(
            querySpaceCode = params["renderConfigKey"]
                .orEmpty()
                .ifBlank { DEFAULT_QUERY_SPACE_CODE },
            exposureSpaceCode = params["spaceCode"]
                .orEmpty()
                .ifBlank { DEFAULT_EXPOSURE_SPACE_CODE },
            pageUrl = params["url"]
                .orEmpty()
                .ifBlank { DEFAULT_PAGE_URL }
        )
    }

    fun adDurationMillis(response: JSONObject?, task: JSONObject): Long {
        val seconds = response
            ?.optJSONObject("resultData")
            ?.optDouble("duration", Double.NaN)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: response
                ?.optJSONObject("data")
                ?.optJSONObject("resultData")
                ?.optDouble("duration", Double.NaN)
                ?.takeIf { it.isFinite() && it > 0.0 }
        return if (seconds == null) {
            browseDurationMillis(task)
        } else {
            (seconds.coerceIn(MIN_BROWSE_SECONDS, MAX_BROWSE_SECONDS) * 1_000.0)
                .toLong()
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
        if (response.has("success") && !response.optBoolean("success", false)) {
            return false
        }
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

    private fun decode(value: String): String =
        runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)

    private const val DEFAULT_QUERY_SPACE_CODE =
        "adPosId#2024042922700095310##sceneCode#null##mediaScene#27##rewardNum#1##spaceCode#TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW##expCode#AntFishingStyleV2"
    private const val DEFAULT_EXPOSURE_SPACE_CODE =
        "TASK_ONE_TASK_GET_FISH_ROD_ONCE_DAY_NEW"
    private const val DEFAULT_PAGE_URL =
        "https://render.alipay.com/p/yuyan/180020010001256918/fishing-landing.html?caprMode=sync"
}
