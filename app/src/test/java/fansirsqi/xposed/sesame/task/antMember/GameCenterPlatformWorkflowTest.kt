package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCenterPlatformWorkflowTest {

    @Test
    fun `真实游戏任务不会调用报名或发送接口`() {
        var signupCalls = 0
        var sendCalls = 0
        val workflow = GameCenterPlatformWorkflow(
            queryTasks = {
                taskResponse(
                    taskId = "game-1",
                    status = "NOT_DONE",
                    title = "玩游戏通过一关",
                    actionType = "NORMAL",
                    extra = """
                        "buttonText":"去完成",
                        "gameId":"game",
                        "appId":"app",
                        "jumpLink":"alipays://platformapi/startapp"
                    """.trimIndent()
                )
            },
            signupTask = {
                signupCalls++
                """{"success":true}"""
            },
            sendTask = {
                sendCalls++
                """{"success":true}"""
            },
            isActionSuccess =(::isSuccess)
        )

        val result = workflow.run()

        assertEquals(0, signupCalls)
        assertEquals(0, sendCalls)
        assertEquals(1, result.skipped)
        assertEquals(0, result.completed)
    }

    @Test
    fun `发送仅返回ACK且回查状态未推进时不得确认`() {
        var queryCalls = 0
        val workflow = GameCenterPlatformWorkflow(
            queryTasks = {
                queryCalls++
                taskResponse(
                    taskId = "view-1",
                    status = "NOT_DONE",
                    title = "浏览游戏中心",
                    actionType = "VIEW_TASK"
                )
            },
            signupTask = { """{"success":true}""" },
            sendTask = { """{"success":true}""" },
            isActionSuccess =(::isSuccess)
        )

        val result = workflow.run()

        assertEquals(2, queryCalls)
        assertEquals(0, result.completed)
        assertEquals(1, result.failed)
        assertTrue(result.retryable)
    }

    @Test
    fun `报名回查推进后发送并以服务端终态确认`() {
        val responses = ArrayDeque(
            listOf(
                taskResponse(
                    taskId = "signup-1",
                    status = "NOT_DONE",
                    title = "签到浏览任务",
                    actionType = "NORMAL",
                    extra = """"needSignUp":true"""
                ),
                taskResponse(
                    taskId = "signup-1",
                    status = "SIGNUP_COMPLETE",
                    title = "签到浏览任务",
                    actionType = "NORMAL",
                    extra = """"needSignUp":true"""
                ),
                taskResponse(
                    taskId = "signup-1",
                    status = "COMPLETED",
                    title = "签到浏览任务",
                    actionType = "NORMAL",
                    extra = """"needSignUp":true"""
                )
            )
        )
        var signupCalls = 0
        var sendCalls = 0
        val workflow = GameCenterPlatformWorkflow(
            queryTasks = { responses.removeFirst() },
            signupTask = {
                signupCalls++
                """{"success":true}"""
            },
            sendTask = {
                sendCalls++
                """{"success":true}"""
            },
            isActionSuccess =(::isSuccess)
        )

        val result = workflow.run()

        assertEquals(1, signupCalls)
        assertEquals(1, sendCalls)
        assertEquals(1, result.completed)
        assertEquals(0, result.failed)
    }

    private fun isSuccess(response: String): Boolean {
        return runCatching { JSONObject(response).optBoolean("success") }
            .getOrDefault(false)
    }

    private fun taskResponse(
        taskId: String,
        status: String,
        title: String,
        actionType: String,
        extra: String = ""
    ): String {
        val suffix = if (extra.isBlank()) "" else ",$extra"
        return """
            {
              "success": true,
              "data": {
                "platformTaskModule": {
                  "platformTaskList": [{
                    "taskId": "$taskId",
                    "taskStatus": "$status",
                    "title": "$title",
                    "actionType": "$actionType"
                    $suffix
                  }]
                }
              }
            }
        """.trimIndent()
    }
}
