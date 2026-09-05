package fansirsqi.xposed.sesame.hook

import java.util.function.BooleanSupplier

/** 在限流等待前后检查暂停状态。 */
object RpcDispatchGate {
    @JvmStatic
    fun awaitPermission(isBlocked: BooleanSupplier, waitForInterval: Runnable): Boolean {
        if (isBlocked.asBoolean) return false
        waitForInterval.run()
        return !isBlocked.asBoolean
    }
}
