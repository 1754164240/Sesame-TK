package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentExecutionRequestHandlerTest {

    @Test
    fun currentGenerationForCurrentOwnerTriggersAndAcknowledges() {
        val schedule = schedule(generation = 2L, owner = "owner")
        val executed = mutableListOf<String>()
        val acknowledged = mutableListOf<Pair<String, Long>>()
        val handler = PersistentExecutionRequestHandler(
            scheduleProvider = { schedule },
            currentOwnerProvider = { "owner" },
            requestExecution = { executed.add(it.dedupeKey); true },
            acknowledge = { key, generation ->
                acknowledged.add(key to generation)
                true
            }
        )

        val accepted = handler.handle("global:poll", 2L, "owner")

        assertTrue(accepted)
        assertEquals(listOf("global:poll"), executed)
        assertEquals(listOf("global:poll" to 2L), acknowledged)
    }

    @Test
    fun staleGenerationCannotExecuteOrCompleteReplacement() {
        val schedule = schedule(generation = 3L, owner = "owner")
        var executionCount = 0
        var acknowledgeCount = 0
        val handler = PersistentExecutionRequestHandler(
            scheduleProvider = { schedule },
            currentOwnerProvider = { "owner" },
            requestExecution = { executionCount++; true },
            acknowledge = { _, _ -> acknowledgeCount++; true }
        )

        val accepted = handler.handle("global:poll", 2L, "owner")

        assertFalse(accepted)
        assertEquals(0, executionCount)
        assertEquals(0, acknowledgeCount)
    }

    @Test
    fun differentOwnerCannotExecute() {
        val handler = PersistentExecutionRequestHandler(
            scheduleProvider = { schedule(generation = 2L, owner = "owner") },
            currentOwnerProvider = { "other" },
            requestExecution = { true },
            acknowledge = { _, _ -> true }
        )

        assertFalse(handler.handle("global:poll", 2L, "owner"))
    }

    private fun schedule(generation: Long, owner: String): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = "global:poll",
            kind = PersistentScheduleKind.GLOBAL_POLL,
            triggerAtMillis = 1_000L,
            ownerUserId = owner,
            generation = generation
        )
}
