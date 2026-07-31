package fansirsqi.xposed.sesame.task

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRecoveryLedgerTest {

    @Test
    fun `恢复时只返回验证阻断和未启动模块`() {
        val ledger = TaskRecoveryLedger()
        ledger.startRun("run-1", listOf("forest", "farm", "member", "sports"))
        ledger.record("forest", RecoverableTaskOutcome.COMPLETED)
        ledger.record("farm", RecoverableTaskOutcome.FAILED)
        ledger.record("member", RecoverableTaskOutcome.BLOCKED_VERIFICATION)

        assertEquals(listOf("member", "sports"), ledger.recoveryTaskIds("run-1"))
    }

    @Test
    fun `同一验证代际和运行只生成一次恢复请求`() {
        val ledger = TaskRecoveryLedger()
        ledger.startRun("run-2", listOf("member"))

        assertEquals(listOf("member"), ledger.claimRecovery("run-2", 3L))
        assertEquals(emptyList<String>(), ledger.claimRecovery("run-2", 3L))
    }

    @Test
    fun `已完成模块不会被后续验证阻断降级`() {
        val ledger = TaskRecoveryLedger()
        ledger.startRun("run-3", listOf("forest"))
        ledger.record("forest", RecoverableTaskOutcome.COMPLETED)
        ledger.record("forest", RecoverableTaskOutcome.BLOCKED_VERIFICATION)

        assertEquals(emptyList<String>(), ledger.recoveryTaskIds("run-3"))
    }
}
