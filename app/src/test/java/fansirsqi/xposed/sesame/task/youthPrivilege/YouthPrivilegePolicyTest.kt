package fansirsqi.xposed.sesame.task.youthPrivilege

import org.junit.Assert.assertEquals
import org.junit.Test

class YouthPrivilegePolicyTest {

    @Test
    fun `签到查询仅在服务端明确要求签到时执行动作`() {
        assertEquals(
            YouthCheckInDecision.EXECUTE,
            YouthPrivilegePolicy.checkInDecision(
                """
                {
                  "resultCode": "SUCCESS",
                  "studentCheckInInfo": {
                    "action": "CHECK_IN_ACTION"
                  }
                }
                """.trimIndent()
            )
        )
    }

    @Test
    fun `签到查询仅在服务端明确已签到时确认完成`() {
        assertEquals(
            YouthCheckInDecision.CONFIRMED,
            YouthPrivilegePolicy.checkInDecision(
                """
                {
                  "success": true,
                  "data": {
                    "studentCheckInInfo": {
                      "action": "CHECKED_IN_ACTION"
                    }
                  }
                }
                """.trimIndent()
            )
        )
    }

    @Test
    fun `签到失败和未知动作均保留重试`() {
        assertEquals(
            YouthCheckInDecision.RETRY,
            YouthPrivilegePolicy.checkInDecision(
                """{"success":false,"studentCheckInInfo":{"action":"CHECK_IN_ACTION"}}"""
            )
        )
        assertEquals(
            YouthCheckInDecision.RETRY,
            YouthPrivilegePolicy.checkInDecision(
                """{"code":"100","result":{"studentCheckInInfo":{"action":"DO_TASK"}}}"""
            )
        )
        assertEquals(
            YouthCheckInDecision.RETRY,
            YouthPrivilegePolicy.checkInDecision("not-json")
        )
    }

    @Test
    fun `免费道具已完成时允许领奖`() {
        assertEquals(
            YouthRewardDecision.CLAIM,
            YouthPrivilegePolicy.rewardDecision(
                rewardResponse(
                    wrapper = "root",
                    taskType = "NENGLIANGZHAO_20230807",
                    taskStatus = "FINISHED"
                ),
                "NENGLIANGZHAO_20230807"
            )
        )
    }

    @Test
    fun `免费道具仅在服务端明确已领取时确认完成`() {
        assertEquals(
            YouthRewardDecision.CONFIRMED,
            YouthPrivilegePolicy.rewardDecision(
                rewardResponse(
                    wrapper = "result",
                    taskType = "JIASUQI_20230808",
                    taskStatus = "RECEIVED"
                ),
                "JIASUQI_20230808"
            )
        )
    }

    @Test
    fun `免费道具失败错任务和未知结构均保留重试`() {
        assertEquals(
            YouthRewardDecision.RETRY,
            YouthPrivilegePolicy.rewardDecision(
                """
                {
                  "success": false,
                  "forestTasksNew": [{
                    "taskInfoList": [{
                      "taskBaseInfo": {
                        "taskType": "DAXUESHENG_SJK",
                        "taskStatus": "RECEIVED"
                      }
                    }]
                  }]
                }
                """.trimIndent(),
                "DAXUESHENG_SJK"
            )
        )
        assertEquals(
            YouthRewardDecision.RETRY,
            YouthPrivilegePolicy.rewardDecision(
                rewardResponse(
                    wrapper = "data",
                    taskType = "OTHER_TASK",
                    taskStatus = "RECEIVED"
                ),
                "DAXUESHENG_SJK"
            )
        )
        assertEquals(
            YouthRewardDecision.RETRY,
            YouthPrivilegePolicy.rewardDecision(
                """{"resultCode":"SUCCESS","data":{}}""",
                "DAXUESHENG_SJK"
            )
        )
    }

    private fun rewardResponse(
        wrapper: String,
        taskType: String,
        taskStatus: String
    ): String {
        val payload =
            """
            {
              "forestTasksNew": [{
                "taskInfoList": [{
                  "taskBaseInfo": {
                    "taskType": "$taskType",
                    "taskStatus": "$taskStatus"
                  }
                }]
              }]
            }
            """.trimIndent()

        return when (wrapper) {
            "root" -> """{"resultCode":"SUCCESS",${payload.removePrefix("{")}}"""
            "data" -> """{"code":"100","data":$payload}"""
            "result" -> """{"success":true,"result":$payload}"""
            else -> error("未知测试容器")
        }
    }
}
