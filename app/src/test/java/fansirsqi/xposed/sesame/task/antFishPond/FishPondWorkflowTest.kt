package fansirsqi.xposed.sesame.task.antFishPond

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FishPondWorkflowTest {

    @Test
    fun `浏览任务按通知等待完成和同步的顺序执行`() = runBlocking {
        val fake = FakeFishPondGateway()
        val result = FishPondWorkflow(
            fake,
            waitForTask = { millis -> fake.events += "wait:$millis" }
        ).run(
            taskEnabled = true,
            autoFishEnabled = false,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = null
        )

        assertTrue(result.progressed)
        assertFalse(result.retryNeeded)
        assertEquals(
            listOf(
                "notice:ad-1",
                "queryAdConfig",
                "requestAdExposure",
                "wait:15000",
                "finish:AD_TASK:ad-1",
                "sync:FISH_ACTIVITY,TASK_DISPLAY,TOMORROW_ROD,LOTTERY_PLUS",
                "listTask"
            ),
            fake.events.windowed(7).first {
                it.first() == "notice:ad-1"
            }
        )
    }

    @Test
    fun `广告曝光失败仍继续完成并复查任务`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            exposureResponse = """{"success":false,"retCode":"217"}"""
        }

        val result = FishPondWorkflow(
            fake,
            waitForTask = { millis -> fake.events += "wait:$millis" }
        ).run(
            taskEnabled = true,
            autoFishEnabled = false,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = null
        )

        assertTrue(fake.events.contains("finish:AD_TASK:ad-1"))
        assertFalse(result.retryNeeded)
    }

    @Test
    fun `广告完成后任务状态未推进则保留重试`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            adTaskStatusAfterCompletion = "TODO"
        }

        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = false,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = null
        )

        assertTrue(result.retryNeeded)
    }

    @Test
    fun `广告任务完成后领奖并复查已领取终态`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            adTaskStatusesAfterCompletion.addAll(
                listOf("TODO", "FINISHED", "FINISHED", "RECEIVED")
            )
        }

        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = false,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = null
        )

        assertTrue(
            fake.events.toString(),
            fake.events.contains("claim:AD_TASK")
        )
        assertFalse(result.retryNeeded)
    }

    @Test
    fun `广告领奖后仍为完成状态则保留重试`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            adTaskStatusesAfterCompletion.addAll(
                listOf("TODO", "FINISHED", "FINISHED", "FINISHED")
            )
        }

        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = false,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = null
        )

        assertTrue(
            fake.events.toString(),
            fake.events.contains("claim:AD_TASK")
        )
        assertTrue(result.retryNeeded)
    }

    @Test
    fun `广告复查不接受其他场景的同名任务`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            adTaskSceneAfterCompletion = "OTHER_SCENE"
        }

        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = false,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = null
        )

        assertTrue(result.retryNeeded)
    }

    @Test
    fun `单个任务抛错仍继续后续领奖和钓鱼`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            thrownTaskType = "FISH_TASK_15"
        }
        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = true,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = "risk-token"
        )

        assertTrue(result.retryNeeded)
        assertEquals(listOf("FISH_TASK_14"), fake.claimedTasks)
        assertEquals(1, fake.angleCalls)
        assertEquals(1, result.confirmedFishCount)
    }

    @Test
    fun `支线查询失败仍继续主任务和钓鱼`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            subplotResponse = """{"success":false}"""
        }
        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = true,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = "risk-token"
        )

        assertTrue(result.retryNeeded)
        assertEquals(listOf("FISH_TASK_14"), fake.claimedTasks)
        assertTrue(fake.completedTasks.contains("FISH_TASK_15"))
        assertEquals(1, fake.angleCalls)
    }

    @Test
    fun `缺少风控令牌只跳过钓鱼并继续领取任务奖励`() = runBlocking {
        val fake = FakeFishPondGateway()
        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = true,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = null
        )

        assertEquals(0, result.confirmedFishCount)
        assertFalse(result.retryNeeded)
        assertEquals(listOf("FISH_TASK_14"), fake.claimedTasks)
        assertEquals(0, fake.angleCalls)
    }

    @Test
    fun `达到每日上限或首页暂态失败时安全停止`() = runBlocking {
        val limited = FakeFishPondGateway()
        val limitedResult = FishPondWorkflow(limited, waitForTask = {}).run(
            taskEnabled = false,
            autoFishEnabled = true,
            todayFishCount = 30,
            dailyLimit = 30,
            riskToken = "risk-token"
        )
        assertEquals(0, limitedResult.confirmedFishCount)
        assertEquals(0, limited.angleCalls)

        val failed = FakeFishPondGateway().apply { indexResponse = "" }
        val failedResult = FishPondWorkflow(failed, waitForTask = {}).run(
            taskEnabled = true,
            autoFishEnabled = true,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = "risk-token"
        )
        assertTrue(failedResult.retryNeeded)
        assertFalse(failedResult.progressed)
        assertEquals(0, failed.listTaskCalls)
        assertEquals(0, failed.angleCalls)
    }

    @Test
    fun `福利鱼定位失败时不计入每日次数`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            positionResponse = """{"success":false}"""
        }
        val persistedCounts = mutableListOf<Int>()
        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = false,
            autoFishEnabled = true,
            todayFishCount = 5,
            dailyLimit = 30,
            riskToken = "risk-token",
            onFishConfirmed = persistedCounts::add
        )

        assertEquals(0, result.confirmedFishCount)
        assertTrue(result.retryNeeded)
        assertTrue(persistedCounts.isEmpty())
    }

    @Test
    fun `定位流水状态异常时同步一次并停止本轮钓鱼`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            positionResponse =
                """{"success":false,"resultCode":"C09","resultDesc":"钓鱼流水状态异常"}"""
        }
        val persistedCounts = mutableListOf<Int>()

        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = false,
            autoFishEnabled = true,
            todayFishCount = 5,
            dailyLimit = 30,
            riskToken = "risk-token",
            onFishConfirmed = persistedCounts::add
        )

        assertEquals(1, fake.angleCalls)
        assertEquals(
            listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD", "LOTTERY_PLUS"),
            fake.syncCalls.last()
        )
        assertEquals(0, result.confirmedFishCount)
        assertTrue(result.retryNeeded)
        assertTrue(persistedCounts.isEmpty())
    }

    @Test
    fun `福利鱼定位成功后才计入每日次数`() = runBlocking {
        val fake = FakeFishPondGateway()
        val persistedCounts = mutableListOf<Int>()
        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = false,
            autoFishEnabled = true,
            todayFishCount = 5,
            dailyLimit = 30,
            riskToken = "risk-token",
            onFishConfirmed = persistedCounts::add
        )

        assertEquals(1, result.confirmedFishCount)
        assertFalse(result.retryNeeded)
        assertEquals(listOf(6), persistedCounts)
    }

    @Test
    fun `响应根节点要求定位时必须先定位再计数`() = runBlocking {
        val fake = FakeFishPondGateway().apply {
            angleResponse =
                """
                {
                  "success": true,
                  "needRodPositioning": true,
                  "rodSumCount": 1,
                  "angleResultInfo": {
                    "fishType":"BIG_FISH",
                    "bizNo":"fish-biz"
                  }
                }
                """.trimIndent()
        }
        val persistedCounts = mutableListOf<Int>()
        val result = FishPondWorkflow(fake, waitForTask = {}).run(
            taskEnabled = false,
            autoFishEnabled = true,
            todayFishCount = 5,
            dailyLimit = 30,
            riskToken = "risk-token",
            onFishConfirmed = persistedCounts::add
        )

        assertEquals(listOf("fish-biz" to "SPECIAL_BIG_ZONE"), fake.positionCalls)
        assertEquals(1, result.confirmedFishCount)
        assertEquals(listOf(6), persistedCounts)
    }

    private class FakeFishPondGateway : FishPondGateway {
        var indexResponse =
            """{"success":true,"data":{"rodSumCount":1,"canExchange":false}}"""
        var listTaskCalls = 0
        var angleCalls = 0
        var angleResponse =
            """
            {
              "success": true,
              "data": {
                "rodSumCount": 0,
                "angleResultInfo": {
                  "fishType":"WELFARE_FISH",
                  "bizNo":"fish-biz"
                }
              }
            }
            """.trimIndent()
        var positionResponse = """{"success":true}"""
        var exposureResponse = """{"success":true}"""
        var adTaskStatusAfterCompletion = "RECEIVED"
        val adTaskStatusesAfterCompletion = ArrayDeque<String>()
        var adTaskSceneAfterCompletion = "ANTFISHPOND_TASK"
        var failedTaskType: String? = null
        var thrownTaskType: String? = null
        var subplotResponse =
            """
            {
              "success": true,
              "data": {
                "subplotsActivityList": [
                  {"activityType":"GIFT_BOX","status":"TODO"},
                  {"activityType":"TOMORROW_ROD","status":"TODAY_TODO"}
                ]
              }
            }
            """.trimIndent()
        val signKeys = mutableListOf<String>()
        val syncCalls = mutableListOf<List<String>>()
        val triggeredActivities = mutableListOf<Pair<String, String>>()
        val claimedTasks = mutableListOf<String>()
        val completedTasks = mutableListOf<String>()
        val positionCalls = mutableListOf<Pair<String, String>>()
        val events = mutableListOf<String>()

        override fun fishpondIndex(): String = indexResponse

        override fun fishpondSyncIndex(syncTypes: List<String>): String {
            syncCalls += syncTypes
            events += "sync:${syncTypes.joinToString(",")}"
            val rodCount = if (angleCalls == 0) 1 else 0
            return """{"success":true,"data":{"rodSumCount":$rodCount}}"""
        }

        override fun querySubplotsActivity(): String = subplotResponse

        override fun triggerSubplotsActivity(activityType: String, actionType: String): String {
            triggeredActivities += activityType to actionType
            return """{"success":true}"""
        }

        override fun listTask(): String {
            listTaskCalls++
            events += "listTask"
            val adStatus = if (listTaskCalls == 1) {
                "TODO"
            } else {
                adTaskStatusesAfterCompletion.removeFirstOrNull()
                    ?: adTaskStatusAfterCompletion
            }
            val adScene = if (listTaskCalls == 1) {
                "ANTFISHPOND_TASK"
            } else {
                adTaskSceneAfterCompletion
            }
            return """
                {
                  "success": true,
                  "data": {
                    "signInfo": {
                      "list": [
                        {"today":true,"signed":false,"signKey":"2026-07-28"}
                      ]
                    },
                    "taskList": [
                      {
                        "taskId":"FISH_TASK_15",
                        "sceneCode":"ANTFISHPOND_TASK",
                        "taskStatus":"TODO",
                        "actionType":"VISIT",
                        "taskTitle":"查看鱼池进度"
                      },
                      {
                        "taskId":"FISH_TASK_14",
                        "sceneCode":"ANTFISHPOND_TASK",
                        "taskStatus":"${if ("FISH_TASK_14" in claimedTasks) "RECEIVED" else "FINISHED"}",
                        "actionType":"GOFISH",
                        "taskTitle":"浏览鱼池"
                      },
                      {
                        "taskId":"AD_TASK",
                        "sceneCode":"$adScene",
                        "taskStatus":"$adStatus",
                        "taskTitle":"观看广告",
                        "adBizNo":"ad-1",
                        "taskDisplayConfig":{
                          "desc":"浏览30秒得钓竿",
                          "targetUrl":"alipays://platformapi/startapp?renderConfigKey=query-space&spaceCode=exposure-space&url=https%3A%2F%2Frender.alipay.com%2Ffish.html"
                        }
                      }
                    ]
                  }
                }
            """.trimIndent()
        }

        override fun sign(signKey: String): String {
            signKeys += signKey
            return """{"success":true}"""
        }

        override fun fishpondExchangeReward(): String = """{"success":true}"""

        override fun finishTask(taskType: String, sceneCode: String): String {
            return finishTask(taskType, sceneCode, null)
        }

        override fun fishpondAdNotice(adBizNo: String): String {
            events += "notice:$adBizNo"
            return """{"success":true}"""
        }

        override fun queryAdTaskConfig(spaceCode: String): String {
            events += "queryAdConfig"
            return """{"success":true,"resultData":{"duration":15.0}}"""
        }

        override fun requestAdExposure(spaceCode: String, pageUrl: String): String {
            events += "requestAdExposure"
            return exposureResponse
        }

        override fun finishTask(
            taskType: String,
            sceneCode: String,
            adBizNo: String?
        ): String {
            if (taskType == thrownTaskType) {
                error("任务调用异常")
            }
            completedTasks += taskType
            events += "finish:$taskType:${adBizNo.orEmpty()}"
            return if (taskType == failedTaskType) {
                """{"success":false}"""
            } else {
                """{"success":true}"""
            }
        }

        override fun receiveTaskAward(taskType: String, sceneCode: String): String {
            claimedTasks += taskType
            events += "claim:$taskType"
            return """{"success":true}"""
        }

        override fun fishpondAngle(riskToken: String): String {
            angleCalls++
            return angleResponse
        }

        override fun fishpondAngleRodPositioning(bizNo: String, areaType: String): String {
            positionCalls += bizNo to areaType
            return positionResponse
        }
    }
}
