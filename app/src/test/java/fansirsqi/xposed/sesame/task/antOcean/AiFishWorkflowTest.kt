package fansirsqi.xposed.sesame.task.antOcean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFishWorkflowTest {

    @Test
    fun `被抓时等待救援任务后使用专用接口找回`() {
        val gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAPTURED", 3, 0),
                home("CAN_TOUCH", 3, 0),
                home("CAN_TOUCH", 0, 0)
            ),
            taskProvider = { scene ->
                if (scene == AiFishProtocol.RESCUE_SCENE) {
                    taskResponse(
                        task(
                            taskType = "AIFISH_RESCUE_BROWSE_15S",
                            status = "TODO",
                            waitSeconds = 15,
                            playType = "VISIT_FLOAT_BALL",
                            sceneCode = scene
                        )
                    )
                } else {
                    taskResponse()
                }
            }
        )

        val result = AiFishWorkflow(gateway).run()

        assertTrue(result.rescued)
        assertEquals(listOf(16_000L), gateway.waits)
        assertEquals(1, gateway.rescueCalls)
        assertTrue(gateway.finishCalls.isEmpty())
        assertTrue(gateway.receiveCalls.isEmpty())
    }

    @Test
    fun `未被抓时不查询和执行救援`() {
        val gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 0, 0),
                home("CAN_TOUCH", 0, 0)
            ),
            taskProvider = { taskResponse() }
        )

        val result = AiFishWorkflow(gateway).run()

        assertFalse(result.rescued)
        assertFalse(
            gateway.listScenes.contains(AiFishProtocol.RESCUE_SCENE)
        )
        assertEquals(0, gateway.rescueCalls)
    }

    @Test
    fun `找回后仍为被抓状态时不执行主任务和摸鱼`() {
        val gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAPTURED", 3, 0),
                home("CAPTURED", 3, 0)
            ),
            taskProvider = {
                taskResponse(
                    task(
                        taskType = "AIFISH_RESCUE_BROWSE_15S",
                        status = "TODO",
                        waitSeconds = 15,
                        playType = "VISIT_FLOAT_BALL",
                        sceneCode = AiFishProtocol.RESCUE_SCENE
                    )
                )
            }
        )

        val result = AiFishWorkflow(gateway).run()

        assertFalse(result.rescued)
        assertFalse(gateway.listScenes.contains(AiFishProtocol.MAIN_SCENE))
        assertEquals(0, gateway.touchCalls)
    }

    @Test
    fun `主场景所有待办任务只尝试一次且逐项回查领奖`() {
        lateinit var gateway: FakeAiFishGateway
        gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 0, 0),
                home("CAN_TOUCH", 0, 0)
            ),
            taskProvider = {
                val dailyStatus = if (
                    gateway.receiveCalls.any {
                        it.second == "daily_add_touch_fish"
                    }
                ) {
                    "RECEIVED"
                } else {
                    "FINISHED"
                }
                val browseStatus = when {
                    gateway.receiveCalls.any {
                        it.second == "AIFISH_SHJF"
                    } -> "RECEIVED"
                    gateway.finishCalls.any {
                        it.second == "AIFISH_SHJF"
                    } -> "FINISHED"
                    else -> "TODO"
                }
                taskResponse(
                    task("daily_add_touch_fish", dailyStatus, 0, "OTHER"),
                    task(
                        "AIFISH_SHJF",
                        browseStatus,
                        5,
                        "VISIT_FLOAT_BALL"
                    ),
                    task("AIFISH_UNKNOWN", "TODO", 0, "OTHER")
                )
            }
        )

        val result = AiFishWorkflow(gateway).run()

        assertEquals(
            listOf(
                AiFishProtocol.MAIN_SCENE to "AIFISH_SHJF",
                AiFishProtocol.MAIN_SCENE to "AIFISH_UNKNOWN"
            ),
            gateway.finishCalls
        )
        assertEquals(
            listOf(
                AiFishProtocol.MAIN_SCENE to "daily_add_touch_fish",
                AiFishProtocol.MAIN_SCENE to "AIFISH_SHJF"
            ),
            gateway.receiveCalls
        )
        assertEquals(listOf(5_000L, 0L), gateway.waits)
        assertEquals(1, result.completedTaskCount)
        assertEquals(2, result.receivedRewardCount)
    }

    @Test
    fun `保卫向日葵任务每天只完成一次`() {
        val gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 0, 0),
                home("CAN_TOUCH", 0, 0),
                home("CAN_TOUCH", 0, 0),
                home("CAN_TOUCH", 0, 0)
            ),
            taskProvider = {
                taskResponse(
                    task("AIFISH_ZHUANHUA_BWXRK", "TODO", 0, "OTHER")
                )
            }
        )

        AiFishWorkflow(gateway).run()
        AiFishWorkflow(gateway).run()

        assertEquals(
            listOf(AiFishProtocol.MAIN_SCENE to "AIFISH_ZHUANHUA_BWXRK"),
            gateway.finishCalls
        )
    }

    @Test
    fun `摸鱼以剩余次数或累计次数推进并在无进展时停止`() {
        val gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 3, 0),
                home("CAN_TOUCH", 3, 0)
            ),
            touchResponses = dequeOf(
                home("CAN_TOUCH", 2, 1),
                home("CAN_TOUCH", 2, 2),
                home("CAN_TOUCH", 2, 2)
            ),
            taskProvider = { taskResponse() }
        )

        val result = AiFishWorkflow(gateway).run()

        assertEquals(3, gateway.touchCalls)
        assertEquals(2, result.touchCount)
    }

    @Test
    fun `没有摸鱼次数时不调用摸鱼接口`() {
        val gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 0, 0),
                home("CAN_TOUCH", 0, 0)
            ),
            taskProvider = { taskResponse() }
        )

        val result = AiFishWorkflow(gateway).run()

        assertEquals(0, gateway.touchCalls)
        assertEquals(0, result.touchCount)
    }

    @Test
    fun `持续推进时摸鱼调用不超过二十次`() {
        val responses = ArrayDeque<String>()
        repeat(25) { index ->
            responses.addLast(home("CAN_TOUCH", 1, index + 1))
        }
        val gateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 1, 0),
                home("CAN_TOUCH", 1, 0)
            ),
            touchResponses = responses,
            taskProvider = { taskResponse() }
        )

        val result = AiFishWorkflow(gateway).run()

        assertEquals(20, gateway.touchCalls)
        assertEquals(20, result.touchCount)
    }

    @Test
    fun `摸鱼响应失败或未知时立即停止`() {
        val failedGateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 2, 0),
                home("CAN_TOUCH", 2, 0)
            ),
            touchResponses = dequeOf("""{"success":false}"""),
            taskProvider = { taskResponse() }
        )
        val unknownGateway = FakeAiFishGateway(
            homeResponses = dequeOf(
                home("CAN_TOUCH", 2, 0),
                home("CAN_TOUCH", 2, 0)
            ),
            touchResponses = dequeOf(
                """{"success":true,"resultCode":"SUCCESS"}"""
            ),
            taskProvider = { taskResponse() }
        )

        assertEquals(0, AiFishWorkflow(failedGateway).run().touchCount)
        assertEquals(0, AiFishWorkflow(unknownGateway).run().touchCount)
        assertEquals(1, failedGateway.touchCalls)
        assertEquals(1, unknownGateway.touchCalls)
    }

    @Test
    fun `状态接口失败时不进入主页和任务流程`() {
        val gateway = FakeAiFishGateway(
            statusResponse = """{"success":false}""",
            homeResponses = dequeOf(home("CAN_TOUCH", 2, 0)),
            taskProvider = { taskResponse() }
        )

        val result = AiFishWorkflow(gateway).run()

        assertFalse(result.available)
        assertEquals(0, gateway.homeCalls)
        assertTrue(gateway.listScenes.isEmpty())
    }

    private class FakeAiFishGateway(
        private val statusResponse: String =
            """{"success":true,"resultCode":"SUCCESS"}""",
        private val homeResponses: ArrayDeque<String>,
        private val touchResponses: ArrayDeque<String> = ArrayDeque(),
        private val taskProvider: (String) -> String
    ) : AiFishGateway {
        val listScenes = mutableListOf<String>()
        val finishCalls = mutableListOf<Pair<String, String>>()
        val receiveCalls = mutableListOf<Pair<String, String>>()
        val waits = mutableListOf<Long>()
        val completedTodayTaskTypes = mutableSetOf<String>()
        var homeCalls = 0
        var rescueCalls = 0
        var touchCalls = 0

        override fun queryStatus(): String = statusResponse

        override fun queryHome(): String {
            homeCalls++
            return homeResponses.removeFirst()
        }

        override fun listTasks(sceneCode: String): String {
            listScenes += sceneCode
            return taskProvider(sceneCode)
        }

        override fun finishTask(
            sceneCode: String,
            taskType: String
        ): String {
            finishCalls += sceneCode to taskType
            return """{"success":true,"code":"100000000"}"""
        }

        override fun receiveTaskAward(
            sceneCode: String,
            taskType: String
        ): String {
            receiveCalls += sceneCode to taskType
            return """{"success":true,"code":"100000000"}"""
        }

        override fun hasCompletedToday(taskType: String): Boolean {
            return completedTodayTaskTypes.contains(taskType)
        }

        override fun markCompletedToday(taskType: String) {
            completedTodayTaskTypes += taskType
        }

        override fun rescueFish(): String {
            rescueCalls++
            return """{"success":true,"resultCode":"SUCCESS"}"""
        }

        override fun touchFish(): String {
            touchCalls++
            return touchResponses.removeFirst()
        }

        override fun waitMillis(millis: Long) {
            waits += millis
        }
    }

    private fun home(
        status: String,
        remain: Int,
        touchTotal: Int
    ): String {
        return """
            {
              "success": true,
              "resultCode": "SUCCESS",
              "myFish": {
                "interactVO": {
                  "fishInteractStatus": "$status",
                  "remainTouchChance": $remain,
                  "touchTotal": $touchTotal
                }
              }
            }
        """.trimIndent()
    }

    private fun taskResponse(vararg tasks: String): String {
        return """
            {
              "success": true,
              "code": "100000000",
              "taskInfoList": [${tasks.joinToString(",")}]
            }
        """.trimIndent()
    }

    private fun task(
        taskType: String,
        status: String,
        waitSeconds: Int,
        playType: String,
        sceneCode: String = AiFishProtocol.MAIN_SCENE
    ): String {
        return """
            {
              "taskBaseInfo": {
                "bizInfo": "{\"taskTitle\":\"$taskType\",\"autoCompleteTask\":false}",
                "prodPlayParam": "{\"timeCount\":$waitSeconds}",
                "sceneCode": "$sceneCode",
                "taskStatus": "$status",
                "taskType": "$taskType",
                "taskProdPlayType": "$playType"
              },
              "taskRights": {
                "awardCount": 1,
                "directReceiveAward": false
              }
            }
        """.trimIndent()
    }

    private fun <T> dequeOf(vararg values: T): ArrayDeque<T> {
        return ArrayDeque(values.asList())
    }
}
