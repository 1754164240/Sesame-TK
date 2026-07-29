package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONArray
import org.json.JSONObject

data class FarmRewardCandidate(
    val id: String,
    val amount: Int
)

object AntFarmRewardPolicy {
    private val successCodes = setOf("SUCCESS", "100", "200", "0")
    private val blockedChouAdTaskIds = setOf(
        "SHANGYEHUA_DAILY_DRAW_TIMES",
        "IP_SHANGYEHUA_TASK"
    )

    fun shouldExecuteChouTask(taskId: String): Boolean =
        taskId !in blockedChouAdTaskIds

    fun selectWithinCapacity(
        candidates: List<FarmRewardCandidate>,
        remainingCapacity: Int
    ): List<FarmRewardCandidate> {
        var remaining = remainingCapacity.coerceAtLeast(0)
        val selected = mutableListOf<FarmRewardCandidate>()
        for (candidate in candidates) {
            if (
                candidate.id.isBlank() ||
                candidate.amount <= 0 ||
                candidate.amount > remaining
            ) {
                continue
            }
            selected += candidate
            remaining -= candidate.amount
        }
        return selected
    }

    fun isTaskReceived(response: String, taskId: String): Boolean {
        if (taskId.isBlank()) {
            return false
        }
        val root = parseResponse(response) ?: return false
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return false
        }
        val taskArrays = findArrays(containers, "farmTaskList")
        if (taskArrays.isEmpty()) {
            return false
        }
        for (array in taskArrays) {
            for (index in 0 until array.length()) {
                val task = array.optJSONObject(index) ?: continue
                if (task.optString("taskId") == taskId) {
                    return task.optString("taskStatus")
                        .equals("RECEIVED", true)
                }
            }
        }
        return false
    }

    fun isFamilySignConfirmed(response: String): Boolean {
        val root = parseResponse(response) ?: return false
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return false
        }
        val state = containers.firstOrNull { it.has("familySignTips") }
            ?: return false
        return !state.optBoolean("familySignTips", true)
    }

    fun isFamilyAwardConfirmed(
        response: String,
        rightId: String
    ): Boolean {
        if (rightId.isBlank()) {
            return false
        }
        val root = parseResponse(response) ?: return false
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return false
        }
        val arrays = findArrays(containers, "familyAwardRecordList")
        if (arrays.isEmpty()) {
            return false
        }
        for (array in arrays) {
            for (index in 0 until array.length()) {
                val record = array.optJSONObject(index) ?: continue
                if (record.optString("rightId") == rightId) {
                    return record.optBoolean("received", false)
                }
            }
        }
        return true
    }

    fun isParadiseRewardConfirmed(
        response: String,
        taskType: String
    ): Boolean {
        if (taskType.isBlank()) {
            return false
        }
        val root = parseResponse(response) ?: return false
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return false
        }

        val arrays = mutableListOf<JSONArray>()
        for (container in containers) {
            container.optJSONObject("taskTriggerPlayInfo")
                ?.optJSONArray("taskList")
                ?.let(arrays::add)
        }
        if (arrays.isEmpty()) {
            return false
        }

        for (array in arrays) {
            for (index in 0 until array.length()) {
                val task = array.optJSONObject(index) ?: continue
                if (task.optString("taskType") != taskType) {
                    continue
                }
                return task.optString("taskStatus").uppercase() in
                    setOf("RECEIVED", "DONE", "COMPLETED")
            }
        }
        return true
    }

    private fun parseResponse(response: String): JSONObject? {
        return runCatching { JSONObject(response) }.getOrNull()
    }

    private fun responseContainers(root: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val pending = ArrayDeque<JSONObject>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            result += current
            listOf("data", "result").forEach { key ->
                current.optJSONObject(key)?.let(pending::addLast)
            }
        }
        return result
    }

    private fun findArrays(
        containers: List<JSONObject>,
        key: String
    ): List<JSONArray> {
        return containers.mapNotNull { it.optJSONArray(key) }
    }

    private fun isRpcSuccess(containers: List<JSONObject>): Boolean {
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
}
