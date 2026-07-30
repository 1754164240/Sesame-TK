package fansirsqi.xposed.sesame.task.antSports

import org.junit.Assert.assertEquals
import org.junit.Test

class SportsTaskWorkflowTest {

    @Test
    fun `普通日常任务完成并回查到COMPLETED才确认`() {
        var queryCalls = 0
        var completeCalls = 0
        val workflow = SportsTaskWorkflow(
            queryGroup = {
                queryCalls++
                groupResponse(
                    taskJson(
                        "SPORTS_DAILY_BROWSE",
                        "BROWSE",
                        "浏览文体页面",
                        if (queryCalls == 1) "TODO" else "COMPLETED"
                    )
                )
            },
            completeTask = { _, _ ->
                completeCalls++
                """{"success":true}"""
            },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        val result = workflow.process("SPORTS_DAILY_GROUP")

        assertEquals(true, result.recognized)
        assertEquals(1, completeCalls)
        assertEquals(
            SportsTaskOutcome.CONFIRMED,
            result.executions.single().outcome
        )
    }

    @Test
    fun `签到ACK后状态未推进时保留重试`() {
        var queryCalls = 0
        var completeCalls = 0
        val response = groupResponse(
            taskJson(
                "SPORTS_DAILY_SIGN",
                "DAILY_SIGN",
                "每日签到",
                "TODO"
            )
        )
        val workflow = SportsTaskWorkflow(
            queryGroup = {
                queryCalls++
                response
            },
            completeTask = { _, _ ->
                completeCalls++
                """{"success":true}"""
            },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        val result = workflow.process("SPORTS_DAILY_SIGN_GROUP")

        assertEquals(2, queryCalls)
        assertEquals(1, completeCalls)
        assertEquals(
            SportsTaskOutcome.RETRY,
            result.executions.single().outcome
        )
    }

    @Test
    fun `签到状态推进后才确认完成`() {
        var queryCalls = 0
        val workflow = SportsTaskWorkflow(
            queryGroup = {
                queryCalls++
                groupResponse(
                    taskJson(
                        "SPORTS_DAILY_SIGN",
                        "DAILY_SIGN",
                        "每日签到",
                        if (queryCalls == 1) "TODO" else "COMPLETED"
                    )
                )
            },
            completeTask = { _, _ -> """{"success":true}""" },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        val result = workflow.process("SPORTS_DAILY_SIGN_GROUP")

        assertEquals(
            SportsTaskOutcome.CONFIRMED,
            result.executions.single().outcome
        )
    }

    @Test
    fun `重复任务身份在同一轮只处理一次`() {
        var completeCalls = 0
        val duplicate = taskJson(
            "SPORTS_DAILY_SIGN",
            "DAILY_SIGN",
            "每日签到",
            "TODO"
        )
        val workflow = SportsTaskWorkflow(
            queryGroup = {
                groupResponse(duplicate, duplicate)
            },
            completeTask = { _, _ ->
                completeCalls++
                """{"success":true}"""
            },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        workflow.process("SPORTS_DAILY_SIGN_GROUP")

        assertEquals(1, completeCalls)
    }

    @Test
    fun `领奖ACK后任务仍待领取时保留重试`() {
        var queryCalls = 0
        var rewardCalls = 0
        val response = groupResponse(
            taskJson(
                "SPORTS_DAILY_STEP",
                "STEP",
                "每日步数",
                "COMPLETED",
                "user-task-1"
            )
        )
        val workflow = SportsTaskWorkflow(
            queryGroup = {
                queryCalls++
                response
            },
            completeTask = { _, _ -> """{"success":true}""" },
            receiveReward = { _, _ ->
                rewardCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("SPORTS_DAILY_GROUP")

        assertEquals(2, queryCalls)
        assertEquals(1, rewardCalls)
        assertEquals(
            SportsTaskOutcome.RETRY,
            result.executions.single().outcome
        )
    }

    @Test
    fun `领奖后服务端进入已领取状态才确认`() {
        var queryCalls = 0
        val workflow = SportsTaskWorkflow(
            queryGroup = {
                queryCalls++
                groupResponse(
                    taskJson(
                        "SPORTS_DAILY_STEP",
                        "STEP",
                        "每日步数",
                        if (queryCalls == 1) "COMPLETED" else "RECEIVED",
                        "user-task-1"
                    )
                )
            },
            completeTask = { _, _ -> """{"success":true}""" },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        val result = workflow.process("SPORTS_DAILY_GROUP")

        assertEquals(
            SportsTaskOutcome.CONFIRMED,
            result.executions.single().outcome
        )
    }

    private fun groupResponse(vararg tasks: String): String {
        return """
            {
              "success":true,
              "group":{"userTaskList":[${tasks.joinToString(",")}]}
            }
        """.trimIndent()
    }

    private fun taskJson(
        taskId: String,
        bizType: String,
        taskName: String,
        status: String,
        userTaskId: String = ""
    ): String {
        val userTask = if (userTaskId.isBlank()) {
            ""
        } else {
            ""","userTaskId":"$userTaskId""""
        }
        return """
            {
              "status":"$status"$userTask,
              "taskInfo":{
                "taskId":"$taskId",
                "bizType":"$bizType",
                "taskName":"$taskName"
              }
            }
        """.trimIndent()
    }
}
