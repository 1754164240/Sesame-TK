package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject

data class GameCenterP2eTaskOutcome(
    val taskId: String,
    val title: String,
    val decision: GameCenterTaskDecision,
    val confirmed: Boolean,
    val retryable: Boolean,
    val message: String
)

data class GameCenterP2eRunResult(
    val recognized: Boolean,
    val completed: Int,
    val failed: Int,
    val skipped: Int,
    val retryable: Boolean,
    val outcomes: List<GameCenterP2eTaskOutcome>
)

class GameCenterP2eTaskWorkflow(
    private val queryTaskSources: () -> List<String>,
    private val signupTask: (JSONObject) -> String,
    private val completeTask: (JSONObject) -> String,
    private val receiveTask: (JSONObject) -> String,
    private val isActionSuccess: (String) -> Boolean,
    private val pauseAfterAction: () -> Unit = {},
    private val executeInteractiveTask: (JSONObject) ->
        GameCenterInteractiveResult = {
            GameCenterInteractiveResult(
                GameCenterInteractiveOutcome.SKIPPED,
                "未配置游戏中心交互执行器"
            )
        }
) {

    fun run(): GameCenterP2eRunResult {
        val initial = querySnapshot()
        if (!initial.recognized) {
            return GameCenterP2eRunResult(
                recognized = false,
                completed = 0,
                failed = 1,
                skipped = 0,
                retryable = true,
                outcomes = emptyList()
            )
        }
        val outcomes = initial.tasks.mapNotNull(::processTask)
        return GameCenterP2eRunResult(
            recognized = true,
            completed = outcomes.count { it.confirmed },
            failed = outcomes.count { !it.confirmed && it.retryable },
            skipped = outcomes.count { !it.confirmed && !it.retryable },
            retryable = outcomes.any { it.retryable },
            outcomes = outcomes
        )
    }

    private fun processTask(task: JSONObject): GameCenterP2eTaskOutcome? {
        val taskId = task.optString("taskId")
        if (taskId.isBlank()) {
            return null
        }
        val title = task.optString("title")
            .ifBlank { task.optString("subTitle") }
            .ifBlank { taskId }
        val decision = GameCenterTaskPolicy.classifyP2eTask(task)
        return when (decision) {
            GameCenterTaskDecision.SIGN_UP -> signupAndComplete(task, title)
            GameCenterTaskDecision.SEND -> completeAndConfirm(task, title, decision)
            GameCenterTaskDecision.EXECUTE_GAME,
            GameCenterTaskDecision.EXECUTE_AD ->
                executeInteractive(task, title, decision)
            GameCenterTaskDecision.CLAIM_ONLY -> receiveAndConfirm(task, title)
            GameCenterTaskDecision.TERMINAL -> null
            GameCenterTaskDecision.SKIP_REAL_GAME,
            GameCenterTaskDecision.SKIP_AD,
            GameCenterTaskDecision.SKIP_FINANCIAL,
            GameCenterTaskDecision.SKIP_UNSUPPORTED ->
                skipped(taskId, title, decision)
        }
    }

    private fun executeInteractive(
        task: JSONObject,
        title: String,
        decision: GameCenterTaskDecision
    ): GameCenterP2eTaskOutcome {
        val taskId = task.optString("taskId")
        return when (val result = executeInteractiveTask(task)) {
            is GameCenterInteractiveResult -> when (result.outcome) {
                GameCenterInteractiveOutcome.CONFIRMED ->
                    confirmed(taskId, title, decision, result.message)
                GameCenterInteractiveOutcome.RETRY ->
                    retry(taskId, title, decision, result.message)
                GameCenterInteractiveOutcome.SKIPPED ->
                    GameCenterP2eTaskOutcome(
                        taskId,
                        title,
                        decision,
                        false,
                        false,
                        result.message
                    )
            }
        }
    }

    private fun signupAndComplete(
        task: JSONObject,
        title: String
    ): GameCenterP2eTaskOutcome {
        val taskId = task.optString("taskId")
        if (!hasActionContract(task)) {
            return retry(taskId, title, GameCenterTaskDecision.SIGN_UP, "报名参数缺失")
        }
        if (!performAction { signupTask(task) }) {
            return retry(taskId, title, GameCenterTaskDecision.SIGN_UP, "报名请求失败")
        }
        pauseAfterAction()
        val refreshed = refreshTask(taskId)
            ?: return retry(taskId, title, GameCenterTaskDecision.SIGN_UP, "报名后状态未知")
        if (
            !GameCenterTaskPolicy.isSignupConfirmed(
                task.optString("taskStatus"),
                refreshed.optString("taskStatus")
            )
        ) {
            return retry(taskId, title, GameCenterTaskDecision.SIGN_UP, "报名后状态未推进")
        }
        if (GameCenterTaskPolicy.isSendConfirmed(refreshed.optString("taskStatus"))) {
            return confirmed(taskId, title, GameCenterTaskDecision.SIGN_UP, "报名后已进入终态")
        }
        return completeAndConfirm(refreshed, title, GameCenterTaskDecision.SEND)
    }

    private fun completeAndConfirm(
        task: JSONObject,
        title: String,
        decision: GameCenterTaskDecision
    ): GameCenterP2eTaskOutcome {
        val taskId = task.optString("taskId")
        if (!hasActionContract(task)) {
            return retry(taskId, title, decision, "完成参数缺失")
        }
        if (!performAction { completeTask(task) }) {
            return retry(taskId, title, decision, "完成请求失败")
        }
        pauseAfterAction()
        val refreshed = refreshTask(taskId)
            ?: return retry(taskId, title, decision, "完成后状态未知")
        return if (
            GameCenterTaskPolicy.isSendConfirmed(refreshed.optString("taskStatus"))
        ) {
            confirmed(taskId, title, decision, "服务端终态已确认")
        } else {
            retry(taskId, title, decision, "完成后状态未终态")
        }
    }

    private fun receiveAndConfirm(
        task: JSONObject,
        title: String
    ): GameCenterP2eTaskOutcome {
        val taskId = task.optString("taskId")
        if (!hasActionContract(task)) {
            return retry(taskId, title, GameCenterTaskDecision.CLAIM_ONLY, "领奖参数缺失")
        }
        if (!performAction { receiveTask(task) }) {
            return retry(taskId, title, GameCenterTaskDecision.CLAIM_ONLY, "领奖请求失败")
        }
        pauseAfterAction()
        val snapshot = querySnapshot()
        if (!snapshot.recognized) {
            return retry(taskId, title, GameCenterTaskDecision.CLAIM_ONLY, "领奖后查询失败")
        }
        val refreshed = GameCenterTaskPolicy.findTask(snapshot, taskId)
        val confirmed = refreshed == null ||
            refreshed.optString("taskStatus").uppercase() in
            setOf("RECEIVED", "DONE", "SUCCESS", "AWARDED")
        return if (confirmed) {
            confirmed(taskId, title, GameCenterTaskDecision.CLAIM_ONLY, "领奖终态已确认")
        } else {
            retry(taskId, title, GameCenterTaskDecision.CLAIM_ONLY, "领奖后状态未刷新")
        }
    }

    private fun refreshTask(taskId: String): JSONObject? {
        val snapshot = querySnapshot()
        if (!snapshot.recognized) {
            return null
        }
        return GameCenterTaskPolicy.findTask(snapshot, taskId)
    }

    private fun querySnapshot(): GameCenterTaskSnapshot {
        val responses = runCatching { queryTaskSources() }.getOrNull()
            ?: return GameCenterTaskSnapshot(false, emptyList())
        if (responses.isEmpty()) {
            return GameCenterTaskSnapshot(false, emptyList())
        }
        val snapshots = responses.map(GameCenterTaskPolicy::parseP2eTasks)
        if (snapshots.any { !it.recognized }) {
            return GameCenterTaskSnapshot(false, emptyList())
        }
        return GameCenterTaskSnapshot(
            recognized = true,
            tasks = snapshots.flatMap { it.tasks }
                .distinctBy { it.optString("taskId") }
        )
    }

    private fun hasActionContract(task: JSONObject): Boolean {
        return task.optString("taskId").isNotBlank() &&
            task.optString("taskToken").isNotBlank()
    }

    private fun performAction(action: () -> String): Boolean {
        val response = runCatching(action).getOrNull() ?: return false
        return runCatching { isActionSuccess(response) }.getOrDefault(false)
    }

    private fun confirmed(
        taskId: String,
        title: String,
        decision: GameCenterTaskDecision,
        message: String
    ): GameCenterP2eTaskOutcome {
        return GameCenterP2eTaskOutcome(taskId, title, decision, true, false, message)
    }

    private fun retry(
        taskId: String,
        title: String,
        decision: GameCenterTaskDecision,
        message: String
    ): GameCenterP2eTaskOutcome {
        return GameCenterP2eTaskOutcome(taskId, title, decision, false, true, message)
    }

    private fun skipped(
        taskId: String,
        title: String,
        decision: GameCenterTaskDecision
    ): GameCenterP2eTaskOutcome {
        val message = when (decision) {
            GameCenterTaskDecision.SKIP_REAL_GAME -> "真实游戏任务不执行"
            GameCenterTaskDecision.SKIP_AD -> "广告任务不执行"
            GameCenterTaskDecision.SKIP_FINANCIAL -> "金融任务不执行"
            else -> "未知任务不执行"
        }
        return GameCenterP2eTaskOutcome(taskId, title, decision, false, false, message)
    }
}
