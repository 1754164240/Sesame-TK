package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GameCenterInteractiveWorkflowTest {

    @Test
    fun `真实游戏按报名启动模拟完成领奖和回查顺序执行`() {
        val calls = mutableListOf<String>()
        val refreshedTasks = ArrayDeque(
            listOf(
                task("game-1", "SIGNUP_COMPLETE", "GAME_TRAN_TASK"),
                task("game-1", "COMPLETED", "GAME_TRAN_TASK")
                    .put("buttonText", "领取"),
                task("game-1", "RECEIVED", "GAME_TRAN_TASK")
            )
        )
        val workflow = workflow(
            signupTask = {
                calls += "signup"
                success()
            },
            launch = {
                calls += "launch"
                true
            },
            simulateGame = {
                calls += "simulate"
                success()
            },
            completeTask = {
                calls += "complete"
                success()
            },
            refreshTask = {
                calls += "refresh"
                refreshedTasks.removeFirst()
            },
            receiveTask = {
                calls += "receive"
                success()
            }
        )

        val result = workflow.execute(
            task("game-1", "NOT_DONE", "GAME_TRAN_TASK")
                .put("needSignUp", true)
                .put("gameId", "game-app")
                .put("duration", 1)
                .put(
                    "jumpLink",
                    "alipays://platformapi/startapp?appId=game-app"
                )
        )

        assertEquals(
            listOf(
                "signup",
                "refresh",
                "launch",
                "simulate",
                "complete",
                "refresh",
                "receive",
                "refresh"
            ),
            calls
        )
        assertEquals(
            GameCenterInteractiveOutcome.CONFIRMED,
            result.outcome
        )
    }

    @Test
    fun `广告使用实时播放编号和事件后完成任务并回查`() {
        val calls = mutableListOf<String>()
        val workflow = workflow(
            queryAd = {
                calls += "queryAd"
                JSONObject()
                    .put("success", true)
                    .put(
                        "playingResult",
                        JSONObject()
                            .put("playingBizId", "play-live")
                            .put(
                                "eventRewardDetail",
                                JSONObject().put(
                                    "eventRewardInfoList",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "eventId",
                                            "event-live"
                                        )
                                    )
                                )
                            )
                    )
                    .toString()
            },
            finishAdEvent = { playId, event ->
                calls += "finishAd:$playId:${event.getString("eventId")}"
                success()
            },
            completeTask = {
                calls += "complete"
                success()
            },
            refreshTask = {
                calls += "refresh"
                task(
                    "ad-1",
                    "COMPLETED",
                    "PLATFORM_TRAN_TASK",
                    "LIGHT_AD_TASK"
                )
            }
        )

        val result = workflow.execute(
            task(
                "ad-1",
                "NOT_DONE",
                "PLATFORM_TRAN_TASK",
                "LIGHT_AD_TASK"
            ).put(
                "positionRequest",
                JSONObject().put("spaceCode", "GAME_CENTER_AD")
            )
        )

        assertEquals(
            listOf(
                "queryAd",
                "finishAd:play-live:event-live",
                "complete",
                "refresh"
            ),
            calls
        )
        assertEquals(
            GameCenterInteractiveOutcome.CONFIRMED,
            result.outcome
        )
    }

    @Test
    fun `非白名单深链和缺少广告参数均跳过`() {
        val invalidGame = workflow().execute(
            task("game-1", "NOT_DONE", "GAME_TRAN_TASK")
                .put("gameId", "game-app")
                .put("duration", 1)
                .put("jumpLink", "intent://unsafe")
        )
        val missingGameFields = workflow().execute(
            task("game-2", "NOT_DONE", "GAME_TRAN_TASK")
                .put(
                    "jumpLink",
                    "alipays://platformapi/startapp?appId=game-app"
                )
        )
        val missingAd = workflow().execute(
            task(
                "ad-1",
                "NOT_DONE",
                "PLATFORM_TRAN_TASK",
                "LIGHT_AD_TASK"
            )
        )

        assertEquals(
            GameCenterInteractiveOutcome.SKIPPED,
            invalidGame.outcome
        )
        assertEquals(
            GameCenterInteractiveOutcome.SKIPPED,
            missingGameFields.outcome
        )
        assertEquals(
            GameCenterInteractiveOutcome.SKIPPED,
            missingAd.outcome
        )
    }

    @Test
    fun `动作成功但服务端状态无进展必须重试`() {
        val result = workflow(
            refreshTask = {
                task("game-1", "NOT_DONE", "GAME_TRAN_TASK")
            }
        ).execute(
            task("game-1", "NOT_DONE", "GAME_TRAN_TASK")
                .put("gameId", "game-app")
                .put("duration", 1)
                .put(
                    "jumpLink",
                    "https://render.alipay.com/game"
                )
        )

        assertEquals(
            GameCenterInteractiveOutcome.RETRY,
            result.outcome
        )
    }

    @Test
    fun `超过最大步骤时停止后续动作`() {
        var launchCalls = 0
        val result = workflow(
            signupTask = { success() },
            launch = {
                launchCalls++
                true
            },
            refreshTask = {
                task("game-1", "SIGNUP_COMPLETE", "GAME_TRAN_TASK")
            },
            maxSteps = 2
        ).execute(
            task("game-1", "NOT_DONE", "GAME_TRAN_TASK")
                .put("needSignUp", true)
                .put("gameId", "game-app")
                .put("duration", 1)
                .put(
                    "jumpLink",
                    "alipays://platformapi/startapp?appId=game-app"
                )
        )

        assertEquals(GameCenterInteractiveOutcome.RETRY, result.outcome)
        assertEquals(0, launchCalls)
    }

    private fun workflow(
        signupTask: (JSONObject) -> String = { success() },
        launch: (String) -> Boolean = { true },
        simulateGame: (JSONObject) -> String = { success() },
        queryAd: (JSONObject) -> String = { success() },
        finishAdEvent: (String, JSONObject) -> String = { _, _ ->
            success()
        },
        completeTask: (JSONObject) -> String = { success() },
        refreshTask: (String) -> JSONObject? = {
            task(it, "COMPLETED", "GAME_TRAN_TASK")
        },
        receiveTask: (JSONObject) -> String = { success() },
        maxSteps: Int = 20
    ): GameCenterInteractiveWorkflow {
        return GameCenterInteractiveWorkflow(
            signupTask = signupTask,
            launch = launch,
            simulateGame = simulateGame,
            queryAd = queryAd,
            finishAdEvent = finishAdEvent,
            completeTask = completeTask,
            refreshTask = refreshTask,
            receiveTask = receiveTask,
            pause = {},
            isActionSuccess = { response ->
                JSONObject(response).optBoolean("success", false)
            },
            maxSteps = maxSteps
        )
    }

    private fun task(
        taskId: String,
        status: String,
        taskType: String,
        actionType: String = "NORMAL"
    ): JSONObject {
        return JSONObject()
            .put("taskId", taskId)
            .put("taskToken", "token-$taskId")
            .put("taskStatus", status)
            .put("taskType", taskType)
            .put("actionType", actionType)
            .put("title", taskId)
    }

    private fun success(): String = """{"success":true}"""
}
