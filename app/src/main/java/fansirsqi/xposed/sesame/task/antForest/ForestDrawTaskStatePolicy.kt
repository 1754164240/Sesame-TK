package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONObject

enum class ForestDrawCompletionDecision {
    CONFIRMED,
    RETRY
}

object ForestDrawTaskStatePolicy {
    fun isTransitionConfirmed(
        previousStatus: String,
        currentStatus: String?
    ): Boolean {
        return when (previousStatus.uppercase()) {
            "TODO" -> currentStatus.equals("FINISHED", true) ||
                currentStatus.equals("RECEIVED", true)
            "FINISHED" -> currentStatus.equals("RECEIVED", true)
            else -> false
        }
    }

    fun taskStatus(
        response: JSONObject,
        sceneCode: String,
        taskType: String
    ): String? {
        if (!isRpcSuccess(response)) {
            return null
        }
        val taskList = response.optJSONArray("taskInfoList") ?: return null
        for (index in 0 until taskList.length()) {
            val baseInfo = taskList.optJSONObject(index)
                ?.optJSONObject("taskBaseInfo")
                ?: continue
            if (baseInfo.optString("taskType") != taskType) {
                continue
            }
            val responseSceneCode = baseInfo.optString("sceneCode")
            if (
                sceneCode.isNotBlank() &&
                responseSceneCode.isNotBlank() &&
                responseSceneCode != sceneCode
            ) {
                continue
            }
            return baseInfo.optString("taskStatus").takeIf { it.isNotBlank() }
        }
        return null
    }

    fun completionDecision(response: JSONObject): ForestDrawCompletionDecision {
        if (!isRpcSuccess(response)) {
            return ForestDrawCompletionDecision.RETRY
        }
        val taskList = response.optJSONArray("taskInfoList")
            ?: return ForestDrawCompletionDecision.RETRY
        if (taskList.length() == 0) {
            return ForestDrawCompletionDecision.RETRY
        }

        var eligibleCount = 0
        for (index in 0 until taskList.length()) {
            val baseInfo = taskList.optJSONObject(index)
                ?.optJSONObject("taskBaseInfo")
                ?: return ForestDrawCompletionDecision.RETRY
            val taskType = baseInfo.optString("taskType")
            val taskStatus = baseInfo.optString("taskStatus")
            if (taskType.isBlank() || taskStatus.isBlank()) {
                return ForestDrawCompletionDecision.RETRY
            }
            val taskName = parseTaskName(baseInfo, taskType)
            if (
                ForestDrawTaskPolicy.actionFor(taskType, taskName) ==
                ForestDrawTaskAction.WAIT_FOR_CAPTURE
            ) {
                continue
            }
            eligibleCount += 1
            if (!taskStatus.equals("RECEIVED", true)) {
                return ForestDrawCompletionDecision.RETRY
            }
        }

        return if (eligibleCount > 0) {
            ForestDrawCompletionDecision.CONFIRMED
        } else {
            ForestDrawCompletionDecision.RETRY
        }
    }

    private fun parseTaskName(baseInfo: JSONObject, fallback: String): String {
        val bizInfo = baseInfo.optString("bizInfo")
        if (bizInfo.isBlank()) {
            return fallback
        }
        return runCatching { JSONObject(bizInfo).optString("title", fallback) }
            .getOrDefault(fallback)
    }

    private fun isRpcSuccess(response: JSONObject): Boolean {
        if (response.has("success")) {
            return response.optBoolean("success", false)
        }
        return response.optString("resultCode")
            .equals("SUCCESS", ignoreCase = true)
    }
}
