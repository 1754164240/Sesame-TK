package fansirsqi.xposed.sesame.task.antFishPond

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FishPondWorkflowTest {

    @Test
    fun `稳健流程处理基础奖励安全任务和一次福利鱼`() {
        val fake = FakeFishPondGateway()
        val result = FishPondWorkflow(fake).run(
            taskEnabled = true,
            autoFishEnabled = true,
            todayFishCount = 0,
            dailyLimit = 30,
            riskToken = "risk-token"
        )

        assertEquals(1, result.confirmedFishCount)
        assertTrue(result.progressed)
        assertFalse(result.retryNeeded)
        assertEquals(listOf("FISH_TASK_14"), fake.claimedTasks)
        assertEquals(listOf("FISH_TASK_15"), fake.completedTasks)
        assertFalse(fake.completedTasks.contains("AD_TASK"))
        assertEquals(
            listOf("GIFT_BOX" to "receiveAward", "TOMORROW_ROD" to "FINISH"),
            fake.triggeredActivities
        )
        assertEquals(listOf("2026-07-28"), fake.signKeys)
        assertEquals(1, fake.angleCalls)
        assertEquals(listOf("fish-biz" to "SPECIAL_BIG_ZONE"), fake.positionCalls)
        assertTrue(fake.syncCalls.isNotEmpty())
    }

    @Test
    fun `缺少风控令牌只跳过钓鱼并继续领取任务奖励`() {
        val fake = FakeFishPondGateway()
        val result = FishPondWorkflow(fake).run(
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
    fun `达到每日上限或首页暂态失败时安全停止`() {
        val limited = FakeFishPondGateway()
        val limitedResult = FishPondWorkflow(limited).run(
            taskEnabled = false,
            autoFishEnabled = true,
            todayFishCount = 30,
            dailyLimit = 30,
            riskToken = "risk-token"
        )
        assertEquals(0, limitedResult.confirmedFishCount)
        assertEquals(0, limited.angleCalls)

        val failed = FakeFishPondGateway().apply { indexResponse = "" }
        val failedResult = FishPondWorkflow(failed).run(
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
    fun `角度成功但福利鱼定位失败仍立即计入每日次数`() {
        val fake = FakeFishPondGateway().apply {
            positionResponse = """{"success":false}"""
        }
        val persistedCounts = mutableListOf<Int>()
        val result = FishPondWorkflow(fake).run(
            taskEnabled = false,
            autoFishEnabled = true,
            todayFishCount = 5,
            dailyLimit = 30,
            riskToken = "risk-token",
            onFishConfirmed = persistedCounts::add
        )

        assertEquals(1, result.confirmedFishCount)
        assertTrue(result.retryNeeded)
        assertEquals(listOf(6), persistedCounts)
    }

    private class FakeFishPondGateway : FishPondGateway {
        var indexResponse =
            """{"success":true,"data":{"rodSumCount":1,"canExchange":false}}"""
        var listTaskCalls = 0
        var angleCalls = 0
        var positionResponse = """{"success":true}"""
        val signKeys = mutableListOf<String>()
        val syncCalls = mutableListOf<List<String>>()
        val triggeredActivities = mutableListOf<Pair<String, String>>()
        val claimedTasks = mutableListOf<String>()
        val completedTasks = mutableListOf<String>()
        val positionCalls = mutableListOf<Pair<String, String>>()

        override fun fishpondIndex(): String = indexResponse

        override fun fishpondSyncIndex(syncTypes: List<String>): String {
            syncCalls += syncTypes
            val rodCount = if (angleCalls == 0) 1 else 0
            return """{"success":true,"data":{"rodSumCount":$rodCount}}"""
        }

        override fun querySubplotsActivity(): String =
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

        override fun triggerSubplotsActivity(activityType: String, actionType: String): String {
            triggeredActivities += activityType to actionType
            return """{"success":true}"""
        }

        override fun listTask(): String {
            listTaskCalls++
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
                        "taskId":"FISH_TASK_14",
                        "sceneCode":"ANTFISHPOND_TASK",
                        "taskStatus":"FINISHED",
                        "actionType":"GOFISH",
                        "taskTitle":"浏览鱼池"
                      },
                      {
                        "taskId":"FISH_TASK_15",
                        "sceneCode":"ANTFISHPOND_TASK",
                        "taskStatus":"TODO",
                        "actionType":"VISIT",
                        "taskTitle":"查看鱼池进度"
                      },
                      {
                        "taskId":"AD_TASK",
                        "sceneCode":"ANTFISHPOND_TASK",
                        "taskStatus":"TODO",
                        "taskTitle":"观看广告",
                        "adBizNo":"ad-1"
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
            completedTasks += taskType
            return """{"success":true}"""
        }

        override fun receiveTaskAward(taskType: String, sceneCode: String): String {
            claimedTasks += taskType
            return """{"success":true}"""
        }

        override fun fishpondAngle(riskToken: String): String {
            angleCalls++
            return """
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
        }

        override fun fishpondAngleRodPositioning(bizNo: String, areaType: String): String {
            positionCalls += bizNo to areaType
            return positionResponse
        }
    }
}
