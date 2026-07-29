package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChouChouLeRewardWorkflowTest {

    @Test
    fun `广告游戏和普通TODO任务均不调用完成动作`() {
        var receiveCalls = 0
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                taskResponse(
                    taskJson(
                        "LIGHT_AD_TASK",
                        "看广告",
                        "TODO",
                        "BROWSE"
                    ),
                    taskJson(
                        "GUESS_GAME_TASK",
                        "猜价格",
                        "TODO",
                        "GAME"
                    ),
                    taskJson(
                        "NORMAL_TASK",
                        "普通任务",
                        "TODO",
                        "VISIT"
                    )
                )
            },
            receiveReward = { _, _ ->
                receiveCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(0, receiveCalls)
        assertEquals(3, result.unsupportedPendingCount)
        assertFalse(result.finished)
    }

    @Test
    fun `领奖ACK后状态未推进时不确认完成`() {
        var queryCalls = 0
        val response = taskResponse(
            taskJson(
                "REWARD_TASK",
                "已完成任务",
                "FINISHED",
                ""
            )
        )
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                queryCalls++
                response
            },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(2, queryCalls)
        assertEquals(
            ChouChouLeRewardOutcome.RETRY,
            result.executions.single().outcome
        )
        assertFalse(result.finished)
    }

    @Test
    fun `服务端进入已领取状态后才确认奖励`() {
        var queryCalls = 0
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                queryCalls++
                taskResponse(
                    taskJson(
                        "REWARD_TASK",
                        "已完成任务",
                        if (queryCalls == 1) "FINISHED" else "RECEIVED",
                        ""
                    )
                )
            },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(
            ChouChouLeRewardOutcome.CONFIRMED,
            result.executions.single().outcome
        )
        assertEquals(true, result.finished)
    }

    @Test
    fun `任务条目畸形时不领取也不推定完成`() {
        var receiveCalls = 0
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                """{"success":true,"farmTaskList":[1]}"""
            },
            receiveReward = { _, _ ->
                receiveCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(0, receiveCalls)
        assertFalse(result.recognized)
        assertFalse(result.finished)
    }

    private fun taskResponse(vararg tasks: String): String {
        return """
            {
              "success":true,
              "farmTaskList":[${tasks.joinToString(",")}]
            }
        """.trimIndent()
    }

    private fun taskJson(
        taskId: String,
        title: String,
        status: String,
        innerAction: String
    ): String {
        return """
            {
              "bizKey":"$taskId",
              "title":"$title",
              "taskStatus":"$status",
              "innerAction":"$innerAction"
            }
        """.trimIndent()
    }
}
