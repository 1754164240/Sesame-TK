package fansirsqi.xposed.sesame.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskRunnerPolicyTest {

    @Test
    fun `后台启动不会计入完成`() {
        val counter = TaskRunCounter()

        counter.record(TaskRunOutcome.COMPLETED)
        counter.record(TaskRunOutcome.STARTED_BACKGROUND)
        counter.record(TaskRunOutcome.TIMED_OUT)
        counter.record(TaskRunOutcome.SKIPPED_OFFLINE)
        counter.record(TaskRunOutcome.FAILED)

        assertEquals(
            TaskRunSnapshot(
                completed = 1,
                startedBackground = 1,
                timedOut = 1,
                skipped = 1,
                failed = 1
            ),
            counter.snapshot()
        )
    }

    @Test
    fun `离线状态不允许启动新任务`() {
        assertTrue(TaskRunnerPolicy.shouldStart(isOffline = false, isManualRunning = false))
        assertFalse(TaskRunnerPolicy.shouldStart(isOffline = true, isManualRunning = false))
        assertFalse(TaskRunnerPolicy.shouldStart(isOffline = false, isManualRunning = true))
    }

    @Test
    fun `调度器不再依赖任务显示名称白名单`() {
        val source = File("src/main/java/fansirsqi/xposed/sesame/task/TaskRunner.kt").readText()

        assertFalse(source.contains("TIMEOUT_WHITELIST"))
        assertTrue(source.contains("runnerExecutionPolicy"))
        assertTrue(source.contains("ApplicationHook.offline"))
    }
}
