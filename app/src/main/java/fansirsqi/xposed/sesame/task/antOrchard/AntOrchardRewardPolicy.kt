package fansirsqi.xposed.sesame.task.antOrchard

import org.json.JSONArray
import org.json.JSONObject

data class AntOrchardTaskState(
    val id: String,
    val groupId: String,
    val title: String,
    val status: String,
    val actionType: String,
    val sceneCode: String,
    val taskPlantType: String,
    val awardCount: Int,
    val source: JSONObject
)

data class AntOrchardTaskSnapshot(
    val recognized: Boolean,
    val tasks: List<AntOrchardTaskState>
)

data class AntOrchardLeyuanTask(
    val sceneCode: String,
    val taskType: String,
    val title: String,
    val status: String,
    val awardCount: Int
)

data class AntOrchardLeyuanSnapshot(
    val recognized: Boolean,
    val tasks: List<AntOrchardLeyuanTask>
)

enum class AntOrchardTaskDecision {
    COMPLETE,
    CLAIM,
    SKIP_UNSAFE,
    TERMINAL,
    RETRY
}

object AntOrchardRewardPolicy {
    const val LEYUAN_DAILY_TASK_SCENE_CODE =
        "ANTORCHARD_LEYUAN_DAILY_TASK"

    private val taskContainerKeys = listOf(
        "taskList",
        "orchardTaskList",
        "dailyTaskList",
        "tasks"
    )
    private val safeCompleteActions = setOf(
        "TRIGGER",
        "ADD_HOME",
        "PUSH_SUBSCRIBE"
    )
    private val unsafeSignals = listOf(
        "GAME",
        "XLIGHT",
        "VISIT",
        "ADVERT",
        "LIGHT_AD",
        "RECHARGE",
        "TOP_UP",
        "ORDER",
        "PURCHASE",
        "PAY",
        "MULTI_STAGE",
        "游戏",
        "广告",
        "充值",
        "下单",
        "购买",
        "支付"
    )

    fun parseLeyuanTasks(response: String): AntOrchardLeyuanSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return AntOrchardLeyuanSnapshot(false, emptyList())
        if (!isSuccess(root)) {
            return AntOrchardLeyuanSnapshot(false, emptyList())
        }
        val containers = listOfNotNull(
            root,
            root.optJSONObject("data"),
            root.optJSONObject("result")
        )
        for (container in containers) {
            val taskInfo = container.optJSONObject("taskTriggerPlayInfo")
                ?: continue
            val taskList = taskInfo.optJSONArray("taskList")
                ?: continue
            val tasks = mutableListOf<AntOrchardLeyuanTask>()
            for (index in 0 until taskList.length()) {
                val task = taskList.optJSONObject(index) ?: continue
                val awardCount = sequenceOf(
                    "awardCount",
                    "totalAwardCount",
                    "nextStageAwardCount"
                ).map { task.optInt(it, 0) }
                    .firstOrNull { it > 0 }
                    ?: 0
                tasks += AntOrchardLeyuanTask(
                    sceneCode = task.optString("sceneCode"),
                    taskType = task.optString("taskType"),
                    title = task.optString("title")
                        .ifBlank { task.optString("taskTitle") },
                    status = task.optString("taskStatus"),
                    awardCount = awardCount
                )
            }
            return AntOrchardLeyuanSnapshot(true, tasks)
        }
        return AntOrchardLeyuanSnapshot(false, emptyList())
    }

    fun isLeyuanClaimable(task: AntOrchardLeyuanTask): Boolean {
        return task.sceneCode == LEYUAN_DAILY_TASK_SCENE_CODE &&
            task.taskType.isNotBlank() &&
            task.status.equals("FINISHED", true) &&
            task.awardCount > 0
    }

    fun isLeyuanRewardConfirmed(
        response: String,
        sceneCode: String,
        taskType: String
    ): Boolean {
        if (sceneCode.isBlank() || taskType.isBlank()) {
            return false
        }
        val snapshot = parseLeyuanTasks(response)
        if (!snapshot.recognized) {
            return false
        }
        val task = snapshot.tasks.firstOrNull {
            it.sceneCode == sceneCode && it.taskType == taskType
        } ?: return true
        return task.status.equals("RECEIVED", true)
    }

    fun isLimitedRewardConfirmed(
        response: String,
        taskId: String
    ): Boolean {
        if (taskId.isBlank()) {
            return false
        }
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return false
        if (!isSuccess(root)) {
            return false
        }
        val containers = listOfNotNull(
            root,
            root.optJSONObject("data"),
            root.optJSONObject("result")
        )
        for (container in containers) {
            val challenge = container.optJSONObject("limitedTimeChallenge")
                ?: continue
            val tasks = challenge.optJSONArray("limitedTimeChallengeTasks")
                ?: continue
            for (index in 0 until tasks.length()) {
                val task = tasks.optJSONObject(index) ?: continue
                if (task.optString("taskId") == taskId) {
                    return task.optString("taskStatus")
                        .equals("RECEIVED", true)
                }
            }
            return true
        }
        return false
    }

    fun isActionAccepted(response: String): Boolean {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return false
        return isSuccess(root)
    }

    fun isCompletionConfirmed(response: String, taskId: String): Boolean {
        if (taskId.isBlank()) {
            return false
        }
        val snapshot = parseTasks(response)
        if (!snapshot.recognized) {
            return false
        }
        val task = snapshot.tasks.firstOrNull { it.id == taskId }
            ?: return false
        return task.status.uppercase() in setOf("FINISHED", "RECEIVED")
    }

    fun isRewardConfirmed(response: String, taskId: String): Boolean {
        if (taskId.isBlank()) {
            return false
        }
        val snapshot = parseTasks(response)
        if (!snapshot.recognized) {
            return false
        }
        val task = snapshot.tasks.firstOrNull { it.id == taskId }
            ?: return true
        return task.status.equals("RECEIVED", true)
    }

    fun decideTask(task: AntOrchardTaskState): AntOrchardTaskDecision {
        return when (task.status.uppercase()) {
            "FINISHED" -> AntOrchardTaskDecision.CLAIM
            "RECEIVED", "DONE", "COMPLETED" ->
                AntOrchardTaskDecision.TERMINAL
            "TODO" -> {
                if (task.id.isBlank() || task.sceneCode.isBlank()) {
                    return AntOrchardTaskDecision.RETRY
                }
                val targetUrl = task.source
                    .optJSONObject("taskDisplayConfig")
                    ?.optString("targetUrl")
                    .orEmpty()
                val riskText = listOf(
                    task.actionType,
                    task.title,
                    targetUrl
                ).joinToString(" ")
                if (
                    task.actionType.uppercase() !in safeCompleteActions ||
                    unsafeSignals.any {
                        riskText.contains(it, ignoreCase = true)
                    }
                ) {
                    AntOrchardTaskDecision.SKIP_UNSAFE
                } else {
                    AntOrchardTaskDecision.COMPLETE
                }
            }
            else -> AntOrchardTaskDecision.RETRY
        }
    }

    fun parseTasks(response: String): AntOrchardTaskSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return AntOrchardTaskSnapshot(false, emptyList())
        if (!isSuccess(root)) {
            return AntOrchardTaskSnapshot(false, emptyList())
        }
        val containers = listOfNotNull(
            root,
            root.optJSONObject("data"),
            root.optJSONObject("result")
        )
        var recognized = false
        val tasks = mutableListOf<AntOrchardTaskState>()
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
        return AntOrchardTaskSnapshot(recognized, tasks.distinctBy { it.id })
    }

    private fun parseTaskArray(array: JSONArray): List<AntOrchardTaskState> {
        val tasks = mutableListOf<AntOrchardTaskState>()
        for (index in 0 until array.length()) {
            val task = array.optJSONObject(index) ?: continue
            tasks += AntOrchardTaskState(
                id = task.optString("taskId"),
                groupId = task.optString("groupId"),
                title = task.optJSONObject("taskDisplayConfig")
                    ?.optString("title")
                    .orEmpty()
                    .ifBlank { task.optString("title") },
                status = task.optString("taskStatus"),
                actionType = task.optString("actionType"),
                sceneCode = task.optString("sceneCode"),
                taskPlantType = task.optString("taskPlantType"),
                awardCount = task.optInt("awardCount", 0),
                source = task
            )
        }
        return tasks
    }

    private fun isSuccess(root: JSONObject): Boolean {
        if (root.has("success")) {
            return root.optBoolean("success", false)
        }
        return root.optString("resultCode").uppercase() in
            setOf("SUCCESS", "100", "200", "0")
    }
}
