package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityState
import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.entity.friend.FriendProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendCapabilityRecorderTest {

    @Test
    fun recordUpdatesOnlyRequestedModuleAndPersistsObservationTime() {
        val storage = InMemoryFriendCenterStorage()
        storage.save(
            "owner",
            FriendCenterConfig(
                userId = "owner",
                profiles = linkedMapOf("100" to FriendProfile(userId = "100"))
            )
        )
        val repository = FriendRepository(storage)
        val recorder = FriendCapabilityRecorder(repository)

        assertTrue(
            recorder.record(
                ownerUserId = "owner",
                friendUserId = "100",
                moduleKey = "forest",
                state = FriendCapabilityState.OPEN,
                source = "home",
                reason = "已开通",
                observedAt = 1234L
            )
        )

        val capability = repository.current("owner").profiles
            .getValue("100").capabilities.getValue("forest")
        assertEquals(FriendCapabilityState.OPEN, capability.state)
        assertEquals("home", capability.source)
        assertEquals("已开通", capability.reason)
        assertEquals(1234L, capability.observedAt)
    }

    @Test
    fun recordRejectsBlankKeysAndUnknownProfiles() {
        val storage = InMemoryFriendCenterStorage()
        storage.save(
            "owner",
            FriendCenterConfig(
                userId = "owner",
                profiles = linkedMapOf("100" to FriendProfile(userId = "100"))
            )
        )
        val recorder = FriendCapabilityRecorder(FriendRepository(storage))

        assertFalse(recorder.record("owner", "100", "", FriendCapabilityState.OPEN, "", "", 1L))
        assertFalse(recorder.record("owner", "999", "forest", FriendCapabilityState.OPEN, "", "", 1L))
        assertFalse(recorder.record("", "100", "forest", FriendCapabilityState.OPEN, "", "", 1L))
    }
}
