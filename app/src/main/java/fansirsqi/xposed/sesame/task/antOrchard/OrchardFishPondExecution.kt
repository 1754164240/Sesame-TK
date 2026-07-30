package fansirsqi.xposed.sesame.task.antOrchard

import kotlinx.coroutines.CancellationException

object OrchardFishPondExecution {

    suspend fun run(
        orchardBlock: suspend () -> Unit,
        fishPondBlock: suspend () -> Unit,
        goldenBeanBlock: suspend () -> Unit = {}
    ) {
        runIsolated(orchardBlock)
        runIsolated(fishPondBlock)
        runIsolated(goldenBeanBlock)
    }

    private suspend fun runIsolated(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 各业务阶段自行记录详细异常，这里只负责隔离后续阶段。
        }
    }
}
