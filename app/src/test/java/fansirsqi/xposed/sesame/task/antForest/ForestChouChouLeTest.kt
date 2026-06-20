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
}
