package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityState
import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.entity.friend.FriendModuleCapability
import fansirsqi.xposed.sesame.entity.friend.FriendProfile
import fansirsqi.xposed.sesame.entity.friend.FriendRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendRepositoryTest {

    @Test
    fun syncPreservesRulesAndMarksMissingProfileRemoved() {
        val storage = InMemoryFriendCenterStorage()
        storage.save(
            "owner",
            FriendCenterConfig(
                userId = "owner",
                profiles = linkedMapOf(
                    "100" to FriendProfile(
                        userId = "100",
                        globalBlocked = true,
                        capabilities = linkedMapOf(
                            "forest" to FriendModuleCapability(state = FriendCapabilityState.OPEN)
                        )
                    ),
                    "200" to FriendProfile(userId = "200")
                )
            )
        )
        val repository = FriendRepository(storage)

        val synced = repository.loadAndSync(
            "owner",
            linkedMapOf(
                "owner" to user("owner", 1, "自己"),
                "100" to user("100", 1, "甲"),
                "300" to user("300", 0, "乙")
            )
        )

        assertTrue(synced.profiles.getValue("100").globalBlocked)
        assertEquals(
            FriendCapabilityState.OPEN,
            synced.profiles.getValue("100").capabilities.getValue("forest").state
        )
        assertEquals(FriendRelation.MUTUAL, synced.profiles.getValue("100").relation)
        assertEquals(FriendRelation.REMOVED, synced.profiles.getValue("200").relation)
        assertTrue(synced.profiles.getValue("200").removed)
        assertEquals(FriendRelation.ONE_WAY, synced.profiles.getValue("300").relation)
        assertEquals(FriendRelation.SELF, synced.profiles.getValue("owner").relation)
    }

    @Test
    fun repositoriesAreIsolatedByOwnerUserId() {
        val storage = InMemoryFriendCenterStorage()
        val repository = FriendRepository(storage)

        repository.loadAndSync("ownerA", mapOf("100" to user("100", 1, "甲")))
        repository.loadAndSync("ownerB", mapOf("200" to user("200", 1, "乙")))

        assertTrue(repository.current("ownerA").profiles.containsKey("100"))
        assertFalse(repository.current("ownerA").profiles.containsKey("200"))
        assertTrue(repository.current("ownerB").profiles.containsKey("200"))
        assertFalse(repository.current("ownerB").profiles.containsKey("100"))
    }

    @Test
    fun emptyOwnerDoesNotReadOrWriteStorage() {
        val storage = InMemoryFriendCenterStorage()
        val repository = FriendRepository(storage)

        val result = repository.loadAndSync("", mapOf("100" to user("100", 1, "甲")))

        assertTrue(result.profiles.isEmpty())
        assertTrue(storage.snapshot().isEmpty())
    }

    private fun user(userId: String, friendStatus: Int, name: String): UserEntity =
        UserEntity(
            userId = userId,
            account = null,
            friendStatus = friendStatus,
            realName = null,
            nickName = name,
            remarkName = null
        )
}

internal class InMemoryFriendCenterStorage : FriendCenterStorage {
    private val values = linkedMapOf<String, FriendCenterConfig>()

    override fun load(ownerUserId: String): FriendCenterConfig? = values[ownerUserId]?.deepCopy()

    override fun save(ownerUserId: String, config: FriendCenterConfig): Boolean {
        values[ownerUserId] = config.deepCopy()
        return true
    }

    fun snapshot(): Map<String, FriendCenterConfig> = values.mapValues { it.value.deepCopy() }

    private fun FriendCenterConfig.deepCopy(): FriendCenterConfig =
        copy(
            groups = groups.map { it.copy(memberIds = LinkedHashSet(it.memberIds)) }.toMutableList(),
            profiles = profiles.mapValuesTo(linkedMapOf()) { (_, profile) ->
                profile.copy(
                    capabilities = profile.capabilities.mapValuesTo(linkedMapOf()) { (_, capability) ->
                        capability.copy()
                    }
                )
            }
        )
}
