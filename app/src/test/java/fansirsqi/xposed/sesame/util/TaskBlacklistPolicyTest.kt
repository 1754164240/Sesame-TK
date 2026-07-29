package fansirsqi.xposed.sesame.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskBlacklistPolicyTest {

    @Test
    fun `任务黑名单管理器实际接入内置规则`() {
        assertTrue(
            TaskBlacklist.builtInEntries()
                .contains("坚持去玩休闲小游戏")
        )
    }

    @Test
    fun `内置游戏规则可以阻断扩展标题`() {
        assertTrue(
            TaskBlacklist.isBuiltInTaskBlocked(
                "坚持去玩休闲小游戏30秒"
            )
        )
        assertFalse(
            TaskBlacklist.isBuiltInTaskBlocked(
                "浏览会员会场15秒"
            )
        )
    }
}
