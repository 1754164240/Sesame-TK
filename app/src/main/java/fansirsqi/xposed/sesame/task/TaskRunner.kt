package fansirsqi.xposed.sesame.task

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.CustomSettings
import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.task.customTasks.ManualTask
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * 协程任务执行器 (优化版)
 *
 * 核心改进:
 * 1. **并发执行**: 支持任务并发运行，缩短总耗时。
 * 2. **生命周期**: 绑定到调用者的生命周期，防止泄漏。
 * 3. **逻辑简化**: 移除复杂的宽限期嵌套，使用标准的协程超时机制。
 */
class CoroutineTaskRunner(allModels: List<Model>) {

    companion object {
        private const val TAG = "CoroutineTaskRunner"
    }

    private val taskList: List<ModelTask> = allModels.filterIsInstance<ModelTask>()

    // 统计数据
    private val runCounter = TaskRunCounter()
    private val taskExecutionTimes = ConcurrentHashMap<String, Long>()

    /**
     * 启动任务执行流程
     * 注意：现在这是一个 suspend 函数，需要在一个协程作用域内调用
     */
    suspend fun run(
        isFirst: Boolean = true,
        rounds: Int = BaseModel.taskExecutionRounds.value
    ) = coroutineScope { // 使用 coroutineScope 创建子作用域
        val startTime = System.currentTimeMillis()

        // 【互斥检查】如果手动任务流正在运行，则跳过本次自动执行
        if (ManualTask.isManualRunning) {
            Log.record(TAG, "⏸ 检测到“手动庄园任务流”正在运行中，跳过本次自动任务调度")
            return@coroutineScope
        }

        if (isFirst) {
            ApplicationHook.updateDay()
            resetCounters()
        }

        try {
            val taskConcurrency = ConcurrencyPolicy.task(BaseModel.taskConcurrency.value)
            Log.record(TAG, "🚀 开始执行任务流程 (并发数: $taskConcurrency)")

            CustomSettings.loadForTaskRunner()
            val status = CustomSettings.getOnceDailyStatus(enableLog = true)
            val recoverySelection = TaskRecoveryRegistry.consumeRecoverySelection()
            val eligibleTasks = taskList.filter { task ->
                task.isEnable &&
                    !CustomSettings.isOnceDailyBlackListed(task.getName(), status) &&
                    (recoverySelection == null ||
                        TaskRecoveryRegistry.stableTaskId(task) in recoverySelection)
            }
            if (recoverySelection == null) {
                TaskRecoveryRegistry.beginRun(
                    eligibleTasks.map(TaskRecoveryRegistry::stableTaskId)
                )
            }

            // 执行多轮任务
            repeat(rounds) { roundIndex ->
                val round = roundIndex + 1
                executeRound(round, rounds, eligibleTasks, taskConcurrency)
            }

            if (CustomSettings.onlyOnceDaily.value) {
                // 确保时间状态是最新的
                TaskCommon.update()
                if (TaskCommon.IS_MODULE_SLEEP_TIME) {
                    Log.record(TAG, "💤 当前处于模块休眠时间，不设置 OnceDaily::Finished 标记")
                } else {
                    Status.setFlagToday("OnceDaily::Finished")
                }
            }

        } catch (e: CancellationException) {
            Log.record(TAG, "🚫 任务流程被取消")
            withContext(NonCancellable) {
                taskList.forEach { it.stopTaskAndJoin() }
            }
            throw e
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "任务流程异常", e)
        } finally {
            printExecutionSummary(startTime, System.currentTimeMillis())
            if (TaskRunnerPolicy.shouldScheduleNext(currentCoroutineContext().isActive)) {
                scheduleNext()
            }
        }
    }

    /**
     * 执行一轮任务 (并发模式)
     */
    private suspend fun executeRound(
        round: Int,
        totalRounds: Int,
        tasksToRun: List<ModelTask>,
        taskConcurrency: Int
    ) = coroutineScope {
        val roundStartTime = System.currentTimeMillis()

        val excludedCount = taskList.count { it.isEnable } - tasksToRun.size
        repeat(excludedCount.coerceAtLeast(0)) {
            runCounter.record(TaskRunOutcome.SKIPPED_FILTERED)
        }

        Log.record(TAG, "🔄 [第 $round/$totalRounds 轮] 开始，共 ${tasksToRun.size} 个任务")

        // 2. 并发执行
        // 使用 Semaphore 限制并发数量
        val semaphore = Semaphore(taskConcurrency)

        // 创建所有任务的 Deferred 对象
        val deferreds = tasksToRun.map { task ->
            async {
                semaphore.withPermit {
                    if (!TaskRunnerPolicy.shouldStart(ApplicationHook.offline, ManualTask.isManualRunning)) {
                        runCounter.record(TaskRunOutcome.SKIPPED_OFFLINE)
                        TaskRecoveryRegistry.record(
                            TaskRecoveryRegistry.stableTaskId(task),
                            RecoverableTaskOutcome.BLOCKED_VERIFICATION
                        )
                        Log.record(TAG, "⏸ 任务 ${task.getName()} 因离线或手动模式而跳过")
                        return@withPermit
                    }
                    executeSingleTask(task, round)
                }
            }
        }

        // 3. 等待本轮所有任务完成
        deferreds.awaitAll()

        val roundTime = System.currentTimeMillis() - roundStartTime
        Log.record(TAG, "✅ [第 $round/$totalRounds 轮] 结束，耗时: ${roundTime}ms")
    }

    /**
     * 执行单个任务
     */
    private suspend fun executeSingleTask(task: ModelTask, round: Int) {
        val taskName = task.getName() ?: "未知任务"
        val taskId = "$taskName-R$round"
        val startTime = System.currentTimeMillis()

        val timeout = task.runnerTimeoutMillis

        try {
            Log.record(TAG, "▶️ 启动: $taskId")
            task.addRunCents()

            val outcome = withTimeout(timeout) {
                val launchResult = task.launchTask(force = false, rounds = 1)
                val job = launchResult.job

                if (!launchResult.started) {
                    TaskRunOutcome.SKIPPED_RUNNING
                } else when (task.runnerExecutionPolicy) {
                    RunnerExecutionPolicy.START_ONLY -> {
                        if (job.isActive) {
                            TaskRunOutcome.STARTED_BACKGROUND
                        } else {
                            job.join()
                            if (job.isCancelled) TaskRunOutcome.FAILED else TaskRunOutcome.COMPLETED
                        }
                    }
                    RunnerExecutionPolicy.AWAIT_COMPLETION -> {
                        job.join()
                        if (job.isCancelled) TaskRunOutcome.FAILED else TaskRunOutcome.COMPLETED
                    }
                }
            }

            val time = System.currentTimeMillis() - startTime
            val taskStableId = TaskRecoveryRegistry.stableTaskId(task)
            if (ApplicationHook.offline) {
                TaskRecoveryRegistry.record(
                    taskStableId,
                    RecoverableTaskOutcome.BLOCKED_VERIFICATION
                )
            } else {
                val recoverableOutcome = when (outcome) {
                    TaskRunOutcome.COMPLETED,
                    TaskRunOutcome.STARTED_BACKGROUND -> RecoverableTaskOutcome.COMPLETED
                    else -> RecoverableTaskOutcome.FAILED
                }
                TaskRecoveryRegistry.record(taskStableId, recoverableOutcome)
            }
            runCounter.record(outcome)
            taskExecutionTimes[taskId] = time
            when (outcome) {
                TaskRunOutcome.COMPLETED ->
                    Log.record(TAG, "✅ 完成: $taskId (耗时: ${time}ms)")
                TaskRunOutcome.STARTED_BACKGROUND ->
                    Log.record(TAG, "✨ 后台启动: $taskId (启动耗时: ${time}ms)")
                TaskRunOutcome.FAILED ->
                    Log.error(TAG, "❌ 任务异常结束: $taskId (耗时: ${time}ms)")
                TaskRunOutcome.SKIPPED_RUNNING ->
                    Log.record(TAG, "⏭️ 跳过: $taskId 仍在运行")
                else -> Unit
            }

        } catch (e: TimeoutCancellationException) {
            val time = System.currentTimeMillis() - startTime
            runCounter.record(TaskRunOutcome.TIMED_OUT)
            Log.error(TAG, "⏰ 超时: $taskId (${time}ms > ${timeout}ms)")
            task.stopTaskAndJoin()
            TaskRecoveryRegistry.record(
                TaskRecoveryRegistry.stableTaskId(task),
                RecoverableTaskOutcome.FAILED
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val time = System.currentTimeMillis() - startTime
            runCounter.record(TaskRunOutcome.FAILED)
            Log.error(TAG, "❌ 失败: $taskId (${e.message})")
            TaskRecoveryRegistry.record(
                TaskRecoveryRegistry.stableTaskId(task),
                if (ApplicationHook.offline) {
                    RecoverableTaskOutcome.BLOCKED_VERIFICATION
                } else {
                    RecoverableTaskOutcome.FAILED
                }
            )
        }
    }

    private fun scheduleNext() {
        try {
            ApplicationHook.scheduleNextExecutionInternal(ApplicationHook.lastExecTime)
            Log.record(TAG, "📅 已调度下次执行")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "调度失败", e)
        }
    }

    private fun resetCounters() {
        runCounter.reset()
        taskExecutionTimes.clear()
    }

    @SuppressLint("DefaultLocale")
    private fun printExecutionSummary(startTime: Long, endTime: Long) {
        val totalTime = endTime - startTime
        val avgTime = if (taskExecutionTimes.isNotEmpty()) taskExecutionTimes.values.average() else 0.0
        val snapshot = runCounter.snapshot()

        Log.record(TAG, "📈 === 执行统计 (并发模式) ===")
        Log.record(TAG, "⏱️ 总耗时: ${totalTime}ms")
        Log.record(
            TAG,
            "✅ 完成: ${snapshot.completed} | ✨ 后台: ${snapshot.startedBackground} | " +
                "⏰ 超时: ${snapshot.timedOut} | ❌ 异常: ${snapshot.failed} | ⏭️ 跳过: ${snapshot.skipped}"
        )
        if (taskExecutionTimes.isNotEmpty()) {
            Log.record(TAG, "⚡ 平均耗时: %.0fms".format(avgTime))
        }

        val nextTime = ApplicationHook.nextExecutionTime
        if (nextTime > 0) {
            Log.record(TAG, "📅 下次: ${TimeUtil.getCommonDate(nextTime)}")
        }
        Log.record(TAG, "============================")
    }
}
