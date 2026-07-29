package fansirsqi.xposed.sesame.task.antSports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AntSportsRouteIntegrationTest {

    @Test
    fun `完整路线发现链路只返回待收见闻关联路线`() = runBlocking {
        val workflow = AntSportsRouteWorkflow(
            queryUser = { "" },
            queryPath = { "" },
            queryWorldMap = {
                """
                    {
                      "success": true,
                      "data": {
                        "cityList": [{
                          "cityId":"city-1",
                          "status":"ONLINE"
                        }]
                      }
                    }
                """.trimIndent()
            },
            queryCityPath = {
                """
                    {
                      "success": true,
                      "data": {
                        "cityPathList": [
                          {
                            "pathId":"path-received",
                            "pathCompleteStatus":"JOIN"
                          },
                          {
                            "pathId":"path-target",
                            "pathCompleteStatus":"JOIN"
                          }
                        ]
                      }
                    }
                """.trimIndent()
            },
            queryCityKnowledgeDetail = {
                """
                    {
                      "success": true,
                      "result": {
                        "data": {
                          "cityKnowledgeList": [
                            {
                              "knowledgeId":"knowledge-received",
                              "pathId":"path-received",
                              "status":"RECEIVED"
                            },
                            {
                              "knowledgeId":"knowledge-target",
                              "pathId":"path-target",
                              "status":"NOT_RECEIVE"
                            }
                          ]
                        }
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
        assertEquals("path-target", result.pathId)
    }
}
