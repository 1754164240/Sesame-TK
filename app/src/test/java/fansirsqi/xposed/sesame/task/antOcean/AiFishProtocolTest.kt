package fansirsqi.xposed.sesame.task.antOcean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFishProtocolTest {

    @Test
    fun `主页识别被抓状态和摸鱼进度`() {
        val snapshot = AiFishProtocol.parseHome(
            """
                {
                  "success": true,
                  "resultCode": "SUCCESS",
                  "myFish": {
                    "interactVO": {
                      "fishInteractStatus": "CAPTURED",
                      "remainTouchChance": 3,
                      "touchTotal": 0
                    }
                  }
                }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals("CAPTURED", snapshot.fishStatus)
        assertEquals(3, snapshot.remainTouchChance)
        assertEquals(0, snapshot.touchTotal)
    }

    @Test
    fun `主页兼容响应数据包装且未知结构不推定成功`() {
        val wrapped = AiFishProtocol.parseHome(
            """
                {
                  "resData": {
                    "success": true,
                    "resultCode": "SUCCESS",
                    "myFish": {
                      "interactVO": {
                        "fishInteractStatus": "CAN_TOUCH",
                        "remainTouchChance": 2,
                        "touchTotal": 7
                      }
                    }
                  }
                }
            """.trimIndent()
        )
        val unknown = AiFishProtocol.parseHome(
            """{"success":true,"resultCode":"SUCCESS"}"""
        )

        assertTrue(wrapped.recognized)
        assertEquals("CAN_TOUCH", wrapped.fishStatus)
        assertEquals(2, wrapped.remainTouchChance)
        assertEquals(7, wrapped.touchTotal)
        assertFalse(unknown.recognized)
        assertNull(unknown.fishStatus)
    }

    @Test
    fun `任务列表解析字符串业务信息并限制等待时间`() {
        val snapshot = AiFishProtocol.parseTasks(
            taskResponse(
                task("AIFISH_NEGATIVE", "TODO", -5, "OTHER"),
                task("AIFISH_SHJF", "TODO", 5, "VISIT_FLOAT_BALL"),
                task("AIFISH_LONG", "TODO", 90, "VISIT_FLOAT_BALL")
            )
        )

        assertTrue(snapshot.recognized)
        assertEquals(3, snapshot.tasks.size)
        assertEquals(0, snapshot.tasks[0].waitSeconds)
        assertEquals("逛生活缴费", snapshot.tasks[1].title)
        assertEquals(5, snapshot.tasks[1].waitSeconds)
        assertEquals(60, snapshot.tasks[2].waitSeconds)
    }

    @Test
    fun `救援任务只稳定选择带正数倒计时的浏览任务`() {
        val snapshot = AiFishProtocol.parseTasks(
            taskResponse(
                task(
                    "AIFISH_RESCUE_Z",
                    "TODO",
                    15,
                    "VISIT_FLOAT_BALL",
                    sceneCode = AiFishProtocol.RESCUE_SCENE
                ),
                task(
                    "AIFISH_RESCUE_A",
                    "TODO",
                    10,
                    "VISIT_FLOAT_BALL",
                    sceneCode = AiFishProtocol.RESCUE_SCENE
                ),
                task(
                    "AIFISH_RESCUE_ZERO",
                    "TODO",
                    0,
                    "VISIT_FLOAT_BALL",
                    sceneCode = AiFishProtocol.RESCUE_SCENE
                ),
                task(
                    "AIFISH_RESCUE_OTHER",
                    "TODO",
                    15,
                    "OTHER",
                    sceneCode = AiFishProtocol.RESCUE_SCENE
                )
            )
        )

        assertEquals(
            "AIFISH_RESCUE_A",
            AiFishProtocol.selectRescueTask(snapshot)?.taskType
        )
    }

    @Test
    fun `动作成功兼容抓包中的三类响应`() {
        assertTrue(
            AiFishProtocol.isActionAccepted(
                """{"success":true,"resultCode":"SUCCESS"}"""
            )
        )
        assertTrue(
            AiFishProtocol.isActionAccepted(
                """{"success":true,"code":"100000000"}"""
            )
        )
        assertTrue(
            AiFishProtocol.isActionAccepted(
                """{"resData":{"success":true,"resultCode":"SUCCESS"}}"""
            )
        )
        assertFalse(AiFishProtocol.isActionAccepted("""{"success":false}"""))
        assertFalse(AiFishProtocol.isActionAccepted("not-json"))
    }

    @Test
    fun `缺少任务数组时结构未知`() {
        val snapshot = AiFishProtocol.parseTasks(
            """{"success":true,"code":"100000000"}"""
        )

        assertFalse(snapshot.recognized)
        assertTrue(snapshot.tasks.isEmpty())
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
        val title = if (taskType == "AIFISH_SHJF") {
            "逛生活缴费"
        } else {
            taskType
        }
        return """
            {
              "taskBaseInfo": {
                "bizInfo": "{\"taskTitle\":\"$title\",\"autoCompleteTask\":false}",
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
}
