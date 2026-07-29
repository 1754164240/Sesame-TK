package fansirsqi.xposed.sesame.hook.keepalive

import android.content.Context
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log

object PersistentSchedulerRuntime {
    @Volatile
    private var moduleService: PersistentScheduleService? = null

    @Volatile
    private var targetController: PersistentSchedulerController? = null

    @Volatile
    private var executionHandler: PersistentExecutionRequestHandler? = null

    @Synchronized
    fun moduleService(context: Context): PersistentScheduleService {
        moduleService?.let { return it }
        val applicationContext = context.applicationContext ?: context
        val registry = PersistentScheduleRegistry(
            PersistentScheduleFileStorage(
                Files.getPersistentScheduleFile(applicationContext)
            ) { Log.printStackTrace("PersistentScheduler", it) }
        )
        val router = ScheduledTaskRouter(
            AndroidScheduledRouteEnvironment(applicationContext)
        ) { schedule ->
            PersistentLaunchPolicy.isForegroundLaunchEnabledInPayload(schedule)
        }
        val coordinator = PersistentScheduleCoordinator(
            registry = registry,
            alarmBackend = SystemAlarmBackend(applicationContext),
            fallback = CoroutineScheduleFallback(),
            dispatcher = router
        )
        return PersistentScheduleService(registry, coordinator).also {
            moduleService = it
        }
    }

    @Synchronized
    fun initializeTarget(
        context: Context,
        currentOwnerProvider: () -> String?,
        requestExecution: (PersistentSchedule) -> Boolean,
        enabled: () -> Boolean,
        allowForegroundLaunch: () -> Boolean
    ): PersistentSchedulerController {
        val gateway = RemotePersistentScheduleGateway(context)
        val controller = PersistentSchedulerController(
            service = gateway,
            enabled = enabled,
            allowForegroundLaunch = allowForegroundLaunch
        )
        targetController = controller
        executionHandler = PersistentExecutionRequestHandler(
            scheduleProvider = gateway::get,
            currentOwnerProvider = currentOwnerProvider,
            requestExecution = requestExecution,
            acknowledge = { key, generation ->
                gateway.acknowledge(key, generation, System.currentTimeMillis())
            }
        )
        return controller
    }

    fun targetController(): PersistentSchedulerController? = targetController

    fun handleExecutionRequest(
        dedupeKey: String?,
        generation: Long,
        ownerUserId: String?
    ): Boolean =
        executionHandler?.handle(dedupeKey, generation, ownerUserId) ?: false
}
