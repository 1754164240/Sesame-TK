package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject

data class GameCenterTaskSnapshot(
    val recognized: Boolean,
    val tasks: List<JSONObject>
)

enum class GameCenterTaskDecision {
    SIGN_UP,
    SEND,
    CLAIM_ONLY,
    TERMINAL,
    SKIP_REAL_GAME,
    SKIP_AD,
    SKIP_FINANCIAL,
    SKIP_UNSUPPORTED
}

object GameCenterTaskPolicy {
    private val successCodes = setOf("SUCCESS", "100", "200", "0")
    private val terminalStatuses = setOf(
        "AWARDED",
        "RECEIVED",
        "DONE",
        "COMPLETED",
        "COMPLETE",
        "FINISHED"
    )
    private val financialKeywords = setOf(
        "提现",
        "借款",
        "借一笔",
        "借呗",
        "贷款",
        "充值",
        "下单",
        "购买",
        "支付",
        "现金兑换"
    )
    private val gameplayKeywords = setOf(
        "通关",
        "击杀",
        "挑战",
        "订单",
        "主线",
        "夜市",
        "庙会",
        "BOSS",
        "玩游戏",
        "完成游戏"
    )

    fun classifyPlatformTask(task: JSONObject): GameCenterTaskDecision {
        val status = task.optString("taskStatus").uppercase()
        val buttonText = task.optString("buttonText")
        if (isFinancialTask(task)) {
            return GameCenterTaskDecision.SKIP_FINANCIAL
        }
        if (isRealGameplayTask(task)) {
            return GameCenterTaskDecision.SKIP_REAL_GAME
        }

        val actionType = task.optString("actionType").uppercase()
        if (actionType.contains("AD") || task.optString("title").contains("广告")) {
            return GameCenterTaskDecision.SKIP_AD
        }
        if (status in terminalStatuses) {
            return if (buttonText.contains("领取")) {
                GameCenterTaskDecision.CLAIM_ONLY
            } else {
                GameCenterTaskDecision.TERMINAL
            }
        }
        if (task.optBoolean("needSignUp", false) && status == "NOT_DONE") {
            return GameCenterTaskDecision.SIGN_UP
        }
        if (
            status == "SIGNUP_COMPLETE" ||
            actionType in setOf("BROWSE", "VIEW", "VIEW_TASK", "CALL_APP")
        ) {
            return GameCenterTaskDecision.SEND
        }
        return GameCenterTaskDecision.SKIP_UNSUPPORTED
    }

    fun classifyP2eTask(task: JSONObject): GameCenterTaskDecision {
        val status = task.optString("taskStatus").uppercase()
        val buttonText = task.optString("buttonText")
        if (isFinancialTask(task)) {
            return GameCenterTaskDecision.SKIP_FINANCIAL
        }

        val taskType = task.optString("taskType").uppercase()
        val actionType = task.optString("actionType").uppercase()
        return when {
            taskType == "GAME_TRAN_TASK" ->
                GameCenterTaskDecision.SKIP_REAL_GAME

            actionType == "LIGHT_AD_TASK" || actionType.contains("AD") ->
                GameCenterTaskDecision.SKIP_AD

            taskType != "PLATFORM_TRAN_TASK" || actionType != "VIEW_TASK" ->
                GameCenterTaskDecision.SKIP_UNSUPPORTED

            status in terminalStatuses ->
                if (buttonText.contains("领取")) {
                    GameCenterTaskDecision.CLAIM_ONLY
                } else {
                    GameCenterTaskDecision.TERMINAL
                }

            task.optBoolean("needSignUp", false) &&
                status in setOf("UN_SIGNUP", "NONE_SIGNUP", "NOT_DONE") ->
                GameCenterTaskDecision.SIGN_UP

            else -> GameCenterTaskDecision.SEND
        }
    }

    fun parseP2eTasks(response: String): GameCenterTaskSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return GameCenterTaskSnapshot(false, emptyList())
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return GameCenterTaskSnapshot(false, emptyList())
        }

        var recognized = false
        val tasks = mutableListOf<JSONObject>()
        for (container in containers) {
            val exposedModule = container.optJSONObject("exposedTaskModuleVO")
            if (exposedModule?.has("exposedTaskList") == true) {
                recognized = true
                appendTasks(exposedModule.optJSONArray("exposedTaskList"), tasks)
            }
            val platformModule = container.optJSONObject("platformGameTaskModule")
            for (key in listOf("platformTaskList", "taskList", "gameTaskList")) {
                if (platformModule?.has(key) == true) {
                    recognized = true
                    appendTasks(platformModule.optJSONArray(key), tasks)
                }
            }
        }
        return GameCenterTaskSnapshot(
            recognized = recognized,
            tasks = tasks.distinctBy { it.optString("taskId") }
        )
    }

    fun parsePlatformTasks(response: String): GameCenterTaskSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return GameCenterTaskSnapshot(false, emptyList())
        val containers = responseContainers(root)
        if (!isRpcSuccess(containers)) {
            return GameCenterTaskSnapshot(false, emptyList())
        }

        var recognized = false
        val tasks = mutableListOf<JSONObject>()
        for (container in containers) {
            val module = container.optJSONObject("platformTaskModule")
            if (module != null) {
                if (module.has("platformTaskList")) {
                    recognized = true
                    appendTasks(module.optJSONArray("platformTaskList"), tasks)
                }
                if (module.has("taskList")) {
                    recognized = true
                    appendTasks(module.optJSONArray("taskList"), tasks)
                }
            }
            if (container.has("platformTaskList")) {
                recognized = true
                appendTasks(container.optJSONArray("platformTaskList"), tasks)
            }
        }
        return GameCenterTaskSnapshot(
            recognized = recognized,
            tasks = tasks.distinctBy { it.optString("taskId") }
        )
    }

    fun findTask(
        snapshot: GameCenterTaskSnapshot,
        taskId: String
    ): JSONObject? {
        if (!snapshot.recognized || taskId.isBlank()) {
            return null
        }
        return snapshot.tasks.firstOrNull {
            it.optString("taskId") == taskId
        }
    }

    fun isSignupConfirmed(
        previousStatus: String,
        refreshedStatus: String?
    ): Boolean {
        val refreshed = refreshedStatus.orEmpty().uppercase()
        if (refreshed.isBlank() || refreshed == previousStatus.uppercase()) {
            return false
        }
        return refreshed == "SIGNUP_COMPLETE" || refreshed in terminalStatuses
    }

    fun isSendConfirmed(refreshedStatus: String?): Boolean {
        return refreshedStatus.orEmpty().uppercase() in terminalStatuses
    }

    private fun isFinancialTask(task: JSONObject): Boolean {
        val text = "${task.optString("title")} ${task.optString("subTitle")}"
        return financialKeywords.any(text::contains)
    }

    private fun isRealGameplayTask(task: JSONObject): Boolean {
        if (task.optString("taskType").equals("GAME_TRAN_TASK", true)) {
            return true
        }
        val hasGameTarget = task.optString("gameId").isNotBlank() &&
            task.optString("appId").isNotBlank() &&
            task.optString("jumpLink").contains("platformapi/startapp", true)
        if (!hasGameTarget) {
            return false
        }
        val text = "${task.optString("title")} ${task.optString("subTitle")}".uppercase()
        return gameplayKeywords.any(text::contains)
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
                if (container.optString(key).trim().uppercase() !in successCodes) {
                    return false
                }
            }
        }
        return markerFound
    }

    private fun appendTasks(
        source: JSONArray?,
        destination: MutableList<JSONObject>
    ) {
        if (source == null) {
            return
        }
        for (index in 0 until source.length()) {
            val task = source.optJSONObject(index) ?: continue
            if (task.optString("taskId").isNotBlank()) {
                destination += task
            }
        }
    }
}
