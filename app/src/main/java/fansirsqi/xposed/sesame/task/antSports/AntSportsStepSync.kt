package fansirsqi.xposed.sesame.task.antSports

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object AntSportsStepSync {
    private const val RPC_MANAGER_CLASS =
        "com.alibaba.health.pedometer.intergation.rpc.RpcManager"

    @Volatile
    private var cachedClass: Class<*>? = null

    @Volatile
    private var cachedMethods: ResolvedStepSyncMethods? = null

    private val diagnosticLogged = AtomicBoolean(false)

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

            val managerClass = loader.loadClass(RPC_MANAGER_CLASS)
            val methods = resolveMethods(managerClass)
            if (methods == null) {
                if (diagnosticLogged.compareAndSet(false, true)) {
                    Log.error(
                        logTag,
                        "无法唯一定位步数同步方法，当前方法签名:\n" +
                            StepSyncMethodResolver.describeMethods(managerClass)
                    )
                }
                return false
            }

            val rpcManager = methods.factory.invoke(null)
            val success = methods.syncMethod.invoke(
                rpcManager,
                step,
                java.lang.Boolean.FALSE,
                "system"
            ) as? Boolean ?: false

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

    @Synchronized
    private fun resolveMethods(managerClass: Class<*>): ResolvedStepSyncMethods? {
        if (cachedClass === managerClass) {
            return cachedMethods
        }

        cachedClass = managerClass
        cachedMethods = StepSyncMethodResolver.resolve(managerClass)
        diagnosticLogged.set(false)
        return cachedMethods
    }
}
