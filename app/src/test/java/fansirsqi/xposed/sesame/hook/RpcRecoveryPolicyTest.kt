package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RpcRecoveryPolicyTest {

    @Test
    fun `网络失败达到阈值后只调度一次恢复`() {
        val policy = RpcRecoveryPolicy()

        assertEquals(RecoveryDecision.NONE, policy.onNetworkFailure(2))
        assertEquals(RecoveryDecision.SCHEDULE_REOPEN, policy.onNetworkFailure(2))
        assertEquals(RecoveryDecision.NONE, policy.onBlockedRequest())
        assertEquals(RpcBlockReason.NETWORK, policy.blockReason)
    }

    @Test
    fun `并发离线请求也只会产生一次恢复决定`() {
        val policy = RpcRecoveryPolicy()
        val executor = Executors.newFixedThreadPool(8)

        val decisions = try {
            executor.invokeAll(
                (1..32).map {
                    Callable { policy.onExternalOffline() }
                }
            ).map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, decisions.count { it == RecoveryDecision.SCHEDULE_REOPEN })
    }

    @Test
    fun `并发外部离线只执行一次恢复动作`() {
        val policy = RpcRecoveryPolicy()
        val executor = Executors.newFixedThreadPool(8)
        val recoveryCount = AtomicInteger()

        try {
            executor.invokeAll(
                (1..32).map {
                    Callable {
                        policy.handleExternalOffline {
                            recoveryCount.incrementAndGet()
                        }
                    }
                }
            ).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, recoveryCount.get())
    }

    @Test
    fun `安全验证只通知一次且不调度重开`() {
        val policy = RpcRecoveryPolicy()

        assertEquals(RecoveryDecision.WAIT_FOR_MANUAL_VERIFICATION, policy.onVerificationRequired())
        assertEquals(RecoveryDecision.NONE, policy.onVerificationRequired())
        assertEquals(RecoveryDecision.NONE, policy.onBlockedRequest())
        assertEquals(RpcBlockReason.VERIFICATION, policy.blockReason)
    }

    @Test
    fun `普通请求成功不能解除安全验证`() {
        val policy = RpcRecoveryPolicy()
        policy.onVerificationRequired()

        assertEquals(false, policy.onSuccess(RpcRequestPurpose.BUSINESS))
        assertEquals(RpcBlockReason.VERIFICATION, policy.blockReason)
    }

    @Test
    fun `只有当前代际的探测成功才能解除安全验证`() {
        val policy = RpcRecoveryPolicy()
        policy.onVerificationRequired()
        val oldGeneration = policy.verificationGeneration
        policy.restartVerificationProbeCycle()
        val currentGeneration = policy.verificationGeneration

        assertEquals(
            false,
            policy.onSuccess(RpcRequestPurpose.VERIFICATION_PROBE, oldGeneration)
        )
        assertEquals(RpcBlockReason.VERIFICATION, policy.blockReason)
        assertEquals(
            true,
            policy.onSuccess(RpcRequestPurpose.VERIFICATION_PROBE, currentGeneration)
        )
        assertEquals(RpcBlockReason.NONE, policy.blockReason)
    }

    @Test
    fun `账号切换会清理内存中的验证阻断`() {
        val policy = RpcRecoveryPolicy()
        policy.restoreVerification(6L)

        policy.reset()

        assertEquals(RpcBlockReason.NONE, policy.blockReason)
        assertEquals(0, policy.failureCount)
    }

    @Test
    fun `成功响应会清空阻断状态和失败计数`() {
        val policy = RpcRecoveryPolicy()
        policy.onNetworkFailure(1)

        policy.onSuccess()

        assertEquals(RpcBlockReason.NONE, policy.blockReason)
        assertEquals(0, policy.failureCount)
        assertEquals(RecoveryDecision.NONE, policy.onNetworkFailure(2))
    }

    @Test
    fun `无返回值请求完成不能重置失败计数`() {
        val policy = RpcRecoveryPolicy()
        policy.onNetworkFailure(3)

        policy.onRequestCompletedWithoutResponse()

        assertEquals(1, policy.failureCount)
    }

    @Test
    fun `频率限制不增加网络失败计数也不调度重开`() {
        val policy = RpcRecoveryPolicy()

        assertEquals(RecoveryDecision.NONE, policy.onFrequencyLimited())
        assertEquals(0, policy.failureCount)
        assertEquals(RpcBlockReason.NONE, policy.blockReason)
    }

    @Test
    fun `未知失败保留现有恢复状态且不调度重开`() {
        val policy = RpcRecoveryPolicy()
        policy.onNetworkFailure(3)

        assertEquals(RecoveryDecision.NONE, policy.onUnknownFailure())
        assertEquals(1, policy.failureCount)
        assertEquals(RpcBlockReason.NONE, policy.blockReason)
    }
}
