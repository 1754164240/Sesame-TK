package fansirsqi.xposed.sesame.util

//import android.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import fansirsqi.xposed.sesame.data.RuntimeInfo
import fansirsqi.xposed.sesame.hook.Toast
import fansirsqi.xposed.sesame.model.BaseModel
import kotlin.concurrent.Volatile

@SuppressLint("StaticFieldLeak")
object Notify {
    private val TAG: String = Notify::class.java.getSimpleName()

    @SuppressLint("StaticFieldLeak")
    var context: Context? = null
    private const val NOTIFICATION_ID = 99
    private const val ERROR_NOTIFICATION_ID = 98
    private const val CHANNEL_ID = "fansirsqi.xposed.sesame.ANTFOREST_NOTIFY_CHANNEL"
    private const val ERROR_CHANNEL_ID = "fansirsqi.xposed.sesame.ERROR_NOTIFY_CHANNEL"
    private var mNotifyManager: NotificationManager? = null
    private val stateStore = NotificationStateStore(
        persistentNotificationId = NOTIFICATION_ID,
        errorNotificationId = ERROR_NOTIFICATION_ID
    )

    @SuppressLint("StaticFieldLeak")
    private var builder: NotificationCompat.Builder? = null

    @Volatile
    private var isNotificationStarted = false


    private fun checkPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.error(TAG, "Missing POST_NOTIFICATIONS permission to send new notification$context")
                Toast.show("请在设置中开启目标应用通知权限")
                return false
            }
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.error(TAG, "Notifications are disabled for this app.$context")
            Toast.show("请在设置中开启目标应用通知权限")
            return false
        }
        return true
    }

    @JvmStatic
    fun start(context: Context) {
        try {
            if (checkPermission(context)) {
                Notify.context = context
                stop()
                val initialState = stateStore.resetPersistent(
                    title = "🚀 启动中",
                    content = "🔔 暂无消息",
                    nextExecTime = 0L,
                    lastSentAt = System.currentTimeMillis()
                )
                mNotifyManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                val it = Intent(Intent.ACTION_VIEW)
                it.setData("alipays://platformapi/startapp?appId=".toUri())
                val pi = PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationChannel = NotificationChannel(CHANNEL_ID, "🔔 芝麻粒能量提醒", NotificationManager.IMPORTANCE_LOW)
                    notificationChannel.enableLights(false)
                    notificationChannel.enableVibration(false)
                    notificationChannel.setShowBadge(false)
                    mNotifyManager!!.createNotificationChannel(notificationChannel)
                }
                builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(context.resources, android.R.drawable.sym_def_app_icon))
                    .setContentTitle(initialState.title)
                    .setContentText(initialState.content)
                    .setSubText("芝麻粒")
                    .setAutoCancel(false)
                    .setContentIntent(pi)
                if (BaseModel.enableOnGoing.value) {
                    builder!!.setOngoing(true)
                }
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder!!.build())
                isNotificationStarted = true
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    /**
     * 停止通知。 移除通知并停止前台服务。
     */
    @JvmStatic
    fun stop() {
        try {
            if (context == null) {
                // Log.error(TAG, "Context is null in stop(), cannot proceed.");
                return
            }
            if (context is Service) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    (context as Service).stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    (context as Service).stopSelf()
                }
            }
            NotificationManagerCompat.from(context!!).cancel(NOTIFICATION_ID)
            mNotifyManager = null
            isNotificationStarted = false
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    /**
     * 更新通知文本。 更新通知的标题和内容文本，并发送通知。
     *
     * @param status 要更新的状态文本。
     */
    @JvmStatic
    fun updateStatusText(status: String?) {
        var status = status
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            val forestPauseTime = RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime)
            if (forestPauseTime > System.currentTimeMillis()) {
                status = "❌ 触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime) + "恢复运行"
            }
            stateStore.updateTitle(status.orEmpty())
            sendText(true)
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    /**
     * 更新下一次执行时间的文本。
     *
     * @param nextExecTime 下一次执行的时间。
     */
    @JvmStatic
    fun updateNextExecText(nextExecTime: Long) {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            if (nextExecTime != -1L) {
                stateStore.updateNextExecTime(nextExecTime)
            }
            val state = stateStore.persistentSnapshot()
            stateStore.updateTitle(
                if (state.nextExecTime > 0) {
                    "⏰ 下次执行 " + TimeUtil.getTimeStr(state.nextExecTime)
                } else {
                    ""
                }
            )
            sendText(false)
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    /**
     * 更新上一次执行的文本。
     *
     * @param content 上一次执行的内容。
     */
    @JvmStatic
    fun updateLastExecText(content: String?) {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            stateStore.updateContent(
                "📌 上次执行 " + TimeUtil.getTimeStr(System.currentTimeMillis()) + "\n🌾 " + content
            )
            sendText(false)
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }


    /**
     * 设置状态文本为执行中。
     */
    @JvmStatic
    fun setStatusTextExec() {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            val forestPauseTime = RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime)

            val title = if (forestPauseTime > System.currentTimeMillis()) {
                "❌ 触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime) + "恢复运行"
            } else {
                "⚙️ 芝麻粒正在执行中..."
            }
            stateStore.updateTitle(title)
            sendText(true)
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    /**
     * 设置状态文本为已禁用
     */
    @JvmStatic
    fun setStatusTextDisabled() {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            stateStore.updateTitle("🚫 芝麻粒已禁用")
            builder!!.setProgress(0, 0, false)
            sendText(true)
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    @JvmStatic
    fun setStatusTextExec(content: String?) {
        updateStatusText("🔥 $content 运行中...")
    }

    /**
     * 发送文本更新。 更新通知的内容文本，并重新发送通知。
     *
     * @param force 是否强制刷新
     */
    @Synchronized
    private fun sendText(force: Boolean) {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            val now = System.currentTimeMillis()
            val state = stateStore.persistentSnapshot()
            if (!force && now - state.lastSentAt < 500) {
                return
            }
            if (builder != null) {
                builder!!.setContentTitle(state.title)
                if (!StringUtil.isEmpty(state.content)) {
                    builder!!.setContentText(state.content)
                }
                mNotifyManager!!.notify(NOTIFICATION_ID, builder!!.build())
                stateStore.markPersistentSent(now)
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    @SuppressLint("StaticFieldLeak")
    @JvmStatic
    fun sendNewNotification(title: String?, content: String?) {
        try {
            if (context == null) {
                Log.error(TAG, "Context is null in sendErrorNotification, cannot proceed.")
                return
            }
            if (!checkPermission(context!!) || !isNotificationStarted) return
            val snapshot = stateStore.errorSnapshot(title.orEmpty(), content.orEmpty())
            val notificationManager =
                context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel = NotificationChannel(
                    ERROR_CHANNEL_ID,
                    "‼️ 芝麻粒异常通知",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager?.createNotificationChannel(notificationChannel)
            }
            val errorBuilder = NotificationCompat.Builder(context!!, ERROR_CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setLargeIcon(BitmapFactory.decodeResource(context!!.resources, android.R.drawable.sym_def_app_icon))
                .setContentTitle(snapshot.title)
                .setContentText(snapshot.content)
                .setSubText("芝麻粒")
                .setAutoCancel(true)
            NotificationManagerCompat.from(context!!)
                .notify(snapshot.notificationId, errorBuilder.build())
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }
}
