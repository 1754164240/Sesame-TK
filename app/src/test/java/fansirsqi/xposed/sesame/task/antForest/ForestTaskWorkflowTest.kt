package fansirsqi.xposed.sesame.task.antForest

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestTaskWorkflowTest {

    @Test
    fun `游戏任务不会调用通用完成接口`() = runBlocking {
        var completeCalls = 0
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(taskResponse("mokuai_senlin_hlz", "TODO", "玩游戏完成一局"))
                )
            ),
            completeTask = {
                completeCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.run()

        assertEquals(0, completeCalls)
        assertEquals(1, result.skipped)
        assertEquals(0, result.confirmed)
        assertFalse(result.retryable)
    }

    @Test
    fun `签到ACK后仍未签到时保留重试`() = runBlocking {
        var signCalls = 0
        val unsigned = signResponse(signed = false)
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(unsigned),
                    listOf(unsigned)
                )
            ),
            sign = {
                signCalls++
                """{"success":true}"""
            }
        )

        val result = workflow.run()

        assertEquals(1, signCalls)
        assertEquals(0, result.confirmed)
        assertTrue(result.retryable)
        assertEquals(
            ForestTaskOutcome.RETRY,
            result.outcomes.single().outcome
        )
    }

    @Test
    fun `签到回查为已签到时确认完成`() = runBlocking {
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(signResponse(signed = false)),
                    listOf(signResponse(signed = true))
                )
            )
        )

        val result = workflow.run()

        assertEquals(1, result.confirmed)
        assertTrue(result.signConfirmed)
        assertFalse(result.retryable)
        assertEquals(
            ForestTaskOutcome.CONFIRMED,
            result.outcomes.single().outcome
        )
    }

    @Test
    fun `初始快照已签到时向调用方报告确认状态`() = runBlocking {
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(signResponse(signed = true))
                )
            )
        )

        val result = workflow.run()

        assertTrue(result.signConfirmed)
        assertEquals(0, result.confirmed)
        assertFalse(result.retryable)
    }

    @Test
    fun `多个签到实体存在未确认项时不得报告全部签到`() = runBlocking {
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(
                        """
                        {
                          "success": true,
                          "forestSignVOList": [
                            ${signObject("sign-1", signed = true)},
                            ${signObject("sign-2", signed = false)}
                          ]
                        }
                        """.trimIndent()
                    )
                )
            ),
            sign = {
                """{"success":false}"""
            }
        )

        val result = workflow.run()

        assertFalse(result.signConfirmed)
        assertTrue(result.retryable)
    }

    @Test
    fun `领奖ACK后仍为FINISHED时保留重试`() = runBlocking {
        val finished = taskResponse(
            "ANTFOREST_GREEN_BROWSE",
            "FINISHED",
            "浏览绿色会场"
        )
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(finished),
                    listOf(finished)
                )
            )
        )

        val result = workflow.run()

        assertEquals(0, result.confirmed)
        assertTrue(result.retryable)
        assertEquals(
            ForestTaskOutcome.RETRY,
            result.outcomes.single().outcome
        )
    }

    @Test
    fun `安全任务状态从TODO推进到FINISHED时确认动作`() = runBlocking {
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(
                        taskResponse(
                            "ANTFOREST_GREEN_BROWSE",
                            "TODO",
                            "浏览绿色会场"
                        )
                    ),
                    listOf(
                        taskResponse(
                            "ANTFOREST_GREEN_BROWSE",
                            "FINISHED",
                            "浏览绿色会场"
                        )
                    )
                )
            )
        )

        val result = workflow.run()

        assertEquals(1, result.confirmed)
        assertFalse(result.retryable)
    }

    @Test
    fun `被配置关闭的安全任务不调用完成接口`() = runBlocking {
        var completeCalls = 0
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(
                        taskResponse(
                            "ANTFOREST_DAKA",
                            "TODO",
                            "环保打卡"
                        )
                    )
                )
            ),
            completeTask = {
                completeCalls++
                """{"success":true}"""
            },
            allowTask = { false }
        )

        val result = workflow.run()

        assertEquals(0, completeCalls)
        assertEquals(1, result.skipped)
        assertFalse(result.retryable)
    }

    @Test
    fun `任务消失但回查源包含未知结构时不得确认`() = runBlocking {
        val workflow = workflow(
            responses = ArrayDeque(
                listOf(
                    listOf(
                        taskResponse(
                            "ANTFOREST_GREEN_BROWSE",
                            "FINISHED",
                            "浏览绿色会场"
                        )
                    ),
                    listOf(
                        """{"success":true,"taskInfoList":[]}""",
                        """{"success":true,"unexpectedTaskList":[]}"""
                    )
                )
            )
        )

        val result = workflow.run()

        assertEquals(0, result.confirmed)
        assertTrue(result.retryable)
    }

    private fun workflow(
        responses: ArrayDeque<List<String>>,
        sign: suspend (ForestSignState) -> String = {
            """{"success":true}"""
        },
        completeTask: suspend (ForestTaskState) -> String = {
            """{"success":true}"""
        },
        claimTask: suspend (ForestTaskState) -> String = {
            """{"success":true}"""
        },
        allowTask: (ForestTaskState) -> Boolean = { true }
    ): ForestTaskWorkflow {
        return ForestTaskWorkflow(
            queryTaskSources = {
                check(responses.isNotEmpty()) {
                    "测试查询响应不足"
                }
                responses.removeFirst()
            },
            sign = sign,
            completeTask = completeTask,
            claimTask = claimTask,
            allowTask = allowTask
        )
    }

    private fun taskResponse(
        taskType: String,
        status: String,
        title: String
    ): String {
        return """
            {
              "success": true,
              "taskInfoList": [{
                "taskBaseInfo": {
                  "sceneCode": "SCENE",
                  "taskType": "$taskType",
                  "taskStatus": "$status",
                  "bizInfo": "{\"taskTitle\":\"$title\"}"
                }
              }]
            }
        """.trimIndent()
    }

    private fun signResponse(signed: Boolean): String {
        return """
            {
              "success": true,
              "forestSignVOList": [${signObject("sign-1", signed)}]
            }
        """.trimIndent()
    }

    private fun signObject(signId: String, signed: Boolean): String {
        return """
            {
              "signId": "$signId",
              "sceneCode": "SCENE",
              "currentSignKey": "20260729",
              "signRecords": [{
                "signKey": "20260729",
                "signed": $signed,
                "awardCount": 8
              }]
            }
        """.trimIndent()
    }
}
