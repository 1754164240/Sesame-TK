package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject

data class GameCenterPlatformTaskOutcome(
    val taskId: String,
    val title: String,
    val decision: GameCenterTaskDecision,
    val confirmed: Boolean,
    val retryable: Boolean,
    val message: String
)

data class GameCenterPlatformRunResult(
    val recognized: Boolean,
    val completed: Int,
    val failed: Int,
    val skipped: Int,
    val retryable: Boolean,
    val outcomes: List<GameCenterPlatformTaskOutcome>
)

class GameCenterPlatformWorkflow(
    private val queryTasks: () -> String,
    private val signupTask: (String) -> String,
    private val sendTask: (String) -> String,
    private val isActionSuccess: (String) -> Boolean,
    private val pauseAfterAction: () -> Unit = {}
) {

    fun run(): GameCenterPlatformRunResult {
        val initialSnapshot = querySnapshot()
        if (!initialSnapshot.recognized) {
            return GameCenterPlatformRunResult(
                recognized = false,
                completed = 0,
                failed = 1,
                skipped = 0,
                retryable = true,
                outcomes = emptyList()
            )
        }

        val outcomes = initialSnapshot.tasks.mapNotNull(::processTask)
        return GameCenterPlatformRunResult(
            recognized = true,
            completed = outcomes.count { it.confirmed },
            failed = outcomes.count { !it.confirmed && it.retryable },
            skipped = outcomes.count { !it.confirmed && !it.retryable },
            retryable = outcomes.any { it.retryable },
            outcomes = outcomes
        )
    }

    private fun processTask(task: JSONObject): GameCenterPlatformTaskOutcome? {
        val taskId = task.optString("taskId")
        if (taskId.isBlank()) {
            return null
        }
        val title = task.optString("title").ifBlank {
            task.optString("subTitle").ifBlank { taskId }
        }
        val decision = GameCenterTaskPolicy.classifyPlatformTask(task)
        return when (decision) {
            GameCenterTaskDecision.SIGN_UP ->
                signupAndSend(task, taskId, title)

            GameCenterTaskDecision.SEND ->
                sendAndConfirm(taskId, title, decision)

            GameCenterTaskDecision.CLAIM_ONLY,
            GameCenterTaskDecision.TERMINAL ->
                null

            GameCenterTaskDecision.SKIP_REAL_GAME,
            GameCenterTaskDecision.SKIP_AD,
            GameCenterTaskDecision.SKIP_FINANCIAL,
            GameCenterTaskDecision.SKIP_UNSUPPORTED ->
                skippedOutcome(taskId, title, decision)
        }
    }

    private fun signupAndSend(
        task: JSONObject,
        taskId: String,
        title: String
    ): GameCenterPlatformTaskOutcome {
        val signupResponse = runCatching { signupTask(taskId) }.getOrNull()
        if (signupResponse == null || !isActionSuccess(signupResponse)) {
            return retryableOutcome(
                taskId,
                title,
                GameCenterTaskDecision.SIGN_UP,
                "报名请求失败"
            )
        }
        pauseAfterAction()

        val refreshed = refreshTask(taskId)
            ?: return retryableOutcome(
                taskId,
                title,
                GameCenterTaskDecision.SIGN_UP,
                "报名后未查询到目标任务"
            )
        val refreshedStatus = refreshed.optString("taskStatus")
        if (
            !GameCenterTaskPolicy.isSignupConfirmed(
                task.optString("taskStatus"),
                refreshedStatus
            )
        ) {
            return retryableOutcome(
                taskId,
                title,
                GameCenterTaskDecision.SIGN_UP,
                "报名后状态未推进"
            )
        }
        if (GameCenterTaskPolicy.isSendConfirmed(refreshedStatus)) {
            return confirmedOutcome(
                taskId,
                title,
                GameCenterTaskDecision.SIGN_UP,
                "报名后已进入终态"
            )
        }
        return sendAndConfirm(
            taskId,
            title,
            GameCenterTaskDecision.SEND
        )
    }

    private fun sendAndConfirm(
        taskId: String,
        title: String,
        decision: GameCenterTaskDecision
    ): GameCenterPlatformTaskOutcome {
        val sendResponse = runCatching { sendTask(taskId) }.getOrNull()
        if (sendResponse == null || !isActionSuccess(sendResponse)) {
            return retryableOutcome(taskId, title, decision, "任务发送失败")
        }
        pauseAfterAction()

        val refreshed = refreshTask(taskId)
            ?: return retryableOutcome(
                taskId,
                title,
                decision,
                "发送后未查询到目标任务"
            )
        return if (
            GameCenterTaskPolicy.isSendConfirmed(
                refreshed.optString("taskStatus")
            )
        ) {
            confirmedOutcome(taskId, title, decision, "服务端终态已确认")
        } else {
            retryableOutcome(taskId, title, decision, "发送后状态未终态")
        }
    }

    private fun refreshTask(taskId: String): JSONObject? {
        val snapshot = querySnapshot()
        return GameCenterTaskPolicy.findTask(snapshot, taskId)
    }

    private fun querySnapshot(): GameCenterTaskSnapshot {
        val response = runCatching { queryTasks() }.getOrNull()
            ?: return GameCenterTaskSnapshot(false, emptyList())
        return GameCenterTaskPolicy.parsePlatformTasks(response)
    }

    private fun confirmedOutcome(
        taskId: String,
        title: String,
        decision: GameCenterTaskDecision,
        message: String
    ): GameCenterPlatformTaskOutcome {
        return GameCenterPlatformTaskOutcome(
            taskId = taskId,
            title = title,
            decision = decision,
            confirmed = true,
            retryable = false,
            message = message
        )
    }

    private fun retryableOutcome(
        taskId: String,
        title: String,
        decision: GameCenterTaskDecision,
        message: String
    ): GameCenterPlatformTaskOutcome {
        return GameCenterPlatformTaskOutcome(
            taskId = taskId,
            title = title,
            decision = decision,
            confirmed = false,
            retryable = true,
            message = message
        )
    }

    private fun skippedOutcome(
        taskId: String,
        title: String,
        decision: GameCenterTaskDecision
    ): GameCenterPlatformTaskOutcome {
        val message = when (decision) {
            GameCenterTaskDecision.SKIP_REAL_GAME -> "真实游戏任务无安全闭环"
            GameCenterTaskDecision.SKIP_AD -> "广告任务不自动完成"
            GameCenterTaskDecision.SKIP_FINANCIAL -> "金融或付费任务不自动执行"
            else -> "未知任务类型不自动执行"
        }
        return GameCenterPlatformTaskOutcome(
            taskId = taskId,
            title = title,
            decision = decision,
            confirmed = false,
            retryable = false,
            message = message
        )
    }
}
