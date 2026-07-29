package fansirsqi.xposed.sesame.util

data class PersistentNotificationState(
    val notificationId: Int,
    val title: String,
    val content: String,
    val nextExecTime: Long,
    val lastSentAt: Long
)

data class TransientNotificationSnapshot(
    val notificationId: Int,
    val title: String,
    val content: String
)

class NotificationStateStore(
    private val persistentNotificationId: Int,
    private val errorNotificationId: Int
) {
    private var persistent = PersistentNotificationState(
        notificationId = persistentNotificationId,
        title = "",
        content = "",
        nextExecTime = 0L,
        lastSentAt = 0L
    )

    @Synchronized
    fun resetPersistent(
        title: String,
        content: String,
        nextExecTime: Long,
        lastSentAt: Long
    ): PersistentNotificationState {
        persistent = PersistentNotificationState(
            notificationId = persistentNotificationId,
            title = title,
            content = content,
            nextExecTime = nextExecTime,
            lastSentAt = lastSentAt
        )
        return persistent
    }

    @Synchronized
    fun updateTitle(title: String) {
        persistent = persistent.copy(title = title)
    }

    @Synchronized
    fun updateContent(content: String) {
        persistent = persistent.copy(content = content)
    }

    @Synchronized
    fun updateNextExecTime(nextExecTime: Long) {
        persistent = persistent.copy(nextExecTime = nextExecTime)
    }

    @Synchronized
    fun markPersistentSent(sentAt: Long) {
        persistent = persistent.copy(lastSentAt = sentAt)
    }

    @Synchronized
    fun persistentSnapshot(): PersistentNotificationState = persistent.copy()

    fun errorSnapshot(title: String, content: String): TransientNotificationSnapshot {
        return TransientNotificationSnapshot(
            notificationId = errorNotificationId,
            title = title,
            content = content
        )
    }
}
