package fansirsqi.xposed.sesame.task.antOrchard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenBeanWorkflowTest {

    @Test
    fun `普通任务完成同步领奖并确认终态`() {
        val fake = FakeGoldenBeanGateway(
            initialTasks = listOf(task("JINDOULEYUAN_TRIGGER", "TODO", "GAMECENTER_TRIGGER")),
            syncTasks = listOf(
                listOf(task("JINDOULEYUAN_TRIGGER", "FINISHED", "GAMECENTER_TRIGGER")),
                listOf(task("JINDOULEYUAN_TRIGGER", "RECEIVED", "GAMECENTER_TRIGGER"))
            )
        )

        val result = GoldenBeanWorkflow(fake).run()

        assertEquals(
            listOf(
                "index",
                "finish:JINDOULEYUAN_TRIGGER",
                "sync",
                "claim:JINDOULEYUAN_TRIGGER",
                "sync"
            ),
            fake.events
        )
        assertTrue(result.progressed)
        assertFalse(result.retryNeeded)
        assertEquals(1, result.claimedCount)
    }

    @Test
    fun `财运签使用专用接口并确认已领取`() {
        val fake = FakeGoldenBeanGateway(
            initialTasks = listOf(task("FORTUNE_DRAW", "TODO", "FORTUNE_DRAW")),
            syncTasks = listOf(
                listOf(task("FORTUNE_DRAW", "RECEIVED", "FORTUNE_DRAW"))
            )
        )

        val result = GoldenBeanWorkflow(fake).run()

        assertEquals(listOf("index", "fortuneDraw", "sync"), fake.events)
        assertTrue(result.progressed)
        assertFalse(result.retryNeeded)
        assertEquals(0, result.claimedCount)
    }

    @Test
    fun `支付和余额宝待办不产生写请求`() {
        val fake = FakeGoldenBeanGateway(
            initialTasks = listOf(
                task("GOLDEN_BEAN_TASK_XIANSHANGZHIFU", "TODO", "VISIT"),
                task("GOLDEN_BEAN_TASK_YUEBAO", "TODO", "VISIT")
            )
        )

        val result = GoldenBeanWorkflow(fake).run()

        assertEquals(listOf("index"), fake.events)
        assertFalse(result.progressed)
        assertFalse(result.retryNeeded)
    }

    @Test
    fun `完成接口成功但状态未推进时不领奖`() {
        val fake = FakeGoldenBeanGateway(
            initialTasks = listOf(
                task("GOLDENBEAN_GAME_ZH_CGNNC", "TODO", "VISIT")
            ),
            syncTasks = listOf(
                listOf(task("GOLDENBEAN_GAME_ZH_CGNNC", "TODO", "VISIT"))
            )
        )

        val result = GoldenBeanWorkflow(fake).run()

        assertEquals(
            listOf("index", "finish:GOLDENBEAN_GAME_ZH_CGNNC", "sync"),
            fake.events
        )
        assertTrue(result.retryNeeded)
        assertEquals(0, result.claimedCount)
    }

    @Test
    fun `单个任务异常后继续领取其他已完成任务`() {
        val fake = FakeGoldenBeanGateway(
            initialTasks = listOf(
                task("GOLDENBEAN_GAME_ZH_CGNNC", "TODO", "VISIT"),
                task("GOLDEN_BEAN_TASK_XIANSHANGZHIFU", "FINISHED", "VISIT")
            ),
            syncTasks = listOf(
                listOf(
                    task("GOLDENBEAN_GAME_ZH_CGNNC", "TODO", "VISIT"),
                    task("GOLDEN_BEAN_TASK_XIANSHANGZHIFU", "RECEIVED", "VISIT")
                )
            ),
            throwingFinishTask = "GOLDENBEAN_GAME_ZH_CGNNC"
        )

        val result = GoldenBeanWorkflow(fake).run()

        assertTrue(fake.events.contains("claim:GOLDEN_BEAN_TASK_XIANSHANGZHIFU"))
        assertEquals(1, result.claimedCount)
        assertTrue(result.retryNeeded)
    }

    @Test
    fun `同步后解锁的新任务在下一轮继续闭环`() {
        val fake = FakeGoldenBeanGateway(
            initialTasks = listOf(
                task("JINDOULEYUAN_TRIGGER", "TODO", "GAMECENTER_TRIGGER")
            ),
            syncTasks = listOf(
                listOf(
                    task("JINDOULEYUAN_TRIGGER", "FINISHED", "GAMECENTER_TRIGGER")
                ),
                listOf(
                    task("JINDOULEYUAN_TRIGGER", "RECEIVED", "GAMECENTER_TRIGGER"),
                    task("TEST_PUSH_SUBSCRIBE", "TODO", "PUSH_SUBSCRIBE")
                ),
                listOf(
                    task("JINDOULEYUAN_TRIGGER", "RECEIVED", "GAMECENTER_TRIGGER"),
                    task("TEST_PUSH_SUBSCRIBE", "FINISHED", "PUSH_SUBSCRIBE")
                ),
                listOf(
                    task("JINDOULEYUAN_TRIGGER", "RECEIVED", "GAMECENTER_TRIGGER"),
                    task("TEST_PUSH_SUBSCRIBE", "RECEIVED", "PUSH_SUBSCRIBE")
                )
            )
        )

        val result = GoldenBeanWorkflow(fake).run()

        assertTrue(fake.events.contains("finish:TEST_PUSH_SUBSCRIBE"))
        assertTrue(fake.events.contains("claim:TEST_PUSH_SUBSCRIBE"))
        assertEquals(2, result.claimedCount)
        assertFalse(result.retryNeeded)
    }

    private fun task(
        type: String,
        status: String,
        actionType: String
    ) = TaskFixture(type, status, actionType)

    private data class TaskFixture(
        val type: String,
        val status: String,
        val actionType: String
    )

    private class FakeGoldenBeanGateway(
        initialTasks: List<TaskFixture>,
        syncTasks: List<List<TaskFixture>> = emptyList(),
        private val throwingFinishTask: String? = null
    ) : GoldenBeanGateway {
        val events = mutableListOf<String>()
        private val initialResponse = response(initialTasks)
        private val syncResponses = ArrayDeque(syncTasks.map(::response))

        override fun index(): String {
            events += "index"
            return initialResponse
        }

        override fun sync(syncTypes: List<String>): String {
            events += "sync"
            return syncResponses.removeFirstOrNull()
                ?: """{"success":true,"taskList":[]}"""
        }

        override fun fortuneDraw(): String {
            events += "fortuneDraw"
            return """{"success":true}"""
        }

        override fun finishTask(taskType: String, sceneCode: String): String {
            events += "finish:$taskType"
            if (taskType == throwingFinishTask) {
                error("任务完成接口异常")
            }
            return """{"success":true}"""
        }

        override fun receiveTaskAward(taskType: String, sceneCode: String): String {
            events += "claim:$taskType"
            return """{"success":true}"""
        }

        companion object {
            private fun response(tasks: List<TaskFixture>): String {
                val body = tasks.joinToString(",") { task ->
                    """
                    {
                      "taskId":"${task.type}",
                      "sceneCode":"GOLDEN_BEAN_MASTER_TASK",
                      "taskStatus":"${task.status}",
                      "actionType":"${task.actionType}",
                      "taskDisplayConfig":{"title":"${task.type}"}
                    }
                    """.trimIndent()
                }
                return """{"success":true,"taskList":[$body]}"""
            }
        }
    }
}
