package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChouChouLeRewardWorkflowTest {

    @Test
    fun `普通TODO任务执行并回查后领取奖励`() {
        var queryCalls = 0
        var executeCalls = 0
        var receiveCalls = 0
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                queryCalls++
                when (queryCalls) {
                    1 -> taskResponse(
                        taskJson("NORMAL", "普通任务", "TODO", "")
                    )
                    2 -> taskResponse(
                        taskJson("NORMAL", "普通任务", "FINISHED", "")
                    )
                    else -> taskResponse(
                        taskJson("NORMAL", "普通任务", "RECEIVED", "")
                    )
                }
            },
            executeTask = { _, _ ->
                executeCalls++
                true
            },
            receiveReward = { _, _ ->
                receiveCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(1, executeCalls)
        assertEquals(1, receiveCalls)
        assertEquals(3, queryCalls)
        assertTrue(result.finished)
    }

    @Test
    fun `捐赠任务不执行且不阻塞完成`() {
        var executeCalls = 0
        var receiveCalls = 0
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                taskResponse(
                    taskJson(
                        "DONATION_TASK",
                        "公益捐赠",
                        "TODO",
                        "DONATION"
                    )
                )
            },
            executeTask = { _, _ ->
                executeCalls++
                true
            },
            receiveReward = { _, _ ->
                receiveCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(0, executeCalls)
        assertEquals(0, receiveCalls)
        assertEquals(0, result.unsupportedPendingCount)
        assertTrue(result.finished)
    }

    @Test
    fun `任务执行后状态未推进时不领取奖励`() {
        var queryCalls = 0
        var executeCalls = 0
        var receiveCalls = 0
        val pendingResponse = taskResponse(
            taskJson("NORMAL", "普通任务", "TODO", "")
        )
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                queryCalls++
                pendingResponse
            },
            executeTask = { _, _ ->
                executeCalls++
                true
            },
            receiveReward = { _, _ ->
                receiveCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(2, queryCalls)
        assertEquals(1, executeCalls)
        assertEquals(0, receiveCalls)
        assertFalse(result.finished)
    }

    @Test
    fun `可重复任务执行并领奖直到次数上限`() {
        var queryCalls = 0
        var executeCalls = 0
        var receiveCalls = 0
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                queryCalls++
                when (queryCalls) {
                    1 -> taskResponse(
                        taskJson(
                            "REPEAT_TASK",
                            "浏览任务",
                            "TODO",
                            "",
                            rightsTimes = 0,
                            rightsTimesLimit = 2
                        )
                    )
                    2 -> taskResponse(
                        taskJson(
                            "REPEAT_TASK",
                            "浏览任务",
                            "FINISHED",
                            "",
                            rightsTimes = 1,
                            rightsTimesLimit = 2,
                            awardCount = 1
                        )
                    )
                    3 -> taskResponse(
                        taskJson(
                            "REPEAT_TASK",
                            "浏览任务",
                            "TODO",
                            "",
                            rightsTimes = 1,
                            rightsTimesLimit = 2,
                            receivedAwardCount = 1,
                            awardCount = 1
                        )
                    )
                    4 -> taskResponse(
                        taskJson(
                            "REPEAT_TASK",
                            "浏览任务",
                            "FINISHED",
                            "",
                            rightsTimes = 2,
                            rightsTimesLimit = 2,
                            receivedAwardCount = 1,
                            awardCount = 2
                        )
                    )
                    else -> taskResponse(
                        taskJson(
                            "REPEAT_TASK",
                            "浏览任务",
                            "TODO",
                            "",
                            rightsTimes = 2,
                            rightsTimesLimit = 2,
                            receivedAwardCount = 2,
                            awardCount = 2
                        )
                    )
                }
            },
            executeTask = { _, _ ->
                executeCalls++
                true
            },
            receiveReward = { _, _ ->
                receiveCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("ipDraw")

        assertEquals(5, queryCalls)
        assertEquals(2, executeCalls)
        assertEquals(2, receiveCalls)
        assertTrue(result.executions.all {
            it.outcome == ChouChouLeRewardOutcome.CONFIRMED
        })
        assertTrue(result.finished)
    }

    @Test
    fun `没有剩余次数的TODO任务不执行`() {
        var executeCalls = 0
        val workflow = ChouChouLeRewardWorkflow(
            queryTasks = {
                taskResponse(
                    taskJson(
                        "LIMIT_TASK",
                        "次数已用完",
                        "TODO",
                        "",
                        rightsTimes = 0,
                        rightsTimesLimit = 0
                    )
                )
            },
            executeTask = { _, _ ->
                executeCalls++
                false
            },
            receiveReward = { _, _ -> """{"success":true}""" }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(0, executeCalls)
        assertTrue(result.finished)
    }

    @Test
    fun `执行失败的非捐赠TODO任务保持待处理`() {
        var executeCalls = 0
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
            executeTask = { _, _ ->
                executeCalls++
                false
            },
            receiveReward = { _, _ ->
                receiveCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.process("dailyDraw")

        assertEquals(3, executeCalls)
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
        innerAction: String,
        rightsTimes: Int = 0,
        rightsTimesLimit: Int = 1,
        receivedAwardCount: Int = 0,
        awardCount: Int = 0
    ): String {
        return """
            {
              "bizKey":"$taskId",
              "title":"$title",
              "taskStatus":"$status",
              "innerAction":"$innerAction",
              "rightsTimes":$rightsTimes,
              "rightsTimesLimit":$rightsTimesLimit,
              "alreadyReceiveStageAwardCount":$receivedAwardCount,
              "awardCount":$awardCount
            }
        """.trimIndent()
    }
}
