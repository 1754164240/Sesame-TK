package fansirsqi.xposed.sesame.task.antSports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntSportsRoutePolicyTest {

    @Test
    fun `动作响应只有明确成功时才视为已受理`() {
        assertFalse(AntSportsRoutePolicy.isActionAccepted(""))
        assertFalse(AntSportsRoutePolicy.isActionAccepted("{}"))
        assertFalse(
            AntSportsRoutePolicy.isActionAccepted(
                """{"success":false}"""
            )
        )
        assertTrue(
            AntSportsRoutePolicy.isActionAccepted(
                """{"success":true}"""
            )
        )
        assertTrue(
            AntSportsRoutePolicy.isActionAccepted(
                """{"resultCode":"SUCCESS"}"""
            )
        )
    }

    @Test
    fun `城市路线只返回未完成路径且不回退已完成路径`() {
        val mixed = AntSportsRoutePolicy.parseCityPaths(
            cityPathResponse(
                "path-done" to "COMPLETED",
                "path-next" to "JOIN"
            )
        )
        val completed = AntSportsRoutePolicy.parseCityPaths(
            cityPathResponse("path-done" to "COMPLETED")
        )

        assertTrue(mixed.recognized)
        assertEquals(listOf("path-next"), mixed.unfinishedPathIds)
        assertTrue(completed.recognized)
        assertTrue(completed.unfinishedPathIds.isEmpty())
    }

    @Test
    fun `世界地图只保留有效在线城市`() {
        val snapshot = AntSportsRoutePolicy.parseWorldMap(
            """
                {
                  "success": true,
                  "data": {
                    "cityList": [
                      {"cityId":"000000","status":"ONLINE"},
                      {"cityId":"city-offline","status":"OFFLINE"},
                      {"cityId":"city-1","status":"ONLINE"}
                    ]
                  }
                }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals(listOf("city-1"), snapshot.onlineCityIds)
    }

    @Test
    fun `路线宝箱只有回查后消失才确认领取`() {
        val before = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "JOIN",
                forwardStepCount = 120,
                remainStepCount = 80,
                eventIds = listOf("box-1")
            )
        )
        val unchanged = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "JOIN",
                forwardStepCount = 120,
                remainStepCount = 80,
                eventIds = listOf("box-1")
            )
        )
        val claimed = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "JOIN",
                forwardStepCount = 120,
                remainStepCount = 80,
                eventIds = emptyList()
            )
        )

        assertEquals(
            AntSportsRouteOutcome.RETRY,
            AntSportsRoutePolicy.verifyEvent("box-1", before, unchanged)
        )
        assertEquals(
            AntSportsRouteOutcome.CONFIRMED,
            AntSportsRoutePolicy.verifyEvent("box-1", before, claimed)
        )
    }

    @Test
    fun `加入路线必须回查用户和目标路线都已切换`() {
        val targetPath = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-new",
                completion = "JOIN",
                forwardStepCount = 0,
                remainStepCount = 100,
                eventIds = emptyList()
            )
        )
        val oldUser = AntSportsRoutePolicy.parseUser(
            userResponse("path-old")
        )
        val targetUser = AntSportsRoutePolicy.parseUser(
            userResponse("path-new")
        )

        assertEquals(
            AntSportsRouteOutcome.RETRY,
            AntSportsRoutePolicy.verifyJoin(
                "path-new",
                oldUser,
                targetPath
            )
        )
        assertEquals(
            AntSportsRouteOutcome.CONFIRMED,
            AntSportsRoutePolicy.verifyJoin(
                "path-new",
                targetUser,
                targetPath
            )
        )
    }

    @Test
    fun `行走动作按步数推进和路线终态分类`() {
        val before = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "JOIN",
                forwardStepCount = 120,
                remainStepCount = 80,
                eventIds = emptyList()
            )
        )
        val unchanged = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "JOIN",
                forwardStepCount = 120,
                remainStepCount = 80,
                eventIds = emptyList()
            )
        )
        val progressed = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "JOIN",
                forwardStepCount = 180,
                remainStepCount = 20,
                eventIds = emptyList()
            )
        )
        val completed = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "COMPLETED",
                forwardStepCount = 200,
                remainStepCount = 0,
                eventIds = emptyList()
            )
        )

        assertEquals(
            AntSportsRouteOutcome.RETRY,
            AntSportsRoutePolicy.verifyWalk(before, unchanged)
        )
        assertEquals(
            AntSportsRouteOutcome.PARTIAL,
            AntSportsRoutePolicy.verifyWalk(before, progressed)
        )
        assertEquals(
            AntSportsRouteOutcome.CONFIRMED,
            AntSportsRoutePolicy.verifyWalk(before, completed)
        )
    }

    @Test
    fun `路线详情解析完整状态和待领宝箱`() {
        val snapshot = AntSportsRoutePolicy.parsePath(
            pathResponse(
                pathId = "path-1",
                completion = "JOIN",
                forwardStepCount = 120,
                remainStepCount = 80,
                eventIds = listOf("box-1", "box-2")
            )
        )

        assertTrue(snapshot.recognized)
        assertEquals("path-1", snapshot.pathId)
        assertEquals("JOIN", snapshot.completion)
        assertEquals(120, snapshot.forwardStepCount)
        assertEquals(80, snapshot.remainStepCount)
        assertEquals(20, snapshot.minGoStepCount)
        assertEquals(500, snapshot.pathStepCount)
        assertEquals(setOf("box-1", "box-2"), snapshot.eventIds)
    }

    private fun pathResponse(
        pathId: String,
        completion: String,
        forwardStepCount: Int,
        remainStepCount: Int,
        eventIds: List<String>
    ): String {
        val boxes = eventIds.joinToString(",") {
            """{"boxNo":"$it"}"""
        }
        return """
            {
              "success": true,
              "data": {
                "userPathStep": {
                  "pathId": "$pathId",
                  "pathName": "测试路线",
                  "pathCompleteStatus": "$completion",
                  "forwardStepCount": $forwardStepCount,
                  "remainStepCount": $remainStepCount
                },
                "path": {
                  "minGoStepCount": 20,
                  "pathStepCount": 500
                },
                "treasureBoxList": [$boxes]
              }
            }
        """.trimIndent()
    }

    private fun userResponse(joinedPathId: String): String {
        return """
            {
              "success": true,
              "data": {
                "joinedPathId": "$joinedPathId"
              }
            }
        """.trimIndent()
    }

    private fun cityPathResponse(
        vararg paths: Pair<String, String>
    ): String {
        val pathItems = paths.joinToString(",") { (pathId, status) ->
            """{"pathId":"$pathId","pathCompleteStatus":"$status"}"""
        }
        return """
            {
              "success": true,
              "data": {
                "cityPathList": [$pathItems]
              }
            }
        """.trimIndent()
    }
}
