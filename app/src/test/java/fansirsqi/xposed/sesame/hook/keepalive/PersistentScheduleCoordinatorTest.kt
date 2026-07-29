package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentScheduleCoordinatorTest {

    @Test
    fun reconcileArmsOnlyEarliestPendingSchedule() {
        val fixture = fixture()
        fixture.registry.upsert(schedule("custom:0900", 9_000L), 0L)
        fixture.registry.upsert(schedule("global:poll", 5_000L), 0L)

        val result = fixture.coordinator.reconcile(1_000L)

        assertEquals(listOf(5_000L), fixture.alarm.armedTimes)
        assertEquals(5_000L, result.nextTriggerAtMillis)
        assertFalse(result.usedFallback)
    }

    @Test
    fun repeatedReconcileDoesNotRearmUnchangedEarliestTime() {
        val fixture = fixture()
        fixture.registry.upsert(schedule("global:poll", 5_000L), 0L)

        fixture.coordinator.reconcile(1_000L)
        fixture.coordinator.reconcile(2_000L)

        assertEquals(listOf(5_000L), fixture.alarm.armedTimes)
    }

    @Test
    fun oneAlarmDispatchesEveryTaskInDueWindow() {
        val fixture = fixture()
        fixture.registry.upsert(schedule("a", 10_000L), 0L)
        fixture.registry.upsert(schedule("b", 10_500L), 0L)
        fixture.registry.upsert(schedule("later", 20_000L), 0L)

        val result = fixture.coordinator.onAlarm(10_000L)

        assertEquals(listOf("a", "b"), fixture.dispatcher.keys)
        assertEquals(2, result.completedCount)
        assertEquals(0, result.retriedCount)
        assertEquals(20_000L, result.nextTriggerAtMillis)
        assertEquals(listOf(20_000L), fixture.alarm.armedTimes)
    }

    @Test
    fun deferredDispatchIsRetriedWithoutChangingGeneration() {
        val fixture = fixture(dispatchResult = ScheduleDispatchResult.DEFERRED)
        val original = fixture.registry.upsert(schedule("a", 10_000L), 0L)

        val result = fixture.coordinator.onAlarm(10_000L)

        val retried = fixture.registry.get("a")
        assertEquals(1, result.retriedCount)
        assertEquals(original.generation, retried?.generation)
        assertEquals(70_000L, retried?.triggerAtMillis)
        assertEquals(PersistentScheduleState.PENDING, retried?.state)
    }

    @Test
    fun alarmFailureSchedulesSingleNamedProcessFallback() {
        val fixture = fixture(alarmArmResult = false)
        fixture.registry.upsert(schedule("global:poll", 5_000L), 0L)

        val result = fixture.coordinator.reconcile(1_000L)
        fixture.coordinator.reconcile(2_000L)

        assertTrue(result.usedFallback)
        assertEquals(
            listOf(FallbackCall(4_000L, PersistentScheduleCoordinator.FALLBACK_KEY)),
            fixture.fallback.calls
        )
        assertTrue(fixture.registry.get("global:poll") != null)
    }

    @Test
    fun exactAlarmPermissionChangeRetriesSystemAlarmAndCancelsFallback() {
        val fixture = fixture(alarmArmResult = false)
        fixture.registry.upsert(schedule("global:poll", 5_000L), 0L)
        fixture.coordinator.reconcile(1_000L)
        fixture.alarm.armResult = true

        val result = fixture.coordinator.forceReconcile(2_000L)

        assertFalse(result.usedFallback)
        assertEquals(5_000L, result.nextTriggerAtMillis)
        assertEquals(1, fixture.fallback.cancelCount)
    }

    @Test
    fun noTasksCancelsPreviouslyArmedAlarm() {
        val fixture = fixture()
        val scheduled = fixture.registry.upsert(schedule("global:poll", 5_000L), 0L)
        fixture.coordinator.reconcile(1_000L)
        fixture.registry.cancel(scheduled.dedupeKey)

        val result = fixture.coordinator.reconcile(2_000L)

        assertEquals(1, fixture.alarm.cancelCount)
        assertNull(result.nextTriggerAtMillis)
    }

    private fun fixture(
        alarmArmResult: Boolean = true,
        dispatchResult: ScheduleDispatchResult = ScheduleDispatchResult.ROUTED
    ): Fixture {
        val registry = PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())
        val alarm = FakeAlarmBackend(alarmArmResult)
        val fallback = FakeScheduleFallback()
        val dispatcher = FakeDispatcher(dispatchResult)
        return Fixture(
            registry,
            alarm,
            fallback,
            dispatcher,
            PersistentScheduleCoordinator(registry, alarm, fallback, dispatcher)
        )
    }

    private fun schedule(key: String, triggerAtMillis: Long): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = key,
            kind = PersistentScheduleKind.GLOBAL_POLL,
            triggerAtMillis = triggerAtMillis
        )

    private data class Fixture(
        val registry: PersistentScheduleRegistry,
        val alarm: FakeAlarmBackend,
        val fallback: FakeScheduleFallback,
        val dispatcher: FakeDispatcher,
        val coordinator: PersistentScheduleCoordinator
    )
}

private class FakeAlarmBackend(
    var armResult: Boolean
) : ScheduleAlarmBackend {
    val armedTimes = mutableListOf<Long>()
    var cancelCount = 0

    override fun arm(triggerAtMillis: Long): Boolean {
        armedTimes.add(triggerAtMillis)
        return armResult
    }

    override fun cancel(): Boolean {
        cancelCount++
        return true
    }
}

private data class FallbackCall(
    val delayMillis: Long,
    val dedupeKey: String
)

private class FakeScheduleFallback : ScheduleFallback {
    val calls = mutableListOf<FallbackCall>()
    var cancelCount = 0

    override fun schedule(delayMillis: Long, dedupeKey: String, callback: () -> Unit) {
        calls.add(FallbackCall(delayMillis, dedupeKey))
    }

    override fun cancel(dedupeKey: String) {
        cancelCount++
    }
}

private class FakeDispatcher(
    private val result: ScheduleDispatchResult
) : ScheduleTaskDispatcher {
    val keys = mutableListOf<String>()

    override fun dispatch(schedule: PersistentSchedule): ScheduleDispatchResult {
        keys.add(schedule.dedupeKey)
        return result
    }
}
