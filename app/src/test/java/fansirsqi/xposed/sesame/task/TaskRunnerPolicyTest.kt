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
    fun `运行中的后台任务不会被下一轮重复排队`() {
        val gate = TaskExecutionGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `任务流取消后不再调度下一次执行`() {
        assertTrue(TaskRunnerPolicy.shouldScheduleNext(isActive = true))
        assertFalse(TaskRunnerPolicy.shouldScheduleNext(isActive = false))
    }

    @Test
    fun `并发配置使用安全边界`() {
        assertEquals(1, ConcurrencyPolicy.task(0))
        assertEquals(3, ConcurrencyPolicy.task(3))
        assertEquals(8, ConcurrencyPolicy.task(99))
        assertEquals(1, ConcurrencyPolicy.forest(0))
        assertEquals(60, ConcurrencyPolicy.forest(60))
        assertEquals(100, ConcurrencyPolicy.forest(999))
    }

    @Test
    fun `调度器不再依赖任务显示名称白名单`() {
        val source = File("src/main/java/fansirsqi/xposed/sesame/task/TaskRunner.kt").readText()

        assertFalse(source.contains("TIMEOUT_WHITELIST"))
        assertTrue(source.contains("runnerExecutionPolicy"))
        assertTrue(source.contains("ApplicationHook.offline"))
    }

    @Test
    fun `任务销毁使用可等待的停止流程`() {
        val modelTaskSource =
            File("src/main/java/fansirsqi/xposed/sesame/task/ModelTask.kt").readText()
        val applicationHookSource =
            File("src/main/java/fansirsqi/xposed/sesame/hook/ApplicationHook.kt").readText()

        assertTrue(modelTaskSource.contains("suspend fun stopTaskAndJoin()"))
        assertTrue(modelTaskSource.contains("suspend fun stopAllTaskAndJoin()"))
        assertTrue(applicationHookSource.contains("stopAllTaskAndJoin()"))
    }
}
