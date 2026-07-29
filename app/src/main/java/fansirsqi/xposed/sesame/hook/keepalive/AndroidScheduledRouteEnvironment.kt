package fansirsqi.xposed.sesame.hook.keepalive

import android.content.Context
import android.content.Intent
import fansirsqi.xposed.sesame.data.General

class AndroidScheduledRouteEnvironment(
    context: Context
) : ScheduledRouteEnvironment {
    private val applicationContext = context.applicationContext ?: context

    override fun isTargetProcess(): Boolean = false

    override fun currentOwnerUserId(): String? = null

    override fun requestExecution(schedule: PersistentSchedule): Boolean = false

    override fun sendToTarget(schedule: PersistentSchedule): Boolean =
        runCatching {
            val intent = Intent(PersistentSchedulerContract.ACTION_EXECUTE)
                .setPackage(General.PACKAGE_NAME)
                .putExtra(PersistentSchedulerContract.EXTRA_DEDUPE_KEY, schedule.dedupeKey)
                .putExtra(PersistentSchedulerContract.EXTRA_GENERATION, schedule.generation)
                .putExtra(PersistentSchedulerContract.EXTRA_OWNER_USER_ID, schedule.ownerUserId)
            applicationContext.sendBroadcast(intent)
            true
        }.getOrDefault(false)

    override fun launchTarget(): Boolean =
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setClassName(General.PACKAGE_NAME, General.CURRENT_USING_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(intent)
            true
        }.getOrDefault(false)
}
