package fansirsqi.xposed.sesame.task.antSports

import org.json.JSONArray
import org.json.JSONObject

enum class NeverlandSignDecision {
    EXECUTE,
    CONFIRMED,
    RETRY
}

data class NeverlandTaskState(
    val id: String,
    val aliases: Set<String>,
    val type: String,
    val status: String,
    val title: String,
    val source: JSONObject
)

data class NeverlandTaskSnapshot(
    val recognized: Boolean,
    val tasks: List<NeverlandTaskState>
)

data class NeverlandBubbleRecord(
    val id: String,
    val status: String,
    val encryptValue: String,
    val source: JSONObject
)

data class NeverlandBubbleSnapshot(
    val recognized: Boolean,
    val records: List<NeverlandBubbleRecord>
)

object NeverlandPolicy {
    private val successCodes = setOf("SUCCESS", "100", "0")
    private val taskContainerKeys = listOf(
        "taskCenterTaskVOS",
        "taskInfoList",
        "taskList",
        "tasks"
    )
    private val bubbleContainerKeys = listOf(
        "bubbleTaskVOS",
        "bubbleList",
        "rewardRecords",
        "medEnergyBallInfoRecords",
        "taskInfos"
    )
    private val terminalTaskStatuses = setOf(
        "FINISHED",
        "RECEIVED",
        "DONE",
        "COMPLETED"
    )
    private val terminalBubbleStatuses = setOf(
        "RECEIVED",
        "FINISHED",
        "PICKED",
        "CLAIMED",
        "DONE",
        "COMPLETED"
    )

    fun signDecision(response: String): NeverlandSignDecision {
        val root = parseResponse(response) ?: return NeverlandSignDecision.RETRY
        if (
            root.optString("errorCode").equals("ALREADY_SIGN_IN", true) ||
            root.optString("errorMsg").contains("已签到")
        ) {
            return NeverlandSignDecision.CONFIRMED
        }
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return NeverlandSignDecision.RETRY
        }

        val signedToday = containers.asSequence()
            .mapNotNull { it.optJSONObject("continuousSignInfo") }
            .firstOrNull { it.has("signedToday") }
            ?.optBoolean("signedToday")
            ?: return NeverlandSignDecision.RETRY
        return if (signedToday) {
            NeverlandSignDecision.CONFIRMED
        } else {
            NeverlandSignDecision.EXECUTE
        }
    }

    fun parseTasks(response: String): NeverlandTaskSnapshot {
        val root = parseResponse(response)
            ?: return NeverlandTaskSnapshot(false, emptyList())
        return parseTasks(root)
    }

    fun parseTasks(root: JSONObject): NeverlandTaskSnapshot {
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return NeverlandTaskSnapshot(false, emptyList())
        }

        var recognized = false
        val tasks = mutableListOf<NeverlandTaskState>()
        for (container in containers) {
            for (key in taskContainerKeys) {
                if (!container.has(key)) {
                    continue
                }
                val array = container.optJSONArray(key) ?: continue
                recognized = true
                tasks += parseTaskArray(array)
            }
        }
        return NeverlandTaskSnapshot(recognized, tasks.distinctBy { it.id })
    }

    fun isTaskTransitionConfirmed(
        previousStatus: String,
        currentStatus: String?
    ): Boolean {
        val current = currentStatus.orEmpty().uppercase()
        return when (previousStatus.uppercase()) {
            "INIT", "SIGNUP_COMPLETE", "TODO", "WAIT_COMPLETE" ->
                current == "TO_RECEIVE" || current == "WAIT_RECEIVE" ||
                    current in terminalTaskStatuses
            "TO_RECEIVE", "WAIT_RECEIVE", "FINISHED" ->
                current == "RECEIVED" || current == "DONE" ||
                    current == "COMPLETED"
            else -> false
        }
    }

    fun taskAliases(task: JSONObject): Set<String> {
        return listOf("taskId", "id", "taskRecordId", "bizId")
            .map { task.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun parseBubbleRecords(response: String): NeverlandBubbleSnapshot {
        val root = parseResponse(response)
            ?: return NeverlandBubbleSnapshot(false, emptyList())
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return NeverlandBubbleSnapshot(false, emptyList())
        }

        var recognized = false
        val records = mutableListOf<NeverlandBubbleRecord>()
        for (container in containers) {
            for (key in bubbleContainerKeys) {
                if (!container.has(key)) {
                    continue
                }
                val array = container.optJSONArray(key) ?: continue
                recognized = true
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = firstNonBlank(
                        item,
                        "medEnergyBallInfoRecordId",
                        "recordId",
                        "taskRecordId",
                        "id"
                    )
                    val encryptValue = item.optString("encryptValue").trim()
                    if (id.isBlank() && encryptValue.isBlank()) {
                        continue
                    }
                    records += NeverlandBubbleRecord(
                        id = id,
                        status = firstNonBlank(
                            item,
                            "bubbleTaskStatus",
                            "rewardStatus",
                            "status"
                        ),
                        encryptValue = encryptValue,
                        source = item
                    )
                }
            }
        }
        return NeverlandBubbleSnapshot(
            recognized,
            records.distinctBy {
                if (it.id.isNotBlank()) "id:${it.id}"
                else "encrypt:${it.encryptValue}"
            }
        )
    }

    fun areBubbleClaimsConfirmed(
        claimedIds: Set<String>,
        response: String
    ): Boolean {
        if (claimedIds.isEmpty()) {
            return false
        }
        val snapshot = parseBubbleRecords(response)
        if (!snapshot.recognized) {
            return false
        }
        val statuses = snapshot.records.associate { it.id to it.status.uppercase() }
        return claimedIds.all { id ->
            val status = statuses[id]
            status == null || status in terminalBubbleStatuses
        }
    }

    fun isBubbleEncryptConfirmed(
        encryptValue: String,
        response: String
    ): Boolean {
        if (encryptValue.isBlank()) {
            return false
        }
        val root = parseResponse(response) ?: return false
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return false
        }

        var recognized = false
        for (container in containers) {
            for (key in bubbleContainerKeys) {
                if (!container.has(key)) {
                    continue
                }
                val array = container.optJSONArray(key) ?: continue
                recognized = true
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    if (item.optString("encryptValue") != encryptValue) {
                        continue
                    }
                    val status = firstNonBlank(
                        item,
                        "bubbleTaskStatus",
                        "rewardStatus",
                        "taskStatus",
                        "status"
                    ).uppercase()
                    if (status !in terminalBubbleStatuses) {
                        return false
                    }
                }
            }
        }
        return recognized
    }

    fun isTaskCenterConfirmedDone(response: JSONObject): Boolean {
        val snapshot = parseTasks(response)
        return snapshot.recognized &&
            snapshot.tasks.isNotEmpty() &&
            snapshot.tasks.all { it.status.uppercase() in terminalTaskStatuses }
    }

    private fun parseTaskArray(array: JSONArray): List<NeverlandTaskState> {
        val result = mutableListOf<NeverlandTaskState>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val task = item.optJSONObject("taskBaseInfo") ?: item
            val normalized = JSONObject(item.toString())
            if (task !== item) {
                val keys = task.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    normalized.put(key, task.opt(key))
                }
            }
            val aliases = taskAliases(task)
            val id = aliases.firstOrNull().orEmpty()
            if (id.isBlank()) {
                continue
            }
            result += NeverlandTaskState(
                id = id,
                aliases = aliases,
                type = firstNonBlank(task, "taskType", "type"),
                status = firstNonBlank(task, "taskStatus", "status"),
                title = firstNonBlank(
                    task,
                    "title",
                    "taskName",
                    "name"
                ),
                source = normalized
            )
        }
        return result
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

    private fun firstNonBlank(
        objectValue: JSONObject,
        vararg keys: String
    ): String {
        return keys.asSequence()
            .map { objectValue.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }
}
