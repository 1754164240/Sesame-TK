package fansirsqi.xposed.sesame.hook.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemotePersistentScheduleGateway(context: Context) : PersistentScheduleGateway {
    private val applicationContext = context.applicationContext ?: context
    private val mapper = jacksonObjectMapper()

    @Volatile
    private var remoteService: IPersistentSchedulerService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remoteService = IPersistentSchedulerService.Stub.asInterface(service)
            connectionLatch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            Log.record(TAG, "持久调度服务连接断开: $name")
        }

        override fun onBindingDied(name: ComponentName?) {
            remoteService = null
            Log.error(TAG, "持久调度服务Binder失效: $name")
        }
    }

    @Volatile
    private var connectionLatch: CountDownLatch? = null

    override fun register(schedule: PersistentSchedule, nowMillis: Long): PersistentSchedule {
        val json = service().registerSchedule(mapper.writeValueAsString(schedule), nowMillis)
        return mapper.readValue(json, PersistentSchedule::class.java)
    }

    override fun replaceWakeSchedules(schedules: List<PersistentSchedule>, nowMillis: Long) {
        service().replaceWakeSchedules(mapper.writeValueAsString(schedules), nowMillis)
    }

    override fun acknowledge(dedupeKey: String, generation: Long, nowMillis: Long): Boolean =
        runCatching {
            service().acknowledge(dedupeKey, generation, nowMillis)
        }.getOrDefault(false)

    override fun cancel(dedupeKey: String, nowMillis: Long): Boolean =
        runCatching {
            service().cancel(dedupeKey, nowMillis)
        }.getOrDefault(false)

    override fun reconcile(nowMillis: Long): ReconcileResult {
        val json = service().reconcile(nowMillis)
        return mapper.readValue(json, ReconcileResult::class.java)
    }

    override fun get(dedupeKey: String): PersistentSchedule? =
        runCatching {
            service().getSchedule(dedupeKey)
                ?.let { mapper.readValue(it, PersistentSchedule::class.java) }
        }.getOrNull()

    @Synchronized
    private fun service(): IPersistentSchedulerService {
        remoteService?.takeIf { it.asBinder().isBinderAlive }?.let { return it }

        logBindingDiagnostics()
        var lastFailure: Throwable? = null
        repeat(MAX_BIND_ATTEMPTS) { attempt ->
            runCatching {
                return connect(attempt + 1)
            }.onFailure {
                lastFailure = it
                Log.error(
                    TAG,
                    "持久调度服务绑定失败: attempt=${attempt + 1}/$MAX_BIND_ATTEMPTS, " +
                        "type=${it.javaClass.simpleName}, message=${it.message}"
                )
            }
        }
        throw IllegalStateException("无法绑定持久调度服务", lastFailure)
    }

    private fun connect(attempt: Int): IPersistentSchedulerService {
        val latch = CountDownLatch(1)
        connectionLatch = latch
        val component = schedulerComponent()
        val intent = Intent(PersistentSchedulerContract.ACTION_BIND).setComponent(component)
        val bound = try {
            applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (securityException: SecurityException) {
            throw IllegalStateException(
                "持久调度服务绑定被系统拒绝: component=$component, attempt=$attempt",
                securityException
            )
        }
        check(bound) {
            "bindService返回false: component=$component, attempt=$attempt"
        }
        if (!latch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            runCatching { applicationContext.unbindService(connection) }
            remoteService = null
            connectionLatch = null
            error("等待持久调度服务连接超时: component=$component, attempt=$attempt")
        }
        connectionLatch = null
        return remoteService ?: error("持久调度服务未连接")
    }

    private fun logBindingDiagnostics() {
        val component = schedulerComponent()
        val details = runCatching {
            val serviceInfo = applicationContext.packageManager.getServiceInfo(component, 0)
            "serviceFound=true, enabled=${serviceInfo.enabled}, exported=${serviceInfo.exported}"
        }.getOrElse {
            "serviceFound=false, error=${it.javaClass.simpleName}:${it.message}"
        }
        Log.record(TAG, "持久调度绑定诊断: component=$component, $details")
    }

    private fun schedulerComponent(): ComponentName =
        ComponentName(
            General.MODULE_PACKAGE_NAME,
            PersistentSchedulerService::class.java.name
        )

    companion object {
        private const val TAG = "PersistentScheduleGateway"
        private const val CONNECTION_TIMEOUT_SECONDS = 3L
        private const val MAX_BIND_ATTEMPTS = 2
    }
}
