package fansirsqi.xposed.sesame.task.antSports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AntSportsRouteWorkflowTest {

    @Test
    fun `用户已在目标路线时不重复调用加入动作`() = runBlocking {
        var actionCalls = 0
        val workflow = AntSportsRouteWorkflow(
            queryUser = { userResponse("path-new") },
            queryPath = {
                pathResponse(
                    forwardStepCount = 0,
                    pathId = "path-new"
                )
            },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = {
                actionCalls++
                """{"success":true}"""
            },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )

        val outcome = workflow.joinRoute("path-new")

        assertEquals(0, actionCalls)
        assertEquals(AntSportsRouteOutcome.RETRY, outcome)
    }

    @Test
    fun `路线前态未知或事件不存在时不调用动作`() = runBlocking {
        var walkCalls = 0
        var eventCalls = 0
        val workflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = { """{"success":true,"data":{}}""" },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = { "" },
            walkGo = { _, _ ->
                walkCalls++
                """{"success":true}"""
            },
            receiveEvent = {
                eventCalls++
                """{"success":true}"""
            }
        )

        assertEquals(
            AntSportsRouteOutcome.RETRY,
            workflow.advancePath("path-1", 20)
        )
        assertEquals(
            AntSportsRouteOutcome.RETRY,
            workflow.claimEvent("path-1", "box-1")
        )
        assertEquals(0, walkCalls)
        assertEquals(0, eventCalls)
    }

    @Test
    fun `三类动作响应为空时即使状态变化也保留重试`() = runBlocking {
        var walkQueryCalls = 0
        val walkWorkflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = {
                walkQueryCalls++
                pathResponse(
                    forwardStepCount = if (walkQueryCalls == 1) 100 else 120
                )
            },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = { "" },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )
        var joinQueryCalls = 0
        val joinWorkflow = AntSportsRouteWorkflow(
            queryUser = {
                joinQueryCalls++
                userResponse(
                    if (joinQueryCalls == 1) "path-old" else "path-new"
                )
            },
            queryPath = {
                pathResponse(
                    forwardStepCount = 0,
                    pathId = "path-new"
                )
            },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = { "" },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )
        var eventQueryCalls = 0
        val eventWorkflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = {
                eventQueryCalls++
                pathResponse(
                    forwardStepCount = 100,
                    eventIds = if (eventQueryCalls == 1) {
                        listOf("box-1")
                    } else {
                        emptyList()
                    }
                )
            },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = { "" },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )

        assertEquals(
            AntSportsRouteOutcome.RETRY,
            walkWorkflow.advancePath("path-1", 20)
        )
        assertEquals(
            AntSportsRouteOutcome.RETRY,
            joinWorkflow.joinRoute("path-new")
        )
        assertEquals(
            AntSportsRouteOutcome.RETRY,
            eventWorkflow.claimEvent("path-1", "box-1")
        )
    }

    @Test
    fun `按城市顺序返回首个未完成路线`() = runBlocking {
        val queriedCities = mutableListOf<String>()
        val workflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = { "" },
            queryWorldMap = {
                """
                    {
                      "success": true,
                      "data": {
                        "cityList": [
                          {"cityId":"city-1","status":"ONLINE"},
                          {"cityId":"city-2","status":"ONLINE"}
                        ]
                      }
                    }
                """.trimIndent()
            },
            queryCityPath = { cityId ->
                queriedCities += cityId
                if (cityId == "city-1") {
                    """{"success":true,"data":{"cityPathList":[{"pathId":"path-done","pathCompleteStatus":"COMPLETED"}]}}"""
                } else {
                    """{"success":true,"data":{"cityPathList":[{"pathId":"path-open","pathCompleteStatus":"JOIN"},{"pathId":"path-later","pathCompleteStatus":"JOIN"}]}}"""
                }
            },
            joinPath = { "" },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )

        val result = workflow.findJoinablePath("theme-1")

        assertEquals(true, result.recognized)
        assertEquals("path-open", result.pathId)
        assertEquals(listOf("city-1", "city-2"), queriedCities)
    }

    @Test
    fun `城市路径结构未知时保留重试`() = runBlocking {
        val workflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = { "" },
            queryWorldMap = {
                """
                    {
                      "success": true,
                      "data": {
                        "cityList": [
                          {"cityId":"city-1","status":"ONLINE"}
                        ]
                      }
                    }
                """.trimIndent()
            },
            queryCityPath = { """{"success":true,"data":{}}""" },
            joinPath = { "" },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )

        val result = workflow.findJoinablePath("theme-1")

        assertEquals(false, result.recognized)
        assertEquals(null, result.pathId)
    }

    @Test
    fun `所有城市路径已完成时不回退到已完成路线`() = runBlocking {
        val workflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = { "" },
            queryWorldMap = {
                """
                    {
                      "success": true,
                      "data": {
                        "cityList": [
                          {"cityId":"city-1","status":"ONLINE"}
                        ]
                      }
                    }
                """.trimIndent()
            },
            queryCityPath = {
                """
                    {
                      "success": true,
                      "data": {
                        "cityPathList": [{
                          "pathId":"path-done",
                          "pathCompleteStatus":"COMPLETED"
                        }]
                      }
                    }
                """.trimIndent()
            },
            joinPath = { "" },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )

        val result = workflow.findJoinablePath("theme-1")

        assertEquals(true, result.recognized)
        assertEquals(null, result.pathId)
    }

    @Test
    fun `宝箱ACK后同一事件仍存在时保留重试`() = runBlocking {
        var actionCalls = 0
        var queryCalls = 0
        val workflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = {
                queryCalls++
                pathResponse(
                    forwardStepCount = 120,
                    eventIds = listOf("box-1")
                )
            },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = { "" },
            walkGo = { _, _ -> "" },
            receiveEvent = {
                actionCalls++
                """{"success":true}"""
            }
        )

        val outcome = workflow.claimEvent("path-1", "box-1")

        assertEquals(1, actionCalls)
        assertEquals(2, queryCalls)
        assertEquals(AntSportsRouteOutcome.RETRY, outcome)
    }

    @Test
    fun `加入路线ACK后用户仍在旧路径时保留重试`() = runBlocking {
        var actionCalls = 0
        val workflow = AntSportsRouteWorkflow(
            queryUser = { userResponse("path-old") },
            queryPath = {
                pathResponse(
                    forwardStepCount = 0,
                    pathId = "path-new"
                )
            },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = {
                actionCalls++
                """{"success":true}"""
            },
            walkGo = { _, _ -> "" },
            receiveEvent = { "" }
        )

        val outcome = workflow.joinRoute("path-new")

        assertEquals(1, actionCalls)
        assertEquals(AntSportsRouteOutcome.RETRY, outcome)
    }

    @Test
    fun `行走ACK后步数未推进时保留重试`() = runBlocking {
        var actionCalls = 0
        var queryCalls = 0
        val workflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = {
                queryCalls++
                pathResponse(forwardStepCount = 120)
            },
            queryWorldMap = { "" },
            queryCityPath = { "" },
            joinPath = { "" },
            walkGo = { _, _ ->
                actionCalls++
                """{"success":true}"""
            },
            receiveEvent = { "" }
        )

        val outcome = workflow.advancePath("path-1", useStepCount = 60)

        assertEquals(1, actionCalls)
        assertEquals(2, queryCalls)
        assertEquals(AntSportsRouteOutcome.RETRY, outcome)
    }

    private fun pathResponse(
        forwardStepCount: Int,
        pathId: String = "path-1",
        eventIds: List<String> = emptyList()
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
                  "pathCompleteStatus": "JOIN",
                  "forwardStepCount": $forwardStepCount,
                  "remainStepCount": 100
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
              "data": {"joinedPathId":"$joinedPathId"}
            }
        """.trimIndent()
    }
}
