package fansirsqi.xposed.sesame.task.youthPrivilege

import org.json.JSONArray
import org.json.JSONObject

enum class YouthCheckInDecision {
    EXECUTE,
    CONFIRMED,
    RETRY
}

enum class YouthRewardDecision {
    CLAIM,
    CONFIRMED,
    RETRY
}

object YouthPrivilegePolicy {
    private val successCodes = setOf("SUCCESS", "100", "0")

    fun isRpcSuccess(response: String): Boolean {
        val root = parseResponse(response) ?: return false
        return isRpcSuccess(responseContainers(root))
    }

    fun checkInDecision(response: String): YouthCheckInDecision {
        val root = parseResponse(response) ?: return YouthCheckInDecision.RETRY
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return YouthCheckInDecision.RETRY
        }

        val action = containers.asSequence()
            .mapNotNull { it.optJSONObject("studentCheckInInfo") }
            .map { it.optString("action").trim().uppercase() }
            .firstOrNull { it.isNotEmpty() }
            ?: return YouthCheckInDecision.RETRY

        return when (action) {
            "CHECK_IN_ACTION" -> YouthCheckInDecision.EXECUTE
            "CHECKED_IN_ACTION" -> YouthCheckInDecision.CONFIRMED
            else -> YouthCheckInDecision.RETRY
        }
    }

    fun rewardDecision(response: String, taskType: String): YouthRewardDecision {
        if (taskType.isBlank()) {
            return YouthRewardDecision.RETRY
        }

        val root = parseResponse(response) ?: return YouthRewardDecision.RETRY
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return YouthRewardDecision.RETRY
        }

        val statuses = containers
            .mapNotNull { it.optJSONArray("forestTasksNew") }
            .flatMap(::taskBaseInfoList)
            .filter { it.optString("taskType") == taskType }
            .map { it.optString("taskStatus").trim().uppercase() }

        return when {
            statuses.any { it == "RECEIVED" } -> YouthRewardDecision.CONFIRMED
            statuses.any { it == "FINISHED" } -> YouthRewardDecision.CLAIM
            else -> YouthRewardDecision.RETRY
        }
    }

    private fun parseResponse(response: String): JSONObject? {
        return try {
            JSONObject(response)
        } catch (_: Exception) {
            null
        }
    }

    private fun responseContainers(root: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val pending = ArrayDeque<JSONObject>()
        pending.add(root)

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            result.add(current)
            listOf("data", "result").forEach { key ->
                current.optJSONObject(key)?.let(pending::addLast)
            }
        }
        return result
    }

    private fun isRpcSuccess(containers: List<JSONObject>): Boolean {
        var hasSuccessMarker = false
        for (container in containers) {
            if (container.has("success")) {
                hasSuccessMarker = true
                if (!container.optBoolean("success", false)) {
                    return false
                }
            }

            for (key in listOf("resultCode", "code")) {
                if (!container.has(key)) {
                    continue
                }
                hasSuccessMarker = true
                val code = container.optString(key).trim().uppercase()
                if (code !in successCodes) {
                    return false
                }
            }
        }
        return hasSuccessMarker
    }

    private fun taskBaseInfoList(groups: JSONArray): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (groupIndex in 0 until groups.length()) {
            val taskInfoList = groups.optJSONObject(groupIndex)
                ?.optJSONArray("taskInfoList")
                ?: continue
            for (taskIndex in 0 until taskInfoList.length()) {
                taskInfoList.optJSONObject(taskIndex)
                    ?.optJSONObject("taskBaseInfo")
                    ?.let(result::add)
            }
        }
        return result
    }
}
