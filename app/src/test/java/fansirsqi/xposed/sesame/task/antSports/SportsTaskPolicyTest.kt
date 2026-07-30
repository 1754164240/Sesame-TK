package fansirsqi.xposed.sesame.task.antSports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsTaskPolicyTest {

    @Test
    fun `只有签到组的明确签到任务允许调用完成接口`() {
        val signTask = task(
            taskId = "SPORTS_DAILY_SIGN",
            bizType = "DAILY_SIGN",
            taskName = "每日签到",
            status = "TODO"
        )
        val browseTask = task(
            taskId = "SPORTS_DAILY_BROWSE",
            bizType = "BROWSE",
            taskName = "浏览文体页面",
            status = "TODO"
        )

        assertEquals(
            SportsTaskAction.COMPLETE_SIGN_IN,
            SportsTaskPolicy.decide(
                "SPORTS_DAILY_SIGN_GROUP",
                signTask
            )
        )
        assertEquals(
            SportsTaskAction.COMPLETE_TASK,
            SportsTaskPolicy.decide(
                "SPORTS_DAILY_GROUP",
                signTask
            )
        )
        assertEquals(
            SportsTaskAction.COMPLETE_TASK,
            SportsTaskPolicy.decide(
                "SPORTS_DAILY_GROUP",
                browseTask
            )
        )
    }

    @Test
    fun `广告游戏资金和相似词任务均保护性跳过`() {
        val riskyTasks = listOf(
            task("SPORTS_LIGHT_AD_TASK", "AD", "看广告", "TODO"),
            task("SPORTS_GAME_TASK", "GAME", "完成游戏", "TODO"),
            task("SPORTS_CASH_TASK", "CASH", "现金任务", "TODO"),
            task("SPORTS_EXCHANGE_TASK", "EXCHANGE", "兑换奖励", "TODO"),
            task("SPORTS_DONATION_TASK", "DONATION", "公益捐赠", "TODO")
        )

        riskyTasks.forEach { state ->
            assertEquals(
                SportsTaskAction.SKIP_UNSAFE,
                SportsTaskPolicy.decide(
                    "SPORTS_DAILY_SIGN_GROUP",
                    state
                )
            )
            assertEquals(
                SportsTaskAction.SKIP_UNSAFE,
                SportsTaskPolicy.decide(
                    "SPORTS_DAILY_GROUP",
                    state
                )
            )
        }
        val similarWord = task(
            "SPORTS_SPREAD_TASK",
            "SPREAD",
            "浏览文体页面",
            "TODO"
        )
        assertEquals(
            SportsTaskAction.COMPLETE_TASK,
            SportsTaskPolicy.decide("SPORTS_DAILY_GROUP", similarWord)
        )
    }

    @Test
    fun `任务列表非对象或缺少关键字段时不可识别`() {
        assertFalse(
            SportsTaskPolicy.parseSnapshot(
                """{"success":true,"group":{"userTaskList":[1]}}"""
            ).recognized
        )
        assertFalse(
            SportsTaskPolicy.parseSnapshot(
                """
                    {
                      "success":true,
                      "group":{
                        "userTaskList":[{
                          "status":"TODO",
                          "taskInfo":{"taskName":"每日签到"}
                        }]
                      }
                    }
                """.trimIndent()
            ).recognized
        )
    }

    @Test
    fun `明确完成的安全任务允许领奖但缺少用户任务身份时跳过`() {
        val completed = task(
            taskId = "SPORTS_DAILY_STEP",
            bizType = "STEP",
            taskName = "每日步数",
            status = "COMPLETED",
            userTaskId = "user-task-1"
        )
        val missingIdentity = completed.copy(userTaskId = "")

        assertEquals(
            SportsTaskAction.CLAIM_REWARD,
            SportsTaskPolicy.decide("SPORTS_DAILY_GROUP", completed)
        )
        assertEquals(
            SportsTaskAction.SKIP_UNSAFE,
            SportsTaskPolicy.decide(
                "SPORTS_DAILY_GROUP",
                missingIdentity
            )
        )
    }

    @Test
    fun `只有服务端状态明确推进才确认完成或领奖`() {
        val beforeTodo = task(
            "SPORTS_DAILY_SIGN",
            "DAILY_SIGN",
            "每日签到",
            "TODO"
        )
        val afterCompleted = beforeTodo.copy(status = "COMPLETED")
        val beforeReward = task(
            "SPORTS_DAILY_STEP",
            "STEP",
            "每日步数",
            "COMPLETED",
            "user-task-1"
        )
        val afterReceived = beforeReward.copy(status = "RECEIVED")

        assertEquals(
            SportsTaskOutcome.CONFIRMED,
            SportsTaskPolicy.verifyCompletion(
                beforeTodo,
                snapshot(afterCompleted)
            )
        )
        assertEquals(
            SportsTaskOutcome.RETRY,
            SportsTaskPolicy.verifyCompletion(
                beforeTodo,
                snapshot(beforeTodo)
            )
        )
        assertEquals(
            SportsTaskOutcome.CONFIRMED,
            SportsTaskPolicy.verifyReward(
                beforeReward,
                snapshot(afterReceived)
            )
        )
        assertEquals(
            SportsTaskOutcome.RETRY,
            SportsTaskPolicy.verifyReward(
                beforeReward,
                snapshot(beforeReward)
            )
        )
        assertTrue(snapshot().recognized)
    }

    private fun task(
        taskId: String,
        bizType: String,
        taskName: String,
        status: String,
        userTaskId: String = ""
    ): SportsTaskState {
        return SportsTaskState(
            taskId = taskId,
            userTaskId = userTaskId,
            bizType = bizType,
            taskName = taskName,
            status = status
        )
    }

    private fun snapshot(
        vararg tasks: SportsTaskState
    ): SportsTaskSnapshot {
        return SportsTaskSnapshot(
            recognized = true,
            tasks = tasks.toList()
        )
    }
}
