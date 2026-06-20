package fansirsqi.xposed.sesame.task.antSports

import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.util.Log

object AntSportsStepSync {
    fun shouldOverrideDailyStep(originStep: Int, targetStep: Int): Boolean {
        return targetStep > 0 && originStep < targetStep
    }

    fun syncStep(step: Int, logTag: String): Boolean {
        return try {
            val loader = ApplicationHook.classLoader
            if (loader == null) {
                Log.error(logTag, "ClassLoader is null, 跳过同步步数")
                return false
            }

            val rpcManager = XposedHelpers.callStaticMethod(
                loader.loadClass("com.alibaba.health.pedometer.intergation.rpc.RpcManager"),
                "a"
            )

            val success = XposedHelpers.callMethod(
                rpcManager,
                "a",
                step,
                java.lang.Boolean.FALSE,
                "system"
            ) as Boolean

            if (success) {
                Log.other("同步步数🏃🏻‍♂️[$step 步]")
                Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)
            } else {
                Log.error(logTag, "同步运动步数失败:$step")
            }
            success
        } catch (t: Throwable) {
            Log.printStackTrace(logTag, t)
            false
        }
    }
}
