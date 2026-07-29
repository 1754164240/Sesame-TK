package fansirsqi.xposed.sesame.task.antMember

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SesameCreditRewardWorkflowTest {
    @Test
    fun `次日奖励ACK后仍可领取时保留重试`() = runBlocking {
        val award = SesameAlchemyNextDayAward("award-1", true, 12)
        val workflow = workflow(
            queryNextDayAward = {
                nextDayResponse("award-1", available = true)
            }
        )

        assertEquals(
            SesameCreditRewardOutcome.RETRY,
            workflow.claimNextDayAward(award)
        )
    }

    @Test
    fun `时段奖励动作后状态关闭才确认`() = runBlocking {
        val task = SesameAlchemyTimeLimitedTask(
            templateId = "meal-1",
            title = "午间奖励",
            state = 1,
            tomorrow = false,
            rewardAmount = 10
        )
        val workflow = workflow(
            queryTimeLimitedTask = { timeLimitedResponse(2) }
        )

        assertEquals(
            SesameCreditRewardOutcome.CONFIRMED,
            workflow.claimTimeLimitedReward(task)
        )
    }

    @Test
    fun `芝麻粒一键收取后目标仍存在时保留重试`() = runBlocking {
        val item = SesameCreditFeedback(
            id = "feedback-1",
            categoryId = "",
            title = "任务奖励",
            status = "UNCLAIMED",
            potentialSize = 10
        )
        val workflow = workflow(
            queryFeedback = {
                feedbackResponse("feedback-1", "UNCLAIMED", 10)
            }
        )

        assertEquals(
            SesameCreditRewardOutcome.RETRY,
            workflow.collectFeedback(listOf(item))
        )
    }

    @Test
    fun `危险炼金任务不调用动作`() = runBlocking {
        var actionCalled = false
        val workflow = workflow(
            completeTask = {
                actionCalled = true
                """{"success":true}"""
            }
        )
        val task = SesameCreditTaskState(
            templateId = "ad-1",
            recordId = "",
            title = "浏览广告",
            finishFlag = false,
            actionText = "去完成",
            bizType = "AD_TASK",
            actionUrl = ""
        )

        assertEquals(
            SesameCreditRewardOutcome.SKIPPED_UNSAFE,
            workflow.completeTask(task)
        )
        assertFalse(actionCalled)
    }

    @Test
    fun `大表鸽动作后芝麻粒资产增加时确认`() = runBlocking {
        var feedbackQueries = 0
        val workflow = workflow(
            queryTasks = { taskResponse(finished = false) },
            queryFeedback = {
                feedbackQueries++
                if (feedbackQueries == 1) {
                    feedbackResponse("old", "UNCLAIMED", 10)
                } else {
                    feedbackResponse("new", "UNCLAIMED", 18)
                }
            }
        )
        val task = SesameCreditTaskState(
            templateId = SesameCreditRewardPolicy.ZHIMA_PIGEON_TEMPLATE_ID,
            recordId = "record-1",
            title = "喂养芝麻大表鸽",
            finishFlag = false,
            actionText = "去完成",
            bizType = "NORMAL",
            actionUrl = ""
        )

        assertEquals(
            SesameCreditRewardOutcome.CONFIRMED,
            workflow.completeTask(task)
        )
    }

    private fun workflow(
        queryNextDayAward: suspend () -> String = {
            nextDayResponse("award-1", available = false)
        },
        claimNextDayAward: suspend (String) -> String = {
            """{"success":true}"""
        },
        queryTimeLimitedTask: suspend () -> String = {
            timeLimitedResponse(2)
        },
        claimTimeLimitedReward: suspend (String) -> String = {
            """{"success":true}"""
        },
        queryFeedback: suspend () -> String = {
            """{"success":true,"creditFeedbackVOS":[]}"""
        },
        collectFeedback: suspend (Set<String>) -> String = {
            """{"success":true}"""
        },
        queryTasks: suspend () -> String = {
            taskResponse(finished = true)
        },
        completeTask: suspend (SesameCreditTaskState) -> String = {
            """{"success":true}"""
        }
    ): SesameCreditRewardWorkflow {
        return SesameCreditRewardWorkflow(
            queryNextDayAward = queryNextDayAward,
            claimNextDayAward = claimNextDayAward,
            queryTimeLimitedTask = queryTimeLimitedTask,
            claimTimeLimitedReward = claimTimeLimitedReward,
            queryFeedback = queryFeedback,
            collectFeedback = collectFeedback,
            queryTasks = queryTasks,
            completeTask = completeTask
        )
    }

    private fun nextDayResponse(awardId: String, available: Boolean) = """
        {
          "success": true,
          "data": {
            "entryList": [{
              "entryCode": "ALCHEMY_STAGE_REWARD",
              "nextDayAwardDTO": {
                "awardAvailable": $available,
                "awardId": "$awardId",
                "pointValue": 12
              }
            }]
          }
        }
    """.trimIndent()

    private fun timeLimitedResponse(state: Int) = """
        {
          "success": true,
          "data": {
            "timeLimitedTaskVO": {
              "templateId": "meal-1",
              "state": $state,
              "tomorrow": false
            }
          }
        }
    """.trimIndent()

    private fun feedbackResponse(
        id: String,
        status: String,
        potentialSize: Int
    ) = """
        {
          "success": true,
          "creditFeedbackVOS": [{
            "creditFeedbackId": "$id",
            "status": "$status",
            "potentialSize": "$potentialSize"
          }]
        }
    """.trimIndent()

    private fun taskResponse(finished: Boolean) = """
        {
          "success": true,
          "data": {
            "toCompleteVOS": [{
              "templateId": "${SesameCreditRewardPolicy.ZHIMA_PIGEON_TEMPLATE_ID}",
              "recordId": "record-1",
              "finishFlag": $finished,
              "actionText": "${if (finished) "已完成" else "去完成"}",
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
