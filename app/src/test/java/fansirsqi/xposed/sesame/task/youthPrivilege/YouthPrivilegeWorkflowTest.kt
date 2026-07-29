package fansirsqi.xposed.sesame.task.youthPrivilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouthPrivilegeWorkflowTest {

    @Test
    fun `签到动作后必须回查到已签到才确认完成`() {
        val gateway = FakeGateway(
            checkInResponses = ArrayDeque(
                listOf(
                    checkInResponse("CHECK_IN_ACTION"),
                    checkInResponse("CHECKED_IN_ACTION")
                )
            )
        )
        var confirmedCount = 0

        val result = YouthPrivilegeWorkflow(gateway).checkIn {
            confirmedCount += 1
        }

        assertTrue(result.confirmed)
        assertTrue(result.actionExecuted)
        assertFalse(result.retryNeeded)
        assertEquals(2, gateway.queryCheckInCount)
        assertEquals(1, gateway.executeCheckInCount)
        assertEquals(1, confirmedCount)
    }

    @Test
    fun `签到动作响应成功但回查未完成时保留重试`() {
        val gateway = FakeGateway(
            checkInResponses = ArrayDeque(
                listOf(
                    checkInResponse("CHECK_IN_ACTION"),
                    checkInResponse("CHECK_IN_ACTION")
                )
            )
        )
        var confirmed = false

        val result = YouthPrivilegeWorkflow(gateway).checkIn {
            confirmed = true
        }

        assertFalse(result.confirmed)
        assertTrue(result.actionExecuted)
        assertTrue(result.retryNeeded)
        assertFalse(confirmed)
    }

    @Test
    fun `免费道具领取后必须逐项回查到已领取`() {
        val reward = YouthForestReward(
            queryTaskType = "DXS_BHZ",
            rewardTaskType = "NENGLIANGZHAO_20230807",
            name = "保护罩"
        )
        val gateway = FakeGateway(
            rewardResponses = mutableMapOf(
                reward.queryTaskType to ArrayDeque(
                    listOf(
                        rewardResponse(reward.rewardTaskType, "FINISHED"),
                        rewardResponse(reward.rewardTaskType, "RECEIVED")
                    )
                )
            )
        )
        var confirmed = false

        val result = YouthPrivilegeWorkflow(gateway).claimForestProps(
            rewards = listOf(reward),
            onAllConfirmed = { confirmed = true }
        )

        assertTrue(result.confirmed)
        assertEquals(1, result.claimedCount)
        assertFalse(result.retryNeeded)
        assertEquals(listOf(reward.rewardTaskType), gateway.claimedRewardTypes)
        assertTrue(confirmed)
    }

    @Test
    fun `未知奖励结构不领奖也不写总完成标记`() {
        val reward = YouthForestReward(
            queryTaskType = "DXS_BHZ",
            rewardTaskType = "NENGLIANGZHAO_20230807",
            name = "保护罩"
        )
        val gateway = FakeGateway(
            rewardResponses = mutableMapOf(
                reward.queryTaskType to ArrayDeque(
                    listOf("""{"resultCode":"SUCCESS","forestTasksNew":[]}""")
                )
            )
        )
        var confirmed = false

        val result = YouthPrivilegeWorkflow(gateway).claimForestProps(
            rewards = listOf(reward),
            onAllConfirmed = { confirmed = true }
        )

        assertFalse(result.confirmed)
        assertEquals(0, result.claimedCount)
        assertTrue(result.retryNeeded)
        assertTrue(gateway.claimedRewardTypes.isEmpty())
        assertFalse(confirmed)
    }

    @Test
    fun `青春任务总览只查询不执行任何动作`() {
        val gateway = FakeGateway(
            checkInResponses = ArrayDeque(
                listOf(checkInResponse("CHECKED_IN_ACTION"))
            )
        )

        assertTrue(YouthPrivilegeWorkflow(gateway).queryTaskOverview())
        assertEquals(1, gateway.queryCheckInCount)
        assertEquals(0, gateway.executeCheckInCount)
        assertTrue(gateway.claimedRewardTypes.isEmpty())
    }

    private class FakeGateway(
        private val checkInResponses: ArrayDeque<String> = ArrayDeque(),
        private val rewardResponses: MutableMap<String, ArrayDeque<String>> =
            mutableMapOf()
    ) : YouthPrivilegeRpcGateway {
        var queryCheckInCount = 0
        var executeCheckInCount = 0
        val claimedRewardTypes = mutableListOf<String>()

        override fun queryCheckIn(): String {
            queryCheckInCount += 1
            return checkInResponses.removeFirstOrNull() ?: """{"success":false}"""
        }

        override fun executeCheckIn(): String {
            executeCheckInCount += 1
            return """{"success":true}"""
        }

        override fun queryForestReward(queryTaskType: String): String {
            return rewardResponses[queryTaskType]
                ?.removeFirstOrNull()
                ?: """{"success":false}"""
        }

        override fun claimForestReward(rewardTaskType: String): String {
            claimedRewardTypes += rewardTaskType
            return """{"success":true}"""
        }
    }

    private fun checkInResponse(action: String): String {
        return """
            {
              "resultCode": "SUCCESS",
              "studentCheckInInfo": {
                "action": "$action"
              }
            }
        """.trimIndent()
    }

    private fun rewardResponse(taskType: String, status: String): String {
        return """
            {
              "success": true,
              "forestTasksNew": [{
                "taskInfoList": [{
                  "taskBaseInfo": {
                    "taskType": "$taskType",
                    "taskStatus": "$status"
                  }
                }]
              }]
            }
        """.trimIndent()
    }
}
