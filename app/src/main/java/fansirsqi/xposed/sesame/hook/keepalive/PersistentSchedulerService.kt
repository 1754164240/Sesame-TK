package fansirsqi.xposed.sesame.hook.keepalive

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fansirsqi.xposed.sesame.data.General

class PersistentSchedulerService : Service() {
    private val mapper = jacksonObjectMapper()

    private val binder = object : IPersistentSchedulerService.Stub() {
        override fun registerSchedule(scheduleJson: String, nowMillis: Long): String {
            enforceCaller()
            val schedule = mapper.readValue(scheduleJson, PersistentSchedule::class.java)
            return mapper.writeValueAsString(moduleService().register(schedule, nowMillis))
        }

        override fun replaceWakeSchedules(schedulesJson: String, nowMillis: Long) {
            enforceCaller()
            val schedules = mapper.readValue(
                schedulesJson,
                object : TypeReference<List<PersistentSchedule>>() {}
            )
            moduleService().replaceWakeSchedules(schedules, nowMillis)
        }

        override fun acknowledge(
            dedupeKey: String,
            generation: Long,
            nowMillis: Long
        ): Boolean {
            enforceCaller()
            return moduleService().acknowledge(dedupeKey, generation, nowMillis)
        }

        override fun cancel(dedupeKey: String, nowMillis: Long): Boolean {
            enforceCaller()
            return moduleService().cancel(dedupeKey, nowMillis)
        }

        override fun reconcile(nowMillis: Long): String {
            enforceCaller()
            return mapper.writeValueAsString(moduleService().reconcile(nowMillis))
        }

        override fun getSchedule(dedupeKey: String): String? {
            enforceCaller()
            return moduleService().get(dedupeKey)?.let(mapper::writeValueAsString)
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        binder.takeIf { intent?.action == PersistentSchedulerContract.ACTION_BIND }

    private fun moduleService(): PersistentScheduleService =
        PersistentSchedulerRuntime.moduleService(this)

    private fun enforceCaller() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        val packages = packageManager.getPackagesForUid(callingUid).orEmpty()
        if (
            !PersistentSchedulerCallerPolicy.isAllowed(
                packages.asList(),
                General.MODULE_PACKAGE_NAME,
                General.PACKAGE_NAME
            )
        ) {
            throw SecurityException("不允许的持久调度调用方")
        }
    }
}
