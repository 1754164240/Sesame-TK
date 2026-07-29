package fansirsqi.xposed.sesame.task.antSports

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NeverlandPolicyTest {

    @Test
    fun `签到只有明确未签到或已签到状态才可决策`() {
        assertEquals(
            NeverlandSignDecision.EXECUTE,
            NeverlandPolicy.signDecision(
                """{"success":true,"data":{"continuousSignInfo":{"signedToday":false}}}"""
            )
        )
        assertEquals(
            NeverlandSignDecision.CONFIRMED,
            NeverlandPolicy.signDecision(
                """{"resultCode":"SUCCESS","result":{"continuousSignInfo":{"signedToday":true}}}"""
            )
        )
        assertEquals(
            NeverlandSignDecision.CONFIRMED,
            NeverlandPolicy.signDecision(
                """{"success":false,"errorCode":"ALREADY_SIGN_IN"}"""
            )
        )
        assertEquals(
            NeverlandSignDecision.RETRY,
            NeverlandPolicy.signDecision(
                """{"success":false,"errorCode":"SYSTEM_BUSY"}"""
            )
        )
    }

    @Test
    fun `任务解析兼容多个容器和任务标识字段`() {
        val taskCenter = NeverlandPolicy.parseTasks(
            """
            {
              "success": true,
              "data": {
                "taskCenterTaskVOS": [{
                  "id": "center-1",
                  "taskType": "LIGHT_TASK",
                  "taskStatus": "INIT",
                  "title": "浏览任务"
                }]
              }
            }
            """.trimIndent()
        )
        val taskList = NeverlandPolicy.parseTasks(
            """
            {
              "code": "100",
              "result": {
                "taskInfoList": [{
                  "taskBaseInfo": {
                    "taskId": "task-2",
                    "taskType": "PROMOKERNEL_TASK",
                    "taskStatus": "TO_RECEIVE",
                    "taskName": "活动任务"
                  }
                }]
              }
            }
            """.trimIndent()
        )
        val recordList = NeverlandPolicy.parseTasks(
            """
            {
              "success": true,
              "taskList": [{
                "taskRecordId": "record-3",
                "type": "LIGHT_TASK",
                "status": "RECEIVED",
                "name": "记录任务"
              }]
            }
            """.trimIndent()
        )

        assertTrue(taskCenter.recognized)
        assertEquals("center-1", taskCenter.tasks.single().id)
        assertEquals("task-2", taskList.tasks.single().id)
        assertEquals("record-3", recordList.tasks.single().id)
    }

    @Test
    fun `任务动作后状态未推进不确认`() {
        assertFalse(
            NeverlandPolicy.isTaskTransitionConfirmed(
                previousStatus = "INIT",
                currentStatus = "INIT"
            )
        )
        assertTrue(
            NeverlandPolicy.isTaskTransitionConfirmed(
                previousStatus = "INIT",
                currentStatus = "TO_RECEIVE"
            )
        )
        assertFalse(
            NeverlandPolicy.isTaskTransitionConfirmed(
                previousStatus = "TO_RECEIVE",
                currentStatus = "TO_RECEIVE"
            )
        )
        assertTrue(
            NeverlandPolicy.isTaskTransitionConfirmed(
                previousStatus = "TO_RECEIVE",
                currentStatus = "RECEIVED"
            )
        )
    }

    @Test
    fun `泡泡记录兼容多种容器和记录标识`() {
        val records = NeverlandPolicy.parseBubbleRecords(
            """
            {
              "success": true,
              "data": {
                "rewardRecords": [{
                  "recordId": "reward-1",
                  "rewardStatus": "WAIT_RECEIVE"
                }],
                "bubbleTaskVOS": [{
                  "medEnergyBallInfoRecordId": "bubble-2",
                  "bubbleTaskStatus": "RECEIVED"
                }]
              }
            }
            """.trimIndent()
        )

        assertTrue(records.recognized)
        assertEquals(setOf("reward-1", "bubble-2"), records.records.map { it.id }.toSet())
    }

    @Test
    fun `泡泡领取后记录消失或进入终态才确认`() {
        val beforeIds = setOf("bubble-1")

        assertFalse(
            NeverlandPolicy.areBubbleClaimsConfirmed(
                beforeIds,
                """{"success":true,"data":{"bubbleTaskVOS":[{"recordId":"bubble-1","status":"WAIT_RECEIVE"}]}}"""
            )
        )
        assertTrue(
            NeverlandPolicy.areBubbleClaimsConfirmed(
                beforeIds,
                """{"success":true,"data":{"bubbleTaskVOS":[]}}"""
            )
        )
        assertFalse(
            NeverlandPolicy.areBubbleClaimsConfirmed(
                beforeIds,
                """{"success":true,"data":{}}"""
            )
        )
    }

    @Test
    fun `浏览泡泡奖励必须回查到记录消失或终态`() {
        assertFalse(
            NeverlandPolicy.isBubbleEncryptConfirmed(
                "encrypt-1",
                """
                {
                  "success": true,
                  "data": {
                    "taskInfos": [{
                      "encryptValue": "encrypt-1",
                      "status": "INIT"
                    }]
                  }
                }
                """.trimIndent()
            )
        )
        assertTrue(
            NeverlandPolicy.isBubbleEncryptConfirmed(
                "encrypt-1",
                """{"success":true,"data":{"taskInfos":[]}}"""
            )
        )
    }

    @Test
    fun `运动运行路径不得调用动态价格购买接口`() {
        val source = File(
            "src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSports.kt"
        ).readText()

        assertFalse(source.contains("AntSportsRpcCall.buyMember("))
        assertTrue(source.contains("queryMemberPriceRanking"))
    }

    @Test
    fun `未知结构不会把任务中心推定为完成`() {
        assertFalse(
            NeverlandPolicy.isTaskCenterConfirmedDone(
                JSONObject("""{"success":true,"data":{}}""")
            )
        )
        assertTrue(
            NeverlandPolicy.isTaskCenterConfirmedDone(
                JSONObject(
                    """
                    {
                      "success": true,
                      "data": {
                        "taskCenterTaskVOS": [{
                          "taskId": "done-1",
                          "taskStatus": "RECEIVED"
                        }]
                      }
                    }
                    """.trimIndent()
                )
            )
        )
    }
}
