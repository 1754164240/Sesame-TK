package fansirsqi.xposed.sesame.task.antOrchard

import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.CancellationException

object GoldenBeanTreasureRunner {

    fun run(enabled: Boolean) {
        if (!enabled) {
            return
        }
        try {
            val result = GoldenBeanWorkflow(GoldenBeanRpcGateway()).run()
            if (result.claimedCount > 0) {
                Log.farm("金豆夺宝🫘[领取奖励]#${result.claimedCount}项")
            }
            if (result.retryNeeded) {
                Log.record(TAG, "金豆夺宝状态暂未完成闭环，等待后续重试")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "金豆夺宝执行异常", e)
        }
    }

    private const val TAG = "GoldenBeanTreasure"
}
