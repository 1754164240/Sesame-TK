package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ChouChouLeProtocol {
    private const val IP_DRAW = "ipDraw"
    private const val DAILY_DRAW = "dailyDraw"
    private const val IP_SOURCE = "ip_ccl"
    private const val DEFAULT_SOURCE = "antfarm_villa"
    private const val DEFAULT_BROWSE_WAIT_MILLIS = 15_000L
    private const val MAX_BROWSE_WAIT_SECONDS = 120.0

    fun discoverDrawTypes(
        nowMillis: Long = System.currentTimeMillis(),
        queryActivity: (scene: String, otherScene: String, source: String) -> String
    ): List<String> {
        return listOf(
            Triple(IP_DRAW, "ipDrawMachine", "dailyDrawMachine"),
            Triple(DAILY_DRAW, "dailyDrawMachine", "ipDrawMachine")
        ).mapNotNull { (drawType, scene, otherScene) ->
            val response = runCatching {
                queryActivity(scene, otherScene, sourceFor(drawType))
            }.getOrNull() ?: return@mapNotNull null
            drawType.takeIf { isActivityAvailable(response, nowMillis) }
        }
    }

    fun sourceFor(drawType: String): String {
        return if (drawType == IP_DRAW) IP_SOURCE else DEFAULT_SOURCE
    }

    fun isActivityAvailable(response: String, nowMillis: Long): Boolean {
        val root = runCatching { JSONObject(response) }.getOrNull() ?: return false
        if (!root.optBoolean("success", false) && root.optString("resultCode") != "100") {
            return false
        }
        val activity = root.optJSONObject("drawMachineActivity") ?: return false
        if (activity.optString("activityId").isBlank()) return false
        val endTime = activity.optLong("endTime", 0L)
        return endTime <= 0L || nowMillis <= endTime
    }

    fun executeBrowseTask(
        targetUrl: String,
        description: String,
        queryLayer: (spaceCode: String) -> String,
        sleeper: (waitMillis: Long) -> Unit,
        finishTask: () -> String
    ): Boolean {
        val layerSpaceCode = extractLayerSpaceCode(targetUrl)
        val layerResponse = if (layerSpaceCode.isNullOrBlank()) {
            null
        } else {
            runCatching { queryLayer(layerSpaceCode) }.getOrNull()
        }
        sleeper(resolveBrowseWaitMillis(layerResponse, description))
        val response = runCatching { JSONObject(finishTask()) }.getOrNull() ?: return false
        return response.optBoolean("success", false) ||
            response.optString("code") == "100000000" ||
            response.optString("resultCode") in setOf("100", "SUCCESS")
    }

    fun extractSpaceCode(targetUrl: String): String? {
        val decoded = runCatching {
            URLDecoder.decode(targetUrl, StandardCharsets.UTF_8.name())
        }.getOrDefault(targetUrl)
        return Regex("(?:[?&])spaceCode=([^&#]+)")
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
    }

    private fun extractLayerSpaceCode(targetUrl: String): String? {
        val decoded = runCatching {
            URLDecoder.decode(targetUrl, StandardCharsets.UTF_8.name())
        }.getOrDefault(targetUrl)
        val renderConfigKey = Regex("(?:[?&])renderConfigKey=([^&]+)")
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
        if (renderConfigKey != null) return renderConfigKey

        return extractSpaceCode(targetUrl)?.let { spaceCode ->
            "mediaScene#27##adPosId#2025042822702040737##spaceCode#$spaceCode"
        }
    }

    private fun resolveBrowseWaitMillis(layerResponse: String?, description: String): Long {
        val durationSeconds = layerResponse
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.takeIf { it.optBoolean("success", false) }
            ?.optJSONObject("resultData")
            ?.optDouble("duration", Double.NaN)
            ?.takeIf { it.isFinite() && it > 0.0 && it <= MAX_BROWSE_WAIT_SECONDS }
        if (durationSeconds != null) {
            return (durationSeconds * 1_000.0).toLong()
        }

        val descriptionSeconds = Regex("(\\d+)\\s*s", RegexOption.IGNORE_CASE)
            .find(description)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it in 1..MAX_BROWSE_WAIT_SECONDS.toLong() }
        return descriptionSeconds?.times(1_000L) ?: DEFAULT_BROWSE_WAIT_MILLIS
    }
}
