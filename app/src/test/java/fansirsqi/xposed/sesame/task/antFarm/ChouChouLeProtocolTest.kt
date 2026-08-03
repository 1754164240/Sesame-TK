package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChouChouLeProtocolTest {

    @Test
    fun `直接探测抽抽乐活动并为IP场景使用抓包来源`() {
        val requests = mutableListOf<Triple<String, String, String>>()

        val drawTypes = ChouChouLeProtocol.discoverDrawTypes(nowMillis = 1_785_737_163_191L) {
                scene, otherScene, source ->
            requests += Triple(scene, otherScene, source)
            if (scene == "ipDrawMachine") {
                """{
                    "success": true,
                    "drawTimes": 0,
                    "drawMachineActivity": {
                        "activityId": "ipDrawMachine_260712",
                        "endTime": 1786204799000
                    }
                }"""
            } else {
                """{"success":true}"""
            }
        }

        assertEquals(listOf("ipDraw"), drawTypes)
        assertEquals(
            listOf(
                Triple("ipDrawMachine", "dailyDrawMachine", "ip_ccl"),
                Triple("dailyDrawMachine", "ipDrawMachine", "antfarm_villa")
            ),
            requests
        )
    }

    @Test
    fun `已过期活动不会进入抽抽乐流程`() {
        val response = """{
            "success": true,
            "drawMachineActivity": {
                "activityId": "ipDrawMachine_260712",
                "endTime": 1786204799000
            }
        }"""

        assertFalse(
            ChouChouLeProtocol.isActivityAvailable(
                response = response,
                nowMillis = 1_786_204_800_000L
            )
        )
    }

    @Test
    fun `杂货铺任务按页面时长等待后才调用完成接口`() {
        val events = mutableListOf<String>()
        val targetUrl = "alipays://platformapi/startapp?appId=2060090000304921" +
            "&spaceCode=MYZYDETCJJ_FEEDS_20250428120325" +
            "&renderConfigKey=mediaScene%2327%23%23adPosId%232025042822702040737" +
            "%23%23spaceCode%23MYZYDETCJJ_FEEDS_20250428120325" +
            "&iepTaskType=IP_SHANGYEHUA_TASK"

        val success = ChouChouLeProtocol.executeBrowseTask(
            targetUrl = targetUrl,
            description = "浏览杂货铺15s，可得1次机会",
            queryLayer = { layerSpaceCode ->
                events += "query:$layerSpaceCode"
                """{
                    "success": true,
                    "resultData": {"duration": 15.0}
                }"""
            },
            sleeper = { waitMillis -> events += "sleep:$waitMillis" },
            finishTask = {
                events += "finish"
                """{"success":true,"code":"100000000"}"""
            }
        )

        assertTrue(success)
        assertEquals(
            listOf(
                "query:mediaScene#27##adPosId#2025042822702040737##spaceCode#" +
                    "MYZYDETCJJ_FEEDS_20250428120325",
                "sleep:15000",
                "finish"
            ),
            events
        )
    }

    @Test
    fun `页面时长接口异常时使用任务描述中的等待时间`() {
        val waits = mutableListOf<Long>()

        val success = ChouChouLeProtocol.executeBrowseTask(
            targetUrl = "alipays://platformapi/startapp?spaceCode=TEST_SPACE",
            description = "浏览杂货铺15s，可得1次机会",
            queryLayer = { """{"success":false}""" },
            sleeper = { waits += it },
            finishTask = { """{"success":true}""" }
        )

        assertTrue(success)
        assertEquals(listOf(15_000L), waits)
    }
}
