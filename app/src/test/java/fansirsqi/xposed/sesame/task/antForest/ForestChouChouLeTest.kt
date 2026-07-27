package fansirsqi.xposed.sesame.task.antForest

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
}
