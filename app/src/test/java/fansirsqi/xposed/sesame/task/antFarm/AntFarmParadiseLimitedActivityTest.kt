package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AntFarmParadiseLimitedActivityTest {

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()
    }

    @Test
    fun 活动期内只返回两个可领取的限时活动任务() {
        val response = JSONObject(
            """
            {
              "success": true,
              "resultCode": "SUCCESS",
              "taskTriggerPlayInfo": {
                "taskList": [
                  {
                    "awardCount": 10,
                    "awardType": "gameCoin",
                    "bizInfo": {"title": "每日签到"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "FINISHED",
                    "taskType": "2026cc_lyqd"
                  },
                  {
                    "awardCount": 50,
                    "awardType": "gameCoin",
                    "bizInfo": {"title": "玩花园世界完成50居民订单"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "TODO",
                    "taskType": "2026cc_wdhysj"
                  },
                  {
                    "awardCount": 100,
                    "awardType": "gameCoin",
                    "bizInfo": {"title": "玩游戏累计开宝箱"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "FINISHED",
                    "taskType": "2026cc_GAME_ljkbx"
                  },
                  {
                    "awardCount": 100,
                    "awardType": "gameCoin",
                    "bizInfo": {"title": "已领宝箱"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "RECEIVED",
                    "taskType": "2026cc_GAME_ljkbx"
                  },
                  {
                    "awardCount": 10,
                    "awardType": "gameCoin",
                    "bizInfo": {"title": "其他场景签到"},
                    "sceneCode": "OTHER_SCENE",
                    "taskStatus": "FINISHED",
                    "taskType": "2026cc_lyqd"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val tasks = AntFarmParadiseLimitedActivity.claimableTasks(
            response,
            millisOf(2026, 6, 19, 8)
        )

        assertEquals(
            listOf("2026cc_lyqd", "2026cc_GAME_ljkbx"),
            tasks.map { it.taskType }
        )
        assertEquals("每日签到", tasks[0].title)
        assertEquals(10, tasks[0].awardCount)
        assertEquals("玩游戏累计开宝箱", tasks[1].title)
        assertEquals(100, tasks[1].awardCount)
    }

    @Test
    fun 活动截止后不再返回可领取任务() {
        val response = JSONObject(
            """
            {
              "success": true,
              "taskTriggerPlayInfo": {
                "taskList": [
                  {
                    "awardCount": 10,
                    "bizInfo": {"title": "每日签到"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "FINISHED",
                    "taskType": "2026cc_lyqd"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val tasks = AntFarmParadiseLimitedActivity.claimableTasks(
            response,
            millisOf(2027, 1, 1, 0)
        )

        assertTrue(tasks.isEmpty())
    }

    @Test
    fun 活动截止时刻仍然允许领取任务() {
        val response = JSONObject(
            """
            {
              "success": true,
              "taskTriggerPlayInfo": {
                "taskList": [
                  {
                    "awardCount": 10,
                    "bizInfo": {"title": "每日签到"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "FINISHED",
                    "taskType": "2026cc_lyqd"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val tasks = AntFarmParadiseLimitedActivity.claimableTasks(
            response,
            millisOf(2026, 12, 31, 23)
        )

        assertEquals(listOf("2026cc_lyqd"), tasks.map { it.taskType })
    }

    @Test
    fun 签到已领取后只返回累计开宝箱奖励任务() {
        val response = JSONObject(
            """
            {
              "success": true,
              "taskTriggerPlayInfo": {
                "taskList": [
                  {
                    "awardCount": 10,
                    "bizInfo": {"title": "每日签到"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "RECEIVED",
                    "taskType": "2026cc_lyqd"
                  },
                  {
                    "awardCount": 100,
                    "bizInfo": {"title": "玩游戏累计开宝箱"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "FINISHED",
                    "taskType": "2026cc_GAME_ljkbx"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val tasks = AntFarmParadiseLimitedActivity.claimableTasks(
            response,
            millisOf(2026, 6, 19, 8)
        )

        assertEquals(listOf("2026cc_GAME_ljkbx"), tasks.map { it.taskType })
        assertEquals(100, tasks.single().awardCount)
    }

    @Test
    fun 宝箱奖励未完成时需要先开宝箱() {
        val response = JSONObject(
            """
            {
              "success": true,
              "taskTriggerPlayInfo": {
                "taskList": [
                  {
                    "awardCount": 100,
                    "bizInfo": {"title": "玩游戏累计开宝箱"},
                    "sceneCode": "ANTFARM_LEYUAN_DAILY_TASK",
                    "taskStatus": "TODO",
                    "taskType": "2026cc_GAME_ljkbx"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertTrue(
            AntFarmParadiseLimitedActivity.shouldOpenTreasureBoxesBeforeClaim(
                response,
                millisOf(2026, 6, 19, 8)
            )
        )
    }
}
