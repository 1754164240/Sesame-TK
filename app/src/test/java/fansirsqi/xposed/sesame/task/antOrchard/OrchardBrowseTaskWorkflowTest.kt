package fansirsqi.xposed.sesame.task.antOrchard

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OrchardBrowseTaskWorkflowTest {

    @Test
    fun `浏览触发后任务已进入终态则立即确认`() = runBlocking {
        var startCalls = 0
        var queryCalls = 0
        var finishCalls = 0
        var waitCalls = 0
        val workflow = OrchardBrowseTaskWorkflow(
            listTasks = {
                queryCalls++
                taskResponse("FINISHED")
            },
            startBrowse = { source ->
                assertEquals("taobao_orchard", source)
                startCalls++
                """{"resultCode":"100"}"""
            },
            finishTask = { _, _ ->
                finishCalls++
                ""
            },
            waitForBrowse = { waitCalls++ }
        )

        val outcome = workflow.process(task())

        assertEquals(AntOrchardRewardOutcome.CONFIRMED, outcome)
        assertEquals(1, startCalls)
        assertEquals(1, queryCalls)
        assertEquals(0, finishCalls)
        assertEquals(0, waitCalls)
    }

    @Test
    fun `浏览计时后完成并再次回查终态`() = runBlocking {
        var queryCalls = 0
        var finishCalls = 0
        var waitCalls = 0
        val workflow = OrchardBrowseTaskWorkflow(
            listTasks = {
                queryCalls++
                taskResponse(
                    if (queryCalls == 1) "TODO" else "FINISHED"
                )
            },
            startBrowse = { """{"resultCode":"100"}""" },
            finishTask = { task, source ->
                assertEquals("task-1", task.id)
                assertEquals("taobao_orchard", source)
                finishCalls++
                """{"resultCode":"100"}"""
            },
            waitForBrowse = { waitCalls++ }
        )

        val outcome = workflow.process(task())

        assertEquals(AntOrchardRewardOutcome.CONFIRMED, outcome)
        assertEquals(2, queryCalls)
        assertEquals(1, finishCalls)
        assertEquals(1, waitCalls)
    }

    @Test
    fun `计时结束但状态未推进时保留重试`() = runBlocking {
        var queryCalls = 0
        val workflow = OrchardBrowseTaskWorkflow(
            listTasks = {
                queryCalls++
                taskResponse("TODO")
            },
            startBrowse = { """{"resultCode":"100"}""" },
            finishTask = { _, _ -> """{"resultCode":"100"}""" },
            waitForBrowse = {}
        )

        val outcome = workflow.process(task())

        assertEquals(AntOrchardRewardOutcome.RETRY, outcome)
        assertEquals(2, queryCalls)
    }

    @Test
    fun `查询结构未知时不继续完成动作`() = runBlocking {
        var finishCalls = 0
        var waitCalls = 0
        val workflow = OrchardBrowseTaskWorkflow(
            listTasks = { """{"resultCode":"100","data":{}}""" },
            startBrowse = { """{"resultCode":"100"}""" },
            finishTask = { _, _ ->
                finishCalls++
                ""
            },
            waitForBrowse = { waitCalls++ }
        )

        val outcome = workflow.process(task())

        assertEquals(AntOrchardRewardOutcome.RETRY, outcome)
        assertEquals(0, finishCalls)
        assertEquals(0, waitCalls)
    }

    @Test
    fun `未匹配白名单或浏览触发失败时不查询和完成`() = runBlocking {
        var queryCalls = 0
        var finishCalls = 0
        val workflow = OrchardBrowseTaskWorkflow(
            listTasks = {
                queryCalls++
                taskResponse("FINISHED")
            },
            startBrowse = { """{"success":false}""" },
            finishTask = { _, _ ->
                finishCalls++
                ""
            },
            waitForBrowse = {}
        )

        assertEquals(
            AntOrchardRewardOutcome.SKIPPED_UNSAFE,
            workflow.process(task(groupId = "unknown"))
        )
        assertEquals(
            AntOrchardRewardOutcome.RETRY,
            workflow.process(task())
        )
        assertEquals(0, queryCalls)
        assertEquals(0, finishCalls)
    }

    private fun task(
        groupId: String = "12172"
    ): AntOrchardTaskState {
        return AntOrchardRewardPolicy.parseTasks(
            """
                {
                  "resultCode":"100",
                  "taskList":[{
                    "taskId":"task-1",
                    "groupId":"$groupId",
                    "taskStatus":"TODO",
                    "actionType":"VISIT",
                    "sceneCode":"972",
                    "taskPlantType":"TAOBAO",
                    "taskDisplayConfig":{
                      "title":"逛淘宝得肥料",
                      "targetUrl":"alipays://platformapi/startapp?source=taobao_orchard"
                    }
                  }]
                }
            """.trimIndent()
        ).tasks.single()
    }

    private fun taskResponse(status: String): String {
        return """
            {
              "resultCode":"100",
              "taskList":[{
                "taskId":"task-1",
                "groupId":"12172",
                "taskStatus":"$status",
                "actionType":"VISIT",
                "sceneCode":"972",
                "taskPlantType":"TAOBAO"
              }]
            }
        """.trimIndent()
    }
}
