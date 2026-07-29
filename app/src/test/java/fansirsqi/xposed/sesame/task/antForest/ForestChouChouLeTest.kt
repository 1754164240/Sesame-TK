package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForestChouChouLeTest {

    @Test
    fun `抽抽乐任务失败达到上限后本轮跳过`() {
        assertFalse(ForestChouChouLe.shouldSkipFailedTask(0))
        assertFalse(ForestChouChouLe.shouldSkipFailedTask(2))
        assertTrue(ForestChouChouLe.shouldSkipFailedTask(3))
        assertTrue(ForestChouChouLe.shouldSkipFailedTask(4))
    }

    @Test
    fun `抽抽乐执行任务前会检查失败次数`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestChouChouLe.kt").readText()

        assertTrue(sourceText.contains("MAX_TASK_FAIL_COUNT"))
        assertTrue(sourceText.contains("shouldSkipFailedTask"))
        assertTrue(sourceText.contains("跳过失败过多任务"))
    }

    @Test
    fun `未知游戏任务必须等待抓包而不是调用通用完成接口`() {
        assertTrue(
            ForestDrawTaskPolicy.actionFor(
                taskType = "FOREST_NORMAL_DRAW_GAME_UNKNOWN",
                taskName = "玩游戏完成一局"
            ) == ForestDrawTaskAction.WAIT_FOR_CAPTURE
        )
        assertTrue(
            ForestDrawTaskPolicy.actionFor(
                taskType = "FOREST_ACTIVITY_DRAW_UNKNOWN",
                taskName = "开宝箱"
            ) == ForestDrawTaskAction.WAIT_FOR_CAPTURE
        )
    }

    @Test
    fun `已知非游戏任务仍按协议分发`() {
        assertTrue(
            ForestDrawTaskPolicy.actionFor(
                taskType = "NORMAL_DRAW_EXCHANGE_VITALITY",
                taskName = "兑换活力值"
            ) == ForestDrawTaskAction.EXCHANGE_VITALITY
        )
        assertTrue(
            ForestDrawTaskPolicy.actionFor(
                taskType = "FOREST_NORMAL_DRAW_XLIGHT_BROWSE",
                taskName = "浏览广告"
            ) == ForestDrawTaskAction.FINISH_XLIGHT
        )
        assertTrue(
            ForestDrawTaskPolicy.actionFor(
                taskType = "FOREST_NORMAL_DRAW_BROWSE",
                taskName = "浏览页面"
            ) == ForestDrawTaskAction.FINISH_STANDARD
        )
    }

    @Test
    fun `明确不可重试的抽抽乐错误立即停止`() {
        assertFalse(
            ForestDrawTaskPolicy.isRetryableFailure(
                resultCode = "ILLEGAL_ARGUMENT",
                resultDescription = "参数错误"
            )
        )
        assertFalse(
            ForestDrawTaskPolicy.isRetryableFailure(
                resultCode = "UNKNOWN",
                resultDescription = "不支持rpc完成的任务"
            )
        )
        assertTrue(
            ForestDrawTaskPolicy.isRetryableFailure(
                resultCode = "SYSTEM_BUSY",
                resultDescription = "系统繁忙"
            )
        )
    }

    @Test
    fun `动作成功但任务列表未刷新时不确认状态变更`() {
        assertFalse(
            ForestDrawTaskStatePolicy.isTransitionConfirmed(
                previousStatus = "TODO",
                currentStatus = "TODO"
            )
        )
        assertTrue(
            ForestDrawTaskStatePolicy.isTransitionConfirmed(
                previousStatus = "TODO",
                currentStatus = "FINISHED"
            )
        )
        assertFalse(
            ForestDrawTaskStatePolicy.isTransitionConfirmed(
                previousStatus = "FINISHED",
                currentStatus = "FINISHED"
            )
        )
        assertTrue(
            ForestDrawTaskStatePolicy.isTransitionConfirmed(
                previousStatus = "FINISHED",
                currentStatus = "RECEIVED"
            )
        )
    }

    @Test
    fun `服务端任务列表明确全部已领取时才能确认场景完成`() {
        val response = JSONObject(
            """
            {
              "success": true,
              "taskInfoList": [{
                "taskBaseInfo": {
                  "sceneCode": "ANTFOREST_NORMAL_DRAW_TASK",
                  "taskType": "FOREST_NORMAL_DRAW_SIGN",
                  "taskStatus": "RECEIVED",
                  "bizInfo": "{\"title\":\"签到\"}"
                }
              }]
            }
            """.trimIndent()
        )

        assertEquals(
            ForestDrawCompletionDecision.CONFIRMED,
            ForestDrawTaskStatePolicy.completionDecision(response)
        )
    }

    @Test
    fun `空列表和未知累计奖励结构均保留重试`() {
        assertEquals(
            ForestDrawCompletionDecision.RETRY,
            ForestDrawTaskStatePolicy.completionDecision(
                JSONObject("""{"success":true,"taskInfoList":[]}""")
            )
        )
        assertEquals(
            ForestDrawCompletionDecision.RETRY,
            ForestDrawTaskStatePolicy.completionDecision(
                JSONObject(
                    """
                    {
                      "success": true,
                      "cumulativeReward": {
                        "current": 3,
                        "target": 3
                      }
                    }
                    """.trimIndent()
                )
            )
        )
    }
}
