package fansirsqi.xposed.sesame.task.antSports

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object AntSportsStepSync {
    private val diagnosticLogged = AtomicBoolean(false)
    private val client = object : FamilyWalkStepSyncClient {
        override fun queryDonationSteps(): String = AntSportsRpcCall.queryDonationSteps()

        override fun walkDonateSignInfo(step: Int): String {
            return AntSportsRpcCall.walkDonateSignInfo(step)
        }

        override fun donateWalkHome(step: Int): String = AntSportsRpcCall.donateWalkHome(step)
    }

    fun shouldOverrideDailyStep(originStep: Int, targetStep: Int): Boolean {
        return targetStep > 0 && originStep < targetStep
    }

    fun syncStep(step: Int, logTag: String): Boolean {
        return try {
            val result = FamilyWalkStepSync.sync(step, client)
            if (result.outcome != FamilyWalkStepSyncOutcome.VERIFIED) {
                if (diagnosticLogged.compareAndSet(false, true)) {
                    Log.error(
                        logTag,
                        "家庭捐步未能确认目标步数: 目标=$step, 查询=${result.verifiedStep}, " +
                            "状态=${result.outcome}"
                    )
                }
                return false
            }

            diagnosticLogged.set(false)
            Log.other("同步步数🏃🏻‍♂️[${result.verifiedStep} 步]")
            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)
            true
        } catch (t: Throwable) {
            Log.printStackTrace(logTag, t)
            false
        }
    }
}
