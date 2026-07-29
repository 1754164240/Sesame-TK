package fansirsqi.xposed.sesame.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationStateStoreTest {

    @Test
    fun `异常通知不会覆盖常驻通知状态`() {
        val store = NotificationStateStore(
            persistentNotificationId = 99,
            errorNotificationId = 98
        )
        store.resetPersistent(
            title = "等待执行",
            content = "上次执行成功",
            nextExecTime = 123456L,
            lastSentAt = 1000L
        )
        val before = store.persistentSnapshot()

        val error = store.errorSnapshot("RPC异常", "请稍后重试")

        assertEquals(before, store.persistentSnapshot())
        assertEquals("RPC异常", error.title)
        assertEquals("请稍后重试", error.content)
        assertNotEquals(before.notificationId, error.notificationId)
    }

    @Test
    fun `常驻状态按字段更新并保留其他字段`() {
        val store = NotificationStateStore(99, 98)
        store.resetPersistent("启动中", "暂无消息", 0L, 10L)

        store.updateTitle("等待下次执行")
        store.updateContent("上次执行成功")
        store.updateNextExecTime(999L)
        store.markPersistentSent(20L)

        assertEquals(
            PersistentNotificationState(
                notificationId = 99,
                title = "等待下次执行",
                content = "上次执行成功",
                nextExecTime = 999L,
                lastSentAt = 20L
            ),
            store.persistentSnapshot()
        )
    }
}
