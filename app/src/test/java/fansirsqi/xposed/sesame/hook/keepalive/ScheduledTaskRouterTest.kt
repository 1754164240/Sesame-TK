package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTaskRouterTest {

    @Test
    fun targetProcessRoutesKnownTaskWithoutForegroundLaunch() {
        val environment = FakeRouteEnvironment(targetProcess = true, currentOwner = "owner")
        val router = ScheduledTaskRouter(environment) { false }

        val result = router.dispatch(schedule(owner = "owner"))

        assertEquals(ScheduleDispatchResult.ROUTED, result)
        assertEquals(listOf("global:poll"), environment.executedKeys)
        assertFalse(environment.launchCalled)
    }

    @Test
    fun targetProcessRejectsDifferentOwner() {
        val environment = FakeRouteEnvironment(targetProcess = true, currentOwner = "other")
        val router = ScheduledTaskRouter(environment) { true }

        val result = router.dispatch(schedule(owner = "owner"))

        assertEquals(ScheduleDispatchResult.SKIPPED_ACCOUNT, result)
        assertTrue(environment.executedKeys.isEmpty())
        assertFalse(environment.launchCalled)
    }

    @Test
    fun moduleProcessSendingBroadcastRemainsDeferredUntilTargetAcknowledges() {
        val environment = FakeRouteEnvironment(targetProcess = false, sendResult = true)
        val router = ScheduledTaskRouter(environment) { false }

        val result = router.dispatch(schedule(owner = "owner"))

        assertEquals(ScheduleDispatchResult.DEFERRED, result)
        assertEquals(listOf("global:poll"), environment.sentKeys)
        assertFalse(environment.launchCalled)
    }

    @Test
    fun foregroundLaunchRequiresBothGlobalSwitchAndPayloadRequest() {
        val environment = FakeRouteEnvironment(targetProcess = false, sendResult = true)
        val router = ScheduledTaskRouter(environment) { true }

        val result = router.dispatch(
            schedule(owner = "owner", payload = """{"launchTarget":true}""")
        )

        assertEquals(ScheduleDispatchResult.DEFERRED, result)
        assertTrue(environment.launchCalled)
    }

    @Test
    fun unknownKindIsNotSentOrExecuted() {
        val environment = FakeRouteEnvironment(targetProcess = false)
        val router = ScheduledTaskRouter(environment) { true }

        val result = router.dispatch(schedule(kind = PersistentScheduleKind.UNKNOWN))

        assertEquals(ScheduleDispatchResult.UNSUPPORTED, result)
        assertTrue(environment.sentKeys.isEmpty())
        assertFalse(environment.launchCalled)
    }

    @Test
    fun verificationCycleIsForegroundLaunchedAtMostOnce() {
        val environment = FakeRouteEnvironment(targetProcess = false, sendResult = true)
        val router = ScheduledTaskRouter(environment) { true }
        val probe = schedule(
            kind = PersistentScheduleKind.VERIFICATION_PROBE,
            payload = """{"launchTarget":true}"""
        ).copy(dedupeKey = "verification:probe:owner:5")

        router.dispatch(probe)
        router.dispatch(probe.copy(generation = 2L))

        assertEquals(1, environment.launchCount)
    }

    private fun schedule(
        owner: String? = null,
        kind: PersistentScheduleKind = PersistentScheduleKind.GLOBAL_POLL,
        payload: String = "{}"
    ): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = "global:poll",
            kind = kind,
            triggerAtMillis = 1_000L,
            payloadJson = payload,
            ownerUserId = owner,
            generation = 1L
        )
}

private class FakeRouteEnvironment(
    private val targetProcess: Boolean,
    private val currentOwner: String? = null,
    private val sendResult: Boolean = true,
    private val executeResult: Boolean = true
) : ScheduledRouteEnvironment {
    val sentKeys = mutableListOf<String>()
    val executedKeys = mutableListOf<String>()
    var launchCalled = false
    var launchCount = 0

    override fun isTargetProcess(): Boolean = targetProcess

    override fun currentOwnerUserId(): String? = currentOwner

    override fun requestExecution(schedule: PersistentSchedule): Boolean {
        executedKeys.add(schedule.dedupeKey)
        return executeResult
    }

    override fun sendToTarget(schedule: PersistentSchedule): Boolean {
        sentKeys.add(schedule.dedupeKey)
        return sendResult
    }

    override fun launchTarget(): Boolean {
        launchCalled = true
        launchCount++
        return true
    }
}
