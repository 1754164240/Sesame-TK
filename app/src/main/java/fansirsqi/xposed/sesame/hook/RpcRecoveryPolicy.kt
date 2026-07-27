package fansirsqi.xposed.sesame.hook

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class RpcBlockReason {
    NONE,
    NETWORK,
    VERIFICATION
}

enum class RecoveryDecision {
    NONE,
    SCHEDULE_REOPEN,
    WAIT_FOR_MANUAL_VERIFICATION
}

/**
 * 维护 RPC 阻断状态，并确保调用方提供的恢复动作每个离线周期只执行一次。
 */
class RpcRecoveryPolicy {
    private val consecutiveFailures = AtomicInteger(0)
    private val reason = AtomicReference(RpcBlockReason.NONE)
    private val recoveryScheduled = AtomicBoolean(false)
    private val verificationNotified = AtomicBoolean(false)

    val blockReason: RpcBlockReason
        get() = reason.get()

    val failureCount: Int
        get() = consecutiveFailures.get()

    fun onNetworkFailure(maxFailures: Int): RecoveryDecision {
        if (reason.get() != RpcBlockReason.NONE) {
            return RecoveryDecision.NONE
        }

        val threshold = maxFailures.coerceAtLeast(1)
        if (consecutiveFailures.incrementAndGet() < threshold) {
            return RecoveryDecision.NONE
        }

        reason.compareAndSet(RpcBlockReason.NONE, RpcBlockReason.NETWORK)
        return scheduleNetworkRecoveryOnce()
    }

    fun onExternalOffline(): RecoveryDecision {
        if (reason.get() == RpcBlockReason.VERIFICATION) {
            return RecoveryDecision.NONE
        }
        reason.compareAndSet(RpcBlockReason.NONE, RpcBlockReason.NETWORK)
        return scheduleNetworkRecoveryOnce()
    }

    fun handleExternalOffline(recover: () -> Unit): RecoveryDecision {
        val decision = onExternalOffline()
        if (decision == RecoveryDecision.SCHEDULE_REOPEN) {
            recover()
        }
        return decision
    }

    fun onVerificationRequired(): RecoveryDecision {
        reason.set(RpcBlockReason.VERIFICATION)
        return if (verificationNotified.compareAndSet(false, true)) {
            RecoveryDecision.WAIT_FOR_MANUAL_VERIFICATION
        } else {
            RecoveryDecision.NONE
        }
    }

    fun onBlockedRequest(): RecoveryDecision = RecoveryDecision.NONE

    fun onRequestCompletedWithoutResponse() {
        // 无返回值请求无法证明 Bridge 已恢复，保留当前熔断状态。
    }

    fun onSuccess() {
        consecutiveFailures.set(0)
        reason.set(RpcBlockReason.NONE)
        recoveryScheduled.set(false)
        verificationNotified.set(false)
    }

    private fun scheduleNetworkRecoveryOnce(): RecoveryDecision {
        return if (recoveryScheduled.compareAndSet(false, true)) {
            RecoveryDecision.SCHEDULE_REOPEN
        } else {
            RecoveryDecision.NONE
        }
    }
}
