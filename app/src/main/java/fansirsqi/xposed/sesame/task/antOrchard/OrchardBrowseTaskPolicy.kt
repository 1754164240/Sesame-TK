package fansirsqi.xposed.sesame.task.antOrchard

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class OrchardBrowseTaskSelection(
    val taskId: String,
    val source: String
)

enum class OrchardBrowseCompletionState {
    CONFIRMED,
    PENDING,
    UNKNOWN
}

object OrchardBrowseTaskPolicy {
    private const val TAOBAO_VISIT_GROUP_ID = "12172"
    private const val TAOBAO_VISIT_SCENE_CODE = "972"

    private val browseSignals = listOf(
        "逛",
        "浏览",
        "看看"
    )
    private val riskSignals = listOf(
        "GAME",
        "游戏",
        "ADVERT",
        "LIGHT_AD",
        "广告",
        "RECHARGE",
        "TOP_UP",
        "充值",
        "ORDER",
        "下单",
        "PURCHASE",
        "购买",
        "PAYMENT",
        "支付",
        "LOAN",
        "借贷",
        "WITHDRAW",
        "提现"
    )

    fun select(
        task: AntOrchardTaskState
    ): OrchardBrowseTaskSelection? {
        if (
            !task.status.equals("TODO", true) ||
            !task.actionType.equals("VISIT", true) ||
            !task.taskPlantType.equals("TAOBAO", true) ||
            task.groupId != TAOBAO_VISIT_GROUP_ID ||
            task.sceneCode != TAOBAO_VISIT_SCENE_CODE ||
            task.id.isBlank() ||
            browseSignals.none { task.title.contains(it) }
        ) {
            return null
        }
        val targetUrl = task.source
            .optJSONObject("taskDisplayConfig")
            ?.optString("targetUrl")
            .orEmpty()
        val source = resolveSource(targetUrl) ?: return null
        val riskText = listOf(
            task.actionType,
            task.taskPlantType,
            task.title,
            targetUrl,
            decodeRepeatedly(targetUrl),
            source
        ).joinToString(" ")
        if (riskSignals.any { riskText.contains(it, ignoreCase = true) }) {
            return null
        }
        return OrchardBrowseTaskSelection(task.id, source)
    }

    fun completionState(
        response: String,
        taskId: String
    ): OrchardBrowseCompletionState {
        if (taskId.isBlank()) {
            return OrchardBrowseCompletionState.UNKNOWN
        }
        val snapshot = AntOrchardRewardPolicy.parseTasks(response)
        if (!snapshot.recognized) {
            return OrchardBrowseCompletionState.UNKNOWN
        }
        val task = snapshot.tasks.firstOrNull { it.id == taskId }
            ?: return OrchardBrowseCompletionState.CONFIRMED
        return if (
            task.status.uppercase() in setOf("FINISHED", "RECEIVED")
        ) {
            OrchardBrowseCompletionState.CONFIRMED
        } else {
            OrchardBrowseCompletionState.PENDING
        }
    }

    fun isCompletionConfirmed(response: String, taskId: String): Boolean {
        return completionState(response, taskId) ==
            OrchardBrowseCompletionState.CONFIRMED
    }

    private fun resolveSource(targetUrl: String): String? {
        if (targetUrl.isBlank()) {
            return null
        }
        val directParameters = parseQuery(targetUrl)
        directParameters["source"]
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val nestedUrl = directParameters["url"]
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return parseQuery(nestedUrl)["source"]
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseQuery(url: String): Map<String, List<String>> {
        val rawQuery = runCatching { URI(url).rawQuery }.getOrNull()
            ?: return emptyMap()
        return rawQuery.split("&")
            .filter { it.isNotBlank() }
            .map { parameter ->
                val separator = parameter.indexOf('=')
                val rawName = if (separator >= 0) {
                    parameter.substring(0, separator)
                } else {
                    parameter
                }
                val rawValue = if (separator >= 0) {
                    parameter.substring(separator + 1)
                } else {
                    ""
                }
                decode(rawName) to decode(rawValue)
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun decodeRepeatedly(value: String): String {
        var decoded = value
        repeat(2) {
            val next = decode(decoded)
            if (next == decoded) {
                return decoded
            }
            decoded = next
        }
        return decoded
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }
}
