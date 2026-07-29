package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class PersistentScheduleRegistryTest {

    @Test
    fun sameDedupeKeyReplacesTimeAndIncrementsGeneration() {
        val registry = PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())

        registry.upsert(schedule("global:poll", 1_000L), nowMillis = 100L)
        val updated = registry.upsert(schedule("global:poll", 2_000L), nowMillis = 200L)

        assertEquals(1, registry.all().size)
        assertEquals(2_000L, updated.triggerAtMillis)
        assertEquals(2L, updated.generation)
        assertEquals(PersistentScheduleState.PENDING, updated.state)
    }

    @Test
    fun expiredClaimReturnsToPendingAfterProcessDeath() {
        val registry = PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())
        registry.upsert(schedule("global:poll", 1_000L), 0L)

        val claimed = registry.claimDue(1_000L, windowMillis = 500L, leaseMillis = 5_000L)
        val recovered = registry.recoverExpiredClaims(6_001L)

        assertEquals(1, claimed.size)
        assertEquals(1, recovered)
        assertEquals(PersistentScheduleState.PENDING, registry.get("global:poll")?.state)
        assertEquals(0L, registry.get("global:poll")?.leaseUntilMillis)
    }

    @Test
    fun oldGenerationCannotCompleteReplacement() {
        val registry = PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())
        val first = registry.upsert(schedule("global:poll", 1_000L), 0L)
        val replacement = registry.upsert(schedule("global:poll", 2_000L), 1L)

        assertFalse(registry.complete(first.dedupeKey, first.generation))
        assertNotNull(registry.get(replacement.dedupeKey))
        assertTrue(registry.complete(replacement.dedupeKey, replacement.generation))
        assertNull(registry.get(replacement.dedupeKey))
    }

    @Test
    fun dueWindowClaimsTasksOnceInStableOrder() {
        val registry = PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())
        registry.upsert(schedule("b", 1_500L), 0L)
        registry.upsert(schedule("a", 1_000L), 0L)
        registry.upsert(schedule("later", 2_100L), 0L)

        val first = registry.claimDue(1_000L, windowMillis = 500L, leaseMillis = 5_000L)
        val second = registry.claimDue(1_000L, windowMillis = 500L, leaseMillis = 5_000L)

        assertEquals(listOf("a", "b"), first.map { it.dedupeKey })
        assertTrue(second.isEmpty())
        assertEquals("later", registry.nextPending()?.dedupeKey)
    }

    @Test
    fun saveFailureKeepsMutationInMemoryForProcessFallback() {
        val storage = InMemoryPersistentScheduleStorage(saveResult = false)
        val registry = PersistentScheduleRegistry(storage)

        registry.upsert(schedule("global:poll", 1_000L), 0L)

        assertNotNull(registry.get("global:poll"))
        assertTrue(registry.hasUnsavedChanges())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankDedupeKeyIsRejected() {
        PersistentScheduleRegistry(InMemoryPersistentScheduleStorage())
            .upsert(schedule(" ", 1_000L), 0L)
    }

    @Test
    fun fileStorageRoundTripsSchedulesAtomically() {
        val directory = createTempDirectory(prefix = "persistent-schedule-").toFile()
        val file = File(directory, "schedules.json")
        try {
            val storage = PersistentScheduleFileStorage(file)
            val expected = listOf(schedule("global:poll", 1_000L).copy(generation = 3L))

            assertTrue(storage.save(expected))
            assertEquals(expected, storage.load())
            assertFalse(File(directory, "schedules.json.tmp").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptFileReturnsEmptyWithoutDeletingEvidence() {
        val directory = createTempDirectory(prefix = "persistent-schedule-corrupt-").toFile()
        val file = File(directory, "schedules.json")
        try {
            file.writeText("{broken")
            val storage = PersistentScheduleFileStorage(file)

            assertTrue(storage.load().isEmpty())
            assertEquals("{broken", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun schedule(key: String, triggerAtMillis: Long): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = key,
            kind = PersistentScheduleKind.GLOBAL_POLL,
            triggerAtMillis = triggerAtMillis
        )
}

internal class InMemoryPersistentScheduleStorage(
    initial: List<PersistentSchedule> = emptyList(),
    var saveResult: Boolean = true
) : PersistentScheduleStorage {
    private var schedules = initial.map { it.copy() }

    override fun load(): List<PersistentSchedule> = schedules.map { it.copy() }

    override fun save(schedules: List<PersistentSchedule>): Boolean {
        if (saveResult) this.schedules = schedules.map { it.copy() }
        return saveResult
    }

    fun snapshot(): List<PersistentSchedule> = schedules.map { it.copy() }
}
