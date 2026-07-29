package fansirsqi.xposed.sesame.task.antMember

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberTaskWorkflowTest {

    @Test
    fun `浏览动作仅返回ACK且详情未终态时保留重试`() = runBlocking {
        var executeCalls = 0
        var detailCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(taskListResponse(status = "PROCESSING"))
            },
            applyTask = { """{"success":true}""" },
            executeTask = {
                executeCalls++
                """{"success":true}"""
            },
            queryTaskDetail = {
                detailCalls++
                taskDetailResponse(status = "PROCESSING")
            },
            pauseBeforeCompletion = {}
        )

        val result = workflow.run()

        assertEquals(1, executeCalls)
        assertEquals(1, detailCalls)
        assertEquals(0, result.confirmed)
        assertEquals(1, result.failed)
        assertTrue(result.retryable)
        assertEquals(
            MemberTaskVerification.UNCONFIRMED,
            result.outcomes.single().verification
        )
    }

    @Test
    fun `广告任务不得调用伪完成或详情回查`() = runBlocking {
        var detailCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(adTaskListResponse(status = "PROCESSING"))
            },
            applyTask = { """{"success":true}""" },
            executeTask = { """{"success":true}""" },
            queryTaskDetail = {
                detailCalls++
                adTaskDetailResponse(status = "PROCESSING")
            },
            pauseBeforeCompletion = {}
        )

        val result = workflow.run()

        assertEquals(0, detailCalls)
        assertEquals(0, result.confirmed)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        assertEquals(false, result.retryable)
        assertEquals(
            MemberTaskDecision.SKIP_AD,
            result.outcomes.single().decision
        )
        assertEquals(null, result.outcomes.single().verification)
    }

    @Test
    fun `CALL_APP任务不调用动作且仅以详情终态确认`() = runBlocking {
        var actionCalls = 0
        var detailCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(callAppTaskListResponse())
            },
            applyTask = {
                actionCalls++
                """{"success":true}"""
            },
            executeTask = {
                actionCalls++
                """{"success":true}"""
            },
            queryTaskDetail = {
                detailCalls++
                taskDetailResponse(status = "COMPLETE")
            },
            pauseBeforeCompletion = {}
        )

        val result = workflow.run()

        assertEquals(0, actionCalls)
        assertEquals(1, detailCalls)
        assertEquals(1, result.confirmed)
        assertEquals(0, result.failed)
    }

    @Test
    fun `报名ACK后详情状态未推进时不继续执行`() = runBlocking {
        var applyCalls = 0
        var executeCalls = 0
        var detailCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(taskListResponse(status = "INIT"))
            },
            applyTask = {
                applyCalls++
                """{"success":true}"""
            },
            executeTask = {
                executeCalls++
                """{"success":true}"""
            },
            queryTaskDetail = {
                detailCalls++
                taskDetailResponse(status = "INIT")
            },
            pauseBeforeCompletion = {}
        )

        val result = workflow.run()

        assertEquals(1, applyCalls)
        assertEquals(1, detailCalls)
        assertEquals(0, executeCalls)
        assertEquals(0, result.confirmed)
        assertEquals(1, result.failed)
        assertTrue(result.retryable)
    }

    @Test
    fun `多查询源包含未知结构时不得判定暂无任务`() = runBlocking {
        var actionCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(
                    """{"success":true,"resultData":{"pureTaskList":[]}}""",
                    """{"success":true,"resultData":{"unexpectedTaskList":[]}}"""
                )
            },
            applyTask = {
                actionCalls++
                """{"success":true}"""
            },
            executeTask = {
                actionCalls++
                """{"success":true}"""
            },
            queryTaskDetail = { """{"success":true}""" },
            pauseBeforeCompletion = {}
        )

        val result = workflow.run()

        assertEquals(0, actionCalls)
        assertEquals(false, result.recognized)
        assertEquals(1, result.failed)
        assertTrue(result.retryable)
    }

    @Test
    fun `本轮动作数量不超过累计任务剩余数量`() = runBlocking {
        var executeCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(twoBrowseTasksResponse())
            },
            applyTask = { """{"success":true}""" },
            executeTask = {
                executeCalls++
                """{"success":true}"""
            },
            queryTaskDetail = {
                taskDetailResponse(status = "COMPLETE")
            },
            pauseBeforeCompletion = {}
        )

        val result = workflow.run(maxActionTasks = 1)

        assertEquals(1, executeCalls)
        assertEquals(1, result.confirmed)
    }

    @Test
    fun `用户黑名单命中时白名单任务也不执行`() = runBlocking {
        var actionCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(taskListResponse(status = "PROCESSING"))
            },
            applyTask = {
                actionCalls++
                """{"success":true}"""
            },
            executeTask = {
                actionCalls++
                """{"success":true}"""
            },
            queryTaskDetail = { taskDetailResponse(status = "COMPLETE") },
            pauseBeforeCompletion = {},
            isTaskBlocked = { true }
        )

        val result = workflow.run()

        assertEquals(0, actionCalls)
        assertEquals(0, result.confirmed)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `服务端终态任务不会重复执行`() = runBlocking {
        var actionCalls = 0
        val workflow = MemberTaskWorkflow(
            queryTaskSources = {
                listOf(taskListResponse(status = "COMPLETE"))
            },
            applyTask = {
                actionCalls++
                """{"success":true}"""
            },
            executeTask = {
                actionCalls++
                """{"success":true}"""
            },
            queryTaskDetail = { taskDetailResponse(status = "COMPLETE") },
            pauseBeforeCompletion = {}
        )

        val result = workflow.run()

        assertEquals(0, actionCalls)
        assertEquals(0, result.confirmed)
        assertEquals(1, result.skipped)
    }

    private fun taskListResponse(status: String): String {
        return """
            {
              "success": true,
              "resultData": {
                "pureTaskList": [{
                  "processId": "process-1",
                  "status": "$status",
                  "simpleTaskConfig": {
                    "configId": "600202500151482",
                    "title": "浏览会员频道",
                    "browseSeconds": 15
                  },
                  "targetBusiness": ["BROWSE#15S#alipays://platformapi/startapp"]
                }]
              }
            }
        """.trimIndent()
    }

    private fun taskDetailResponse(status: String): String {
        return """
            {
              "success": true,
              "resultData": {
                "taskProcessVO": {
                  "processId": "process-1",
                  "status": "$status",
                  "simpleTaskConfig": {
                    "configId": "600202500151482",
                    "title": "浏览会员频道"
                  },
                  "targetBusiness": ["BROWSE#15S#alipays://platformapi/startapp"]
                }
              }
            }
        """.trimIndent()
    }

    private fun adTaskListResponse(status: String): String {
        return """
            {
              "success": true,
              "resultData": {
                "adTaskList": [{
                  "processId": "ad-process-1",
                  "status": "$status",
                  "adTask": true,
                  "lightsAdExtMap": {
                    "adId": "ad-1",
                    "bizId": "ad-biz-1"
                  },
                  "simpleTaskConfig": {
                    "configId": "32002001",
                    "title": "浏览会员广告",
                    "browseSeconds": 15
                  },
                  "targetBusiness": ["BROWSE#15S#alipays://platformapi/startapp"]
                }]
              }
            }
        """.trimIndent()
    }

    private fun adTaskDetailResponse(status: String): String {
        return """
            {
              "success": true,
              "resultData": {
                "taskProcessVO": {
                  "processId": "ad-process-1",
                  "status": "$status",
                  "adBizId": "ad-biz-1",
                  "simpleTaskConfig": {
                    "configId": "32002001",
                    "title": "浏览会员广告"
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun callAppTaskListResponse(): String {
        return """
            {
              "success": true,
              "resultData": {
                "pureTaskList": [{
                  "processId": "process-1",
                  "status": "PROCESSING",
                  "simpleTaskConfig": {
                    "configId": "600202500151482",
                    "title": "访问会员频道"
                  },
                  "targetBusiness": ["CALL_APP#member-channel"]
                }]
              }
            }
        """.trimIndent()
    }

    private fun twoBrowseTasksResponse(): String {
        return """
            {
              "success": true,
              "resultData": {
                "pureTaskList": [
                  {
                    "processId": "process-1",
                    "status": "PROCESSING",
                    "simpleTaskConfig": {
                      "configId": "600202500151482",
                      "title": "浏览会员频道"
                    },
                    "targetBusiness": ["BROWSE#15S#first"]
                  },
                  {
                    "processId": "process-2",
                    "status": "PROCESSING",
                    "simpleTaskConfig": {
                      "configId": "600202400075770",
                      "title": "浏览第二个会员频道"
                    },
                    "targetBusiness": ["BROWSE#15S#second"]
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
