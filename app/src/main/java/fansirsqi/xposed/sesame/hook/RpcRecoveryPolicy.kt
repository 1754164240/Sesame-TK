package fansirsqi.xposed.sesame.hook

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    private val generation = AtomicLong(0L)

    val blockReason: RpcBlockReason
        get() = reason.get()

    val failureCount: Int
        get() = consecutiveFailures.get()

    val verificationGeneration: Long
        get() = generation.get()

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

    @Synchronized
    fun onVerificationRequired(): RecoveryDecision {
        if (reason.getAndSet(RpcBlockReason.VERIFICATION) != RpcBlockReason.VERIFICATION) {
            generation.incrementAndGet()
        }
        return if (verificationNotified.compareAndSet(false, true)) {
            RecoveryDecision.WAIT_FOR_MANUAL_VERIFICATION
        } else {
            RecoveryDecision.NONE
        }
    }

    @Synchronized
    fun restartVerificationProbeCycle(): Long {
        reason.set(RpcBlockReason.VERIFICATION)
        verificationNotified.set(true)
        return generation.incrementAndGet()
    }

    @Synchronized
    fun restoreVerification(restoredGeneration: Long) {
        generation.set(restoredGeneration.coerceAtLeast(1L))
        reason.set(RpcBlockReason.VERIFICATION)
        verificationNotified.set(true)
    }

    fun reset() {
        consecutiveFailures.set(0)
        reason.set(RpcBlockReason.NONE)
        recoveryScheduled.set(false)
        verificationNotified.set(false)
    }

    fun onBlockedRequest(): RecoveryDecision = RecoveryDecision.NONE

    fun onRequestCompletedWithoutResponse() {
        // 无返回值请求无法证明 Bridge 已恢复，保留当前熔断状态。
    }

    fun onFrequencyLimited(): RecoveryDecision = RecoveryDecision.NONE

    fun onUnknownFailure(): RecoveryDecision = RecoveryDecision.NONE

    @JvmOverloads
    fun onSuccess(
        purpose: RpcRequestPurpose = RpcRequestPurpose.BUSINESS,
        expectedGeneration: Long? = null
    ): Boolean {
        if (reason.get() == RpcBlockReason.VERIFICATION) {
            if (
                purpose != RpcRequestPurpose.VERIFICATION_PROBE ||
                expectedGeneration == null ||
                expectedGeneration != generation.get()
            ) {
                return false
            }
        }
        consecutiveFailures.set(0)
        reason.set(RpcBlockReason.NONE)
        recoveryScheduled.set(false)
        verificationNotified.set(false)
        return true
    }

    private fun scheduleNetworkRecoveryOnce(): RecoveryDecision {
        return if (recoveryScheduled.compareAndSet(false, true)) {
            RecoveryDecision.SCHEDULE_REOPEN
        } else {
            RecoveryDecision.NONE
        }
    }
}
