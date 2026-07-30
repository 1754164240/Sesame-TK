package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

enum class GameCenterInteractiveOutcome {
    CONFIRMED,
    RETRY,
    SKIPPED
}

data class GameCenterInteractiveResult(
    val outcome: GameCenterInteractiveOutcome,
    val message: String
)

class GameCenterInteractiveWorkflow(
    private val signupTask: ((JSONObject) -> String)? = null,
    private val launch: (String) -> Boolean,
    private val simulateGame: (JSONObject) -> String,
    private val queryAd: (JSONObject) -> String,
    private val finishAdEvent: (String, JSONObject) -> String,
    private val completeTask: (JSONObject) -> String,
    private val refreshTask: (String) -> JSONObject?,
    private val receiveTask: ((JSONObject) -> String)? = null,
    private val pause: (Long) -> Unit,
    private val isActionSuccess: (String) -> Boolean,
    private val requireTaskToken: Boolean = false,
    private val maxSteps: Int = 20
) {

    fun execute(task: JSONObject): GameCenterInteractiveResult {
        val taskId = task.optString("taskId").trim()
        if (taskId.isBlank()) {
            return skipped("游戏中心任务缺少身份参数")
        }
        if (requireTaskToken && task.optString("taskToken").isBlank()) {
            return skipped("游戏中心任务缺少服务端任务令牌")
        }
        val budget = StepBudget(maxSteps)
        var currentTask = task
        if (requiresSignup(currentTask)) {
            val signup = signupTask
                ?: return skipped("游戏中心任务缺少报名执行器")
            if (!budget.use() || !perform { signup(currentTask) }) {
                return retry("游戏中心任务报名请求失败或超过步骤上限")
            }
            pause(300L)
            if (!budget.use()) {
                return retry("游戏中心任务报名回查超过步骤上限")
            }
            val refreshed = runCatching { refreshTask(taskId) }.getOrNull()
                ?: return retry("游戏中心任务报名后状态未知")
            if (
                !GameCenterTaskPolicy.isSignupConfirmed(
                    currentTask.optString("taskStatus"),
                    refreshed.optString("taskStatus")
                )
            ) {
                return retry("游戏中心任务报名后状态无进展")
            }
            currentTask = refreshed
        }
        val actionType = task.optString("actionType").uppercase()
        val taskType = task.optString("taskType").uppercase()
        val prepared = when {
            actionType.contains("AD") -> executeAd(task, budget)
            taskType == "GAME_TRAN_TASK" ||
                task.optString("gameId").isNotBlank() ->
                executeGame(task, budget)
            else -> skipped("不是可交互的游戏中心任务")
        }
        if (prepared.outcome != GameCenterInteractiveOutcome.CONFIRMED) {
            return prepared
        }
        if (!budget.use() || !perform { completeTask(currentTask) }) {
            return retry("游戏中心任务完成请求失败或超过步骤上限")
        }
        pause(1000L)
        if (!budget.use()) {
            return retry("游戏中心任务完成回查超过步骤上限")
        }
        val refreshed = runCatching { refreshTask(taskId) }.getOrNull()
            ?: return retry("游戏中心任务完成后状态未知")
        if (
            !GameCenterTaskPolicy.isSendConfirmed(
                refreshed.optString("taskStatus")
            )
        ) {
            return retry("游戏中心任务完成后状态无进展")
        }
        if (!refreshed.optString("buttonText").contains("领取")) {
            return confirmed("游戏中心任务服务端状态已推进")
        }
        val receive = receiveTask
            ?: return confirmed("游戏中心任务已进入可领奖状态")
        if (!budget.use() || !perform { receive(refreshed) }) {
            return retry("游戏中心任务领奖请求失败或超过步骤上限")
        }
        pause(300L)
        if (!budget.use()) {
            return retry("游戏中心任务领奖回查超过步骤上限")
        }
        val claimed = runCatching { refreshTask(taskId) }.getOrNull()
            ?: return retry("游戏中心任务领奖后状态未知")
        return if (isClaimConfirmed(claimed)) {
            confirmed("游戏中心任务领奖终态已确认")
        } else {
            retry("游戏中心任务领奖后状态无进展")
        }
    }

    private fun executeGame(
        task: JSONObject,
        budget: StepBudget
    ): GameCenterInteractiveResult {
        val jumpLink = task.optString("jumpLink").trim()
        if (!isAllowedLink(jumpLink)) {
            return skipped("游戏中心真实游戏缺少允许的启动链接")
        }
        if (
            task.optString("gameId").isBlank() &&
            task.optString("appId").isBlank()
        ) {
            return skipped("游戏中心真实游戏缺少游戏或应用编号")
        }
        val durationMillis = resolveDurationMillis(task)
            ?: return skipped("游戏中心真实游戏缺少服务端时长")
        if (
            !budget.use() ||
            !runCatching { launch(jumpLink) }.getOrDefault(false)
        ) {
            return retry("游戏中心真实游戏启动失败或超过步骤上限")
        }
        pause(durationMillis)
        if (!budget.use() || !perform { simulateGame(task) }) {
            return retry("游戏中心游戏过程提交失败或超过步骤上限")
        }
        return confirmed("游戏过程已提交")
    }

    private fun executeAd(
        task: JSONObject,
        budget: StepBudget
    ): GameCenterInteractiveResult {
        if (
            task.optJSONObject("adQueryParams") == null &&
            task.optJSONObject("positionRequest") == null
        ) {
            return skipped("游戏中心广告缺少动态查询参数")
        }
        if (!budget.use()) {
            return retry("游戏中心广告查询超过步骤上限")
        }
        val response = runCatching { JSONObject(queryAd(task)) }.getOrNull()
            ?: return retry("游戏中心广告查询响应不可解析")
        val playingResult = response.optJSONObject("playingResult")
            ?: response.optJSONObject("resData")
                ?.optJSONObject("playingResult")
            ?: return retry("游戏中心广告缺少播放结果")
        val playBizId = playingResult.optString("playingBizId").trim()
        val events = playingResult.optJSONObject("eventRewardDetail")
            ?.optJSONArray("eventRewardInfoList")
            ?: JSONArray()
        if (playBizId.isBlank() || events.length() == 0) {
            return retry("游戏中心广告缺少播放编号或事件")
        }
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index)
                ?: return retry("游戏中心广告事件结构未知")
            if (
                !budget.use() ||
                !perform { finishAdEvent(playBizId, event) }
            ) {
                return retry("游戏中心广告事件提交失败或超过步骤上限")
            }
            pause(resolveEventDurationMillis(event, task))
        }
        return confirmed("广告播放事件已提交")
    }

    private fun resolveDurationMillis(task: JSONObject): Long? {
        val seconds = sequenceOf(
            task.optLong("duration", 0L),
            task.optLong("taskDuration", 0L),
            task.optLong("stayTime", 0L)
        ).firstOrNull { it > 0L } ?: return null
        return seconds.coerceIn(1L, 60L) * 1000L
    }

    private fun resolveEventDurationMillis(
        event: JSONObject,
        task: JSONObject
    ): Long {
        val seconds = sequenceOf(
            event.optLong("duration", 0L),
            event.optLong("waitTime", 0L),
            event.optLong("rewardTime", 0L),
            task.optLong("duration", 0L),
            task.optLong("taskDuration", 0L),
            task.optLong("stayTime", 0L)
        ).firstOrNull { it > 0L } ?: 1L
        return seconds.coerceIn(1L, 60L) * 1000L
    }

    private fun requiresSignup(task: JSONObject): Boolean {
        if (!task.optBoolean("needSignUp", false)) {
            return false
        }
        return task.optString("taskStatus").uppercase() in
            setOf("UN_SIGNUP", "NONE_SIGNUP", "NOT_DONE")
    }

    private fun isClaimConfirmed(task: JSONObject): Boolean {
        return task.optString("taskStatus").uppercase() in
            setOf("RECEIVED", "DONE", "SUCCESS", "AWARDED")
    }

    private fun isAllowedLink(link: String): Boolean {
        if (link.isBlank()) {
            return false
        }
        val scheme = runCatching { URI(link).scheme?.lowercase() }
            .getOrNull()
        return scheme in setOf("alipays", "alipay", "https")
    }

    private fun perform(action: () -> String): Boolean {
        val response = runCatching(action).getOrNull() ?: return false
        return runCatching { isActionSuccess(response) }.getOrDefault(false)
    }

    private fun confirmed(message: String): GameCenterInteractiveResult {
        return GameCenterInteractiveResult(
            GameCenterInteractiveOutcome.CONFIRMED,
            message
        )
    }

    private fun retry(message: String): GameCenterInteractiveResult {
        return GameCenterInteractiveResult(
            GameCenterInteractiveOutcome.RETRY,
            message
        )
    }

    private fun skipped(message: String): GameCenterInteractiveResult {
        return GameCenterInteractiveResult(
            GameCenterInteractiveOutcome.SKIPPED,
            message
        )
    }

    private class StepBudget(maxSteps: Int) {
        private val limit = maxSteps.coerceAtLeast(1)
        private var used = 0

        fun use(): Boolean {
            if (used >= limit) {
                return false
            }
            used++
            return true
        }
    }
}
