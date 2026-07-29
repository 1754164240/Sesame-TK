package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONArray
import org.json.JSONObject

enum class ForestTaskDecision {
    SIGN,
    COMPLETE_SAFE,
    CLAIM,
    TERMINAL,
    SKIP_UNSAFE,
    RETRY
}

data class ForestSignState(
    val stableKey: String,
    val signId: String,
    val sceneCode: String,
    val currentSignKey: String,
    val signed: Boolean,
    val awardCount: Int
)

data class ForestTaskState(
    val stableKey: String,
    val sceneCode: String,
    val taskType: String,
    val status: String,
    val title: String,
    val awardCount: Int = 0
)

data class ForestTaskSnapshot(
    val recognized: Boolean,
    val complete: Boolean,
    val signs: List<ForestSignState>,
    val tasks: List<ForestTaskState>
)

object ForestTaskPolicy {
    private val terminalStatuses = setOf(
        "RECEIVED",
        "COMPLETED",
        "COMPLETE",
        "DONE",
        "SUCCESS"
    )
    private val unsafeSignals = listOf(
        "GAME",
        "XLIGHT",
        "ADVERT",
        "LIGHT_AD",
        "ORDER",
        "PURCHASE",
        "BUY",
        "PAY",
        "RECHARGE",
        "TOP_UP",
        "LOAN",
        "BORROW",
        "WITHDRAW",
        "CASH",
        "游戏",
        "广告",
        "下单",
        "购买",
        "支付",
        "充值",
        "借款",
        "贷款",
        "提现",
        "现金"
    )
    private val safeAsciiSignals = listOf(
        "BROWSE",
        "VISIT",
        "DAKA",
        "SIGN",
        "READ",
        "ADD_HOME",
        "PUSH_SUBSCRIBE",
        "WATER"
    )
    private val safeTextSignals = listOf(
        "浏览",
        "签到",
        "阅读",
        "打卡",
        "浇水"
    )
    private val safeAsciiPatterns = safeAsciiSignals.map { signal ->
        Regex(
            "(^|[^A-Z0-9])${Regex.escape(signal)}([^A-Z0-9]|$)"
        )
    }

    fun parseSnapshot(response: String): ForestTaskSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return unknownSnapshot()
        if (!isRpcSuccess(root)) {
            return unknownSnapshot()
        }
        val payloads = knownPayloads(root)
        val recognizedPayloads = payloads.filter(::hasKnownContainer)
        if (recognizedPayloads.isEmpty()) {
            return unknownSnapshot()
        }

        var complete = true
        val signs = linkedMapOf<String, ForestSignState>()
        val tasks = linkedMapOf<String, ForestTaskState>()
        for (payload in recognizedPayloads) {
            complete = collectSigns(payload, signs) && complete
            complete = collectTasks(payload, tasks) && complete
        }
        return ForestTaskSnapshot(
            recognized = true,
            complete = complete,
            signs = signs.values.toList(),
            tasks = tasks.values.toList()
        )
    }

    fun mergeSnapshots(responses: List<String>): ForestTaskSnapshot {
        if (responses.isEmpty()) {
            return unknownSnapshot()
        }
        val snapshots = responses.map(::parseSnapshot)
        val recognized = snapshots.any { it.recognized }
        if (!recognized) {
            return unknownSnapshot()
        }
        val signs = linkedMapOf<String, ForestSignState>()
        val tasks = linkedMapOf<String, ForestTaskState>()
        snapshots.filter { it.recognized }.forEach { snapshot ->
            snapshot.signs.forEach { mergeSign(signs, it) }
            snapshot.tasks.forEach { mergeTask(tasks, it) }
        }
        return ForestTaskSnapshot(
            recognized = true,
            complete = snapshots.all { it.recognized && it.complete },
            signs = signs.values.toList(),
            tasks = tasks.values.toList()
        )
    }

    fun decide(task: ForestTaskState): ForestTaskDecision {
        if (task.taskType.isBlank() || task.sceneCode.isBlank()) {
            return ForestTaskDecision.RETRY
        }
        val status = task.status.uppercase()
        if (status in terminalStatuses) {
            return ForestTaskDecision.TERMINAL
        }
        if (containsUnsafeSignal(task)) {
            return ForestTaskDecision.SKIP_UNSAFE
        }
        return when (status) {
            "FINISHED" -> ForestTaskDecision.CLAIM
            "TODO" -> {
                if (containsSafeSignal(task)) {
                    ForestTaskDecision.COMPLETE_SAFE
                } else {
                    ForestTaskDecision.SKIP_UNSAFE
                }
            }
            else -> ForestTaskDecision.RETRY
        }
    }

    fun isCompletionConfirmed(
        before: ForestTaskState,
        after: ForestTaskState?
    ): Boolean {
        if (after == null) {
            return false
        }
        val afterStatus = after.status.uppercase()
        return when (before.status.uppercase()) {
            "TODO" -> afterStatus == "FINISHED" || afterStatus in terminalStatuses
            "FINISHED" -> afterStatus in terminalStatuses
            else -> false
        }
    }

    fun isTerminalStatus(status: String): Boolean {
        return status.uppercase() in terminalStatuses
    }

    internal fun isRpcSuccess(root: JSONObject): Boolean {
        if (root.has("success")) {
            return root.optBoolean("success", false)
        }
        return root.optString("resultCode").uppercase() in
            setOf("SUCCESS", "100", "200")
    }

    private fun collectSigns(
        payload: JSONObject,
        signs: MutableMap<String, ForestSignState>
    ): Boolean {
        var complete = true
        val signArrays = mutableListOf<JSONArray>()
        payload.optJSONArray("forestSignVOList")?.let(signArrays::add)
        payload.optJSONObject("energySignVO")?.let {
            signArrays += JSONArray().put(it)
        }
        payload.optJSONObject("forestSignVO")?.let {
            signArrays += JSONArray().put(it)
        }
        for (array in signArrays) {
            for (index in 0 until array.length()) {
                val signObject = array.optJSONObject(index)
                if (signObject == null) {
                    complete = false
                    continue
                }
                val signState = parseSign(signObject)
                if (signState == null) {
                    complete = false
                } else {
                    mergeSign(signs, signState)
                }
            }
        }
        return complete
    }

    private fun parseSign(signObject: JSONObject): ForestSignState? {
        val signId = signObject.optString("signId")
        val sceneCode = signObject.optString("sceneCode")
        val currentSignKey = signObject.optString("currentSignKey")
        val records = signObject.optJSONArray("signRecords") ?: return null
        if (
            signId.isBlank() ||
            sceneCode.isBlank() ||
            currentSignKey.isBlank()
        ) {
            return null
        }
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (record.optString("signKey") != currentSignKey) {
                continue
            }
            return ForestSignState(
                stableKey = "$signId#$currentSignKey",
                signId = signId,
                sceneCode = sceneCode,
                currentSignKey = currentSignKey,
                signed = record.optBoolean("signed", false),
                awardCount = record.optInt("awardCount", 0)
            )
        }
        return null
    }

    private fun collectTasks(
        payload: JSONObject,
        tasks: MutableMap<String, ForestTaskState>
    ): Boolean {
        var complete = true
        payload.optJSONArray("taskInfoList")?.let {
            complete = collectTaskArray(it, tasks) && complete
        }
        payload.optJSONArray("forestTasksNew")?.let { forestTasks ->
            for (index in 0 until forestTasks.length()) {
                val group = forestTasks.optJSONObject(index)
                val taskList = group?.optJSONArray("taskInfoList")
                if (taskList == null) {
                    complete = false
                } else {
                    complete = collectTaskArray(taskList, tasks) && complete
                }
            }
        }
        payload.optJSONArray("taskGroupInfoList")?.let { taskGroups ->
            for (index in 0 until taskGroups.length()) {
                val group = taskGroups.optJSONObject(index)
                val taskList = group?.optJSONArray("taskInfoList")
                if (taskList == null) {
                    complete = false
                } else {
                    complete = collectTaskArray(taskList, tasks) && complete
                }
            }
        }
        return complete
    }

    private fun collectTaskArray(
        taskArray: JSONArray,
        tasks: MutableMap<String, ForestTaskState>
    ): Boolean {
        var complete = true
        for (index in 0 until taskArray.length()) {
            val taskObject = taskArray.optJSONObject(index)
            if (taskObject == null) {
                complete = false
                continue
            }
            val task = parseTask(taskObject)
            if (task == null) {
                complete = false
            } else {
                mergeTask(tasks, task)
            }
            taskObject.optJSONArray("childTaskTypeList")?.let { children ->
                complete = collectTaskArray(children, tasks) && complete
            }
        }
        return complete
    }

    private fun parseTask(taskObject: JSONObject): ForestTaskState? {
        val baseInfo = taskObject.optJSONObject("taskBaseInfo") ?: return null
        val sceneCode = baseInfo.optString("sceneCode")
        val taskType = baseInfo.optString("taskType")
        val status = baseInfo.optString("taskStatus")
        if (sceneCode.isBlank() || taskType.isBlank() || status.isBlank()) {
            return null
        }
        val bizInfo = parseObject(baseInfo.opt("bizInfo"))
        val rights = parseObject(taskObject.opt("taskRights"))
        val title = sequenceOf(
            bizInfo?.optString("taskTitle"),
            bizInfo?.optString("title"),
            baseInfo.optString("taskTitle"),
            taskType
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        return ForestTaskState(
            stableKey = "$sceneCode#$taskType",
            sceneCode = sceneCode,
            taskType = taskType,
            status = status,
            title = title,
            awardCount = rights?.optInt("awardCount", 0) ?: 0
        )
    }

    private fun parseObject(value: Any?): JSONObject? {
        return when (value) {
            is JSONObject -> value
            is String -> {
                if (value.isBlank()) {
                    null
                } else {
                    runCatching { JSONObject(value) }.getOrNull()
                }
            }
            else -> null
        }
    }

    private fun knownPayloads(root: JSONObject): List<JSONObject> {
        val payloads = mutableListOf(root)
        var current = root
        val visited = mutableSetOf<Int>()
        while (visited.add(System.identityHashCode(current))) {
            val nested = sequenceOf("data", "result", "resultData")
                .mapNotNull { current.optJSONObject(it) }
                .firstOrNull()
                ?: break
            payloads += nested
            current = nested
        }
        return payloads
    }

    private fun hasKnownContainer(payload: JSONObject): Boolean {
        return listOf(
            "taskInfoList",
            "forestTasksNew",
            "taskGroupInfoList",
            "forestSignVOList",
            "energySignVO",
            "forestSignVO"
        ).any(payload::has)
    }

    private fun containsUnsafeSignal(task: ForestTaskState): Boolean {
        val riskText = "${task.taskType} ${task.title}"
        return unsafeSignals.any {
            riskText.contains(it, ignoreCase = true)
        }
    }

    private fun containsSafeSignal(task: ForestTaskState): Boolean {
        val safeText = "${task.taskType} ${task.title}"
        return safeTextSignals.any(safeText::contains) ||
            safeAsciiPatterns.any { it.containsMatchIn(safeText.uppercase()) }
    }

    private fun mergeSign(
        signs: MutableMap<String, ForestSignState>,
        candidate: ForestSignState
    ) {
        val current = signs[candidate.stableKey]
        if (current == null || candidate.signed && !current.signed) {
            signs[candidate.stableKey] = candidate
        }
    }

    private fun mergeTask(
        tasks: MutableMap<String, ForestTaskState>,
        candidate: ForestTaskState
    ) {
        val current = tasks[candidate.stableKey]
        if (
            current == null ||
            taskProgressRank(candidate.status) >
            taskProgressRank(current.status)
        ) {
            tasks[candidate.stableKey] = candidate
        }
    }

    private fun taskProgressRank(status: String): Int {
        val normalized = status.uppercase()
        return when {
            normalized in terminalStatuses -> 3
            normalized == "FINISHED" -> 2
            normalized == "TODO" -> 1
            else -> 0
        }
    }

    private fun unknownSnapshot(): ForestTaskSnapshot {
        return ForestTaskSnapshot(
            recognized = false,
            complete = false,
            signs = emptyList(),
            tasks = emptyList()
        )
    }
}
