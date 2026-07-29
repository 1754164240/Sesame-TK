package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameCreditRewardPolicyTest {
    @Test
    fun `次日奖励必须由入口列表识别且领奖后变为不可领取`() {
        val before = """
            {
              "success": true,
              "data": {
                "entryList": [{
                  "entryCode": "ALCHEMY_STAGE_REWARD",
                  "nextDayAwardDTO": {
                    "awardAvailable": true,
                    "awardId": "award-1",
                    "pointValue": 12
                  }
                }]
              }
            }
        """.trimIndent()
        val after = before.replace(
            "\"awardAvailable\": true",
            "\"awardAvailable\": false"
        )

        val snapshot = SesameCreditRewardPolicy.parseNextDayAward(before)

        assertTrue(snapshot.recognized)
        assertEquals("award-1", snapshot.award?.awardId)
        assertEquals(12, snapshot.award?.pointValue)
        assertTrue(
            SesameCreditRewardPolicy.isNextDayAwardConfirmed(
                after,
                "award-1"
            )
        )
        assertFalse(
            SesameCreditRewardPolicy.isNextDayAwardConfirmed(
                """{"success":true,"data":{}}""",
                "award-1"
            )
        )
    }

    @Test
    fun `已识别空次日奖励入口与未知结构分离`() {
        val empty = SesameCreditRewardPolicy.parseNextDayAward(
            """{"success":true,"data":{"entryList":[]}}"""
        )
        val unknown = SesameCreditRewardPolicy.parseNextDayAward(
            """{"success":true,"data":{}}"""
        )

        assertTrue(empty.recognized)
        assertNull(empty.award)
        assertFalse(unknown.recognized)
    }

    @Test
    fun `时段奖励领取后仍可领取时保留重试`() {
        val before = timeLimitedResponse(state = 1)
        val after = timeLimitedResponse(state = 2)

        val snapshot = SesameCreditRewardPolicy.parseTimeLimitedTask(before)

        assertTrue(snapshot.recognized)
        assertTrue(snapshot.task?.claimable == true)
        assertTrue(
            SesameCreditRewardPolicy.isTimeLimitedRewardConfirmed(
                after,
                "meal-1"
            )
        )
        assertFalse(
            SesameCreditRewardPolicy.isTimeLimitedRewardConfirmed(
                before,
                "meal-1"
            )
        )
        assertFalse(
            SesameCreditRewardPolicy.isTimeLimitedRewardConfirmed(
                """{"success":true,"data":{}}""",
                "meal-1"
            )
        )
    }

    @Test
    fun `芝麻粒待收记录消失或不再未领取才确认`() {
        val before = feedbackResponse(
            id = "feedback-1",
            status = "UNCLAIMED",
            potentialSize = "18"
        )
        val claimed = feedbackResponse(
            id = "feedback-1",
            status = "CLAIMED",
            potentialSize = "18"
        )
        val empty = """{"success":true,"creditFeedbackVOS":[]}"""

        assertEquals(
            18,
            SesameCreditRewardPolicy.parseFeedback(before).potentialTotal
        )
        assertTrue(
            SesameCreditRewardPolicy.isFeedbackCollectionConfirmed(
                claimed,
                setOf("feedback-1")
            )
        )
        assertTrue(
            SesameCreditRewardPolicy.isFeedbackCollectionConfirmed(
                empty,
                setOf("feedback-1")
            )
        )
        assertFalse(
            SesameCreditRewardPolicy.isFeedbackCollectionConfirmed(
                before,
                setOf("feedback-1")
            )
        )
        assertFalse(
            SesameCreditRewardPolicy.isFeedbackCollectionConfirmed(
                """{"success":true,"data":{}}""",
                setOf("feedback-1")
            )
        )
    }

    @Test
    fun `大表鸽任务仅由服务端终态或芝麻粒资产增加确认`() {
        val unfinished = sesameTaskResponse(
            finishFlag = false,
            actionText = "去完成"
        )
        val finished = sesameTaskResponse(
            finishFlag = true,
            actionText = "已完成"
        )

        assertTrue(
            SesameCreditRewardPolicy.isTaskCompletionConfirmed(
                finished,
                "hjwf_myzy_gyxj_erfang"
            )
        )
        assertTrue(
            SesameCreditRewardPolicy.isZhimaPigeonCompletionConfirmed(
                unfinished,
                "hjwf_myzy_gyxj_erfang",
                beforePotentialTotal = 10,
                afterPotentialTotal = 18
            )
        )
        assertFalse(
            SesameCreditRewardPolicy.isZhimaPigeonCompletionConfirmed(
                unfinished,
                "hjwf_myzy_gyxj_erfang",
                beforePotentialTotal = 10,
                afterPotentialTotal = 10
            )
        )
    }

    @Test
    fun `广告跳转下单充值和未知模板统一跳过`() {
        val unsafeTasks = listOf(
            SesameCreditTaskState(
                templateId = "ad-1",
                recordId = "",
                title = "浏览广告",
                finishFlag = false,
                actionText = "去完成",
                bizType = "AD_TASK",
                actionUrl = ""
            ),
            SesameCreditTaskState(
                templateId = "jump-1",
                recordId = "",
                title = "打开应用",
                finishFlag = false,
                actionText = "去完成",
                bizType = "NORMAL",
                actionUrl = "alipays://platformapi/startapp?appId=1"
            ),
            SesameCreditTaskState(
                templateId = "order-1",
                recordId = "",
                title = "下单得芝麻粒",
                finishFlag = false,
                actionText = "去完成",
                bizType = "NORMAL",
                actionUrl = ""
            ),
            SesameCreditTaskState(
                templateId = "recharge-1",
                recordId = "",
                title = "充值得奖励",
                finishFlag = false,
                actionText = "去完成",
                bizType = "NORMAL",
                actionUrl = ""
            ),
            SesameCreditTaskState(
                templateId = "",
                recordId = "",
                title = "未知任务",
                finishFlag = false,
                actionText = "去完成",
                bizType = "NORMAL",
                actionUrl = ""
            )
        )

        assertTrue(
            unsafeTasks.all {
                SesameCreditRewardPolicy.decideTask(it) !=
                    SesameCreditTaskDecision.EXECUTE_FREE
            }
        )
    }

    @Test
    fun `满级红包只解析资格查询状态`() {
        assertEquals(
            SesameAlchemyRedPacketState.AVAILABLE,
            SesameCreditRewardPolicy.parseRedPacketState(
                """{"success":true,"data":{"withdrawable":true}}"""
            )
        )
        assertEquals(
            SesameAlchemyRedPacketState.UNAVAILABLE,
            SesameCreditRewardPolicy.parseRedPacketState(
                """{"success":true,"data":{"withdrawable":false}}"""
            )
        )
        assertEquals(
            SesameAlchemyRedPacketState.RETRY,
            SesameCreditRewardPolicy.parseRedPacketState("")
        )
    }

    private fun timeLimitedResponse(state: Int): String = """
        {
          "success": true,
          "data": {
            "timeLimitedTaskVO": {
              "templateId": "meal-1",
              "longTitle": "午间奖励",
              "state": $state,
              "tomorrow": false,
              "rewardAmount": 10
            }
          }
        }
    """.trimIndent()

    private fun feedbackResponse(
        id: String,
        status: String,
        potentialSize: String
    ): String = """
        {
          "success": true,
          "creditFeedbackVOS": [{
            "creditFeedbackId": "$id",
            "cateId": "ZMZY#FEED_ZM_CHICKEN",
            "title": "芝麻大表鸽",
            "status": "$status",
            "potentialSize": "$potentialSize"
          }]
        }
    """.trimIndent()

    private fun sesameTaskResponse(
        finishFlag: Boolean,
        actionText: String
    ): String = """
        {
          "success": true,
          "data": {
            "toCompleteVOS": [{
              "templateId": "hjwf_myzy_gyxj_erfang",
              "recordId": "record-1",
              "title": "喂养芝麻大表鸽",
              "finishFlag": $finishFlag,
              "actionText": "$actionText",
              "bizType": "NORMAL"
            }],
            "dailyTaskListVO": {
              "waitJoinTaskVOS": [],
              "waitCompleteTaskVOS": []
            }
          }
        }
    """.trimIndent()
}
