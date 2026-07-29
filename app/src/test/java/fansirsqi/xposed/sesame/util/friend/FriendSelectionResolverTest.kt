package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityFilter
import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityState
import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.entity.friend.FriendGroup
import fansirsqi.xposed.sesame.entity.friend.FriendModuleCapability
import fansirsqi.xposed.sesame.entity.friend.FriendProfile
import fansirsqi.xposed.sesame.entity.friend.FriendRelation
import fansirsqi.xposed.sesame.entity.friend.FriendRelationFilter
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionCountSpec
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionScope
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendSelectionResolverTest {
    private val resolver = FriendSelectionResolver

    @Test
    fun allFriendsIsResolvedFromEveryCurrentSnapshotWithoutChangingSpec() {
        val spec = FriendSelectionSpec(selectionScope = FriendSelectionScope.ALL_FRIENDS)

        assertEquals(
            linkedSetOf("100"),
            resolver.resolveIds(spec, config(profile("100")))
        )
        assertEquals(
            linkedSetOf("100", "200"),
            resolver.resolveIds(spec, config(profile("100"), profile("200")))
        )
        assertTrue(spec.includeUserIds.isEmpty())
    }

    @Test
    fun exclusionsAndGlobalBlockOverrideAllIncludes() {
        val spec = FriendSelectionSpec(
            selectionScope = FriendSelectionScope.ALL_FRIENDS,
            includeGroupIds = linkedSetOf("g1"),
            excludeUserIds = linkedSetOf("200")
        )
        val config = config(
            profile("100", blocked = true),
            profile("200"),
            groups = mutableListOf(FriendGroup(id = "g1", name = "组", memberIds = linkedSetOf("100", "200")))
        )

        assertTrue(resolver.resolveIds(spec, config).isEmpty())
    }

    @Test
    fun relationFiltersDoNotIncludeRemovedOrUnknownProfiles() {
        val profiles = arrayOf(
            profile("self", relation = FriendRelation.SELF),
            profile("mutual", relation = FriendRelation.MUTUAL),
            profile("oneWay", relation = FriendRelation.ONE_WAY),
            profile("removed", relation = FriendRelation.REMOVED, removed = true),
            profile("unknown", relation = FriendRelation.UNKNOWN)
        )
        val all = FriendSelectionSpec(
            selectionScope = FriendSelectionScope.ALL_FRIENDS,
            relationFilter = FriendRelationFilter.ALL_KNOWN
        )
        val includeSelf = all.copy(relationFilter = FriendRelationFilter.INCLUDE_SELF)

        assertEquals(linkedSetOf("mutual", "oneWay"), resolver.resolveIds(all, config(*profiles)))
        assertEquals(linkedSetOf("self", "mutual", "oneWay"), resolver.resolveIds(includeSelf, config(*profiles)))
    }

    @Test
    fun capabilityFilterRequiresEveryModuleAndRespectsUnknownPolicy() {
        val spec = FriendSelectionSpec(
            selectionScope = FriendSelectionScope.ALL_FRIENDS,
            capabilityFilter = FriendCapabilityFilter(
                moduleKeys = linkedSetOf("forest", "farm"),
                requiredStates = linkedSetOf(FriendCapabilityState.OPEN),
                includeUnknown = false
            )
        )
        val complete = profile(
            "100",
            capabilities = linkedMapOf(
                "forest" to FriendModuleCapability(state = FriendCapabilityState.OPEN),
                "farm" to FriendModuleCapability(state = FriendCapabilityState.OPEN)
            )
        )
        val incomplete = profile(
            "200",
            capabilities = linkedMapOf(
                "forest" to FriendModuleCapability(state = FriendCapabilityState.OPEN)
            )
        )

        assertEquals(linkedSetOf("100"), resolver.resolveIds(spec, config(complete, incomplete)))
        spec.capabilityFilter?.includeUnknown = true
        assertEquals(linkedSetOf("100", "200"), resolver.resolveIds(spec, config(complete, incomplete)))
    }

    @Test
    fun countPriorityIsUserThenFirstIncludedGroupThenDefault() {
        val selection = FriendSelectionSpec(
            includeUserIds = linkedSetOf("100", "200", "300", "400"),
            includeGroupIds = linkedSetOf("g1", "g2")
        )
        val countSpec = FriendSelectionCountSpec(
            selection = selection,
            defaultCount = 1,
            groupCountOverrides = linkedMapOf("g1" to 2, "g2" to 4),
            userCountOverrides = linkedMapOf("100" to 5, "400" to 0)
        )
        val config = config(
            profile("100"),
            profile("200"),
            profile("300"),
            profile("400"),
            groups = mutableListOf(
                FriendGroup(id = "g1", memberIds = linkedSetOf("200", "300")),
                FriendGroup(id = "g2", memberIds = linkedSetOf("300"))
            )
        )

        assertEquals(
            linkedMapOf("100" to 5, "200" to 2, "300" to 2),
            resolver.resolveCountMap(countSpec, config)
        )
    }

    @Test
    fun containsRejectsBlankAndUnknownIds() {
        val spec = FriendSelectionSpec(selectionScope = FriendSelectionScope.ALL_FRIENDS)
        val config = config(profile("100"))

        assertFalse(resolver.contains(spec, "", config))
        assertFalse(resolver.contains(spec, "999", config))
        assertTrue(resolver.contains(spec, "100", config))
    }

    private fun profile(
        id: String,
        relation: FriendRelation = FriendRelation.MUTUAL,
        blocked: Boolean = false,
        removed: Boolean = false,
        capabilities: LinkedHashMap<String, FriendModuleCapability> = linkedMapOf()
    ): FriendProfile =
        FriendProfile(
            userId = id,
            displayName = id,
            relation = relation,
            globalBlocked = blocked,
            removed = removed,
            capabilities = capabilities
        )

    private fun config(
        vararg profiles: FriendProfile,
        groups: MutableList<FriendGroup> = mutableListOf()
    ): FriendCenterConfig =
        FriendCenterConfig(
            userId = "owner",
            groups = groups,
            profiles = profiles.associateByTo(linkedMapOf()) { it.userId }
        )
}
