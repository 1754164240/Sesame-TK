package fansirsqi.xposed.sesame.hook.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fansirsqi.xposed.sesame.data.General
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
        }

        override fun onBindingDied(name: ComponentName?) {
            remoteService = null
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

        val latch = CountDownLatch(1)
        connectionLatch = latch
        val intent = Intent(PersistentSchedulerContract.ACTION_BIND)
            .setComponent(
                ComponentName(
                    General.MODULE_PACKAGE_NAME,
                    PersistentSchedulerService::class.java.name
                )
            )
        check(applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            "无法绑定持久调度服务"
        }
        check(latch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "等待持久调度服务超时"
        }
        connectionLatch = null
        return remoteService ?: error("持久调度服务未连接")
    }

    companion object {
        private const val CONNECTION_TIMEOUT_SECONDS = 3L
    }
}
