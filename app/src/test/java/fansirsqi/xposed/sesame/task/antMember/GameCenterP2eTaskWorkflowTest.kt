package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCenterP2eTaskWorkflowTest {

    @Test
    fun `P2E游戏广告和未知任务不得调用任何动作`() {
        var actionCalls = 0
        val workflow = workflow(
            queryTaskSources = {
                listOf(
                    taskResponse(
                        task("game-1", "COMPLETED", "GAME_TRAN_TASK", "VIEW_TASK")
                            .put("buttonText", "领取")
                    ),
                    taskResponse(
                        task("ad-1", "COMPLETED", "PLATFORM_TRAN_TASK", "LIGHT_AD_TASK")
                            .put("buttonText", "领取")
                    ),
                    taskResponse(
                        task("unknown-1", "NOT_DONE", "UNKNOWN", "VIEW_TASK")
                    )
                )
            },
            signupTask = {
                actionCalls++
                successResponse()
            },
            completeTask = {
                actionCalls++
                successResponse()
            },
            receiveTask = {
                actionCalls++
                successResponse()
            }
        )

        val result = workflow.run()

        assertEquals(0, actionCalls)
        assertEquals(3, result.skipped)
        assertEquals(0, result.completed)
    }

    @Test
    fun `P2E平台浏览ACK后状态未终态必须重试`() {
        var queryCalls = 0
        val pending = task(
            "view-1",
            "NOT_DONE",
            "PLATFORM_TRAN_TASK",
            "VIEW_TASK"
        )
        val workflow = workflow(
            queryTaskSources = {
                queryCalls++
                listOf(taskResponse(pending))
            }
        )

        val result = workflow.run()

        assertEquals(2, queryCalls)
        assertEquals(1, result.failed)
        assertTrue(result.retryable)
    }

    @Test
    fun `P2E已完成平台任务领奖后回查RECEIVED才确认`() {
        val responses = ArrayDeque(
            listOf(
                listOf(
                    taskResponse(
                        task("reward-1", "COMPLETED", "PLATFORM_TRAN_TASK", "VIEW_TASK")
                            .put("buttonText", "领取")
                    )
                ),
                listOf(
                    taskResponse(
                        task("reward-1", "RECEIVED", "PLATFORM_TRAN_TASK", "VIEW_TASK")
                    )
                )
            )
        )
        var receiveCalls = 0
        val workflow = workflow(
            queryTaskSources = { responses.removeFirst() },
            receiveTask = {
                receiveCalls++
                successResponse()
            }
        )

        val result = workflow.run()

        assertEquals(1, receiveCalls)
        assertEquals(1, result.completed)
        assertEquals(0, result.failed)
    }

    @Test
    fun `P2E报名后状态推进再完成并回查终态`() {
        val responses = ArrayDeque(
            listOf(
                listOf(
                    taskResponse(
                        task("signup-1", "UN_SIGNUP", "PLATFORM_TRAN_TASK", "VIEW_TASK")
                            .put("needSignUp", true)
                    )
                ),
                listOf(
                    taskResponse(
                        task("signup-1", "SIGNUP_COMPLETE", "PLATFORM_TRAN_TASK", "VIEW_TASK")
                    )
                ),
                listOf(
                    taskResponse(
                        task("signup-1", "COMPLETED", "PLATFORM_TRAN_TASK", "VIEW_TASK")
                    )
                )
            )
        )
        var signupCalls = 0
        var completeCalls = 0
        val workflow = workflow(
            queryTaskSources = { responses.removeFirst() },
            signupTask = {
                signupCalls++
                successResponse()
            },
            completeTask = {
                completeCalls++
                successResponse()
            }
        )

        val result = workflow.run()

        assertEquals(1, signupCalls)
        assertEquals(1, completeCalls)
        assertEquals(1, result.completed)
    }

    private fun workflow(
        queryTaskSources: () -> List<String>,
        signupTask: (JSONObject) -> String = { successResponse() },
        completeTask: (JSONObject) -> String = { successResponse() },
        receiveTask: (JSONObject) -> String = { successResponse() }
    ): GameCenterP2eTaskWorkflow {
        return GameCenterP2eTaskWorkflow(
            queryTaskSources = queryTaskSources,
            signupTask = signupTask,
            completeTask = completeTask,
            receiveTask = receiveTask,
            isActionSuccess = { response ->
                JSONObject(response).optBoolean("success", false)
            }
        )
    }

    private fun task(
        taskId: String,
        status: String,
        taskType: String,
        actionType: String
    ): JSONObject {
        return JSONObject()
            .put("taskId", taskId)
            .put("taskToken", "token-$taskId")
            .put("taskStatus", status)
            .put("taskType", taskType)
            .put("actionType", actionType)
            .put("title", taskId)
    }

    private fun taskResponse(task: JSONObject): String {
        return JSONObject()
            .put("success", true)
            .put(
                "data",
                JSONObject().put(
                    "platformGameTaskModule",
                    JSONObject().put("platformTaskList", listOf(task))
                )
            )
            .toString()
    }

    private fun successResponse(): String = """{"success":true}"""
}
