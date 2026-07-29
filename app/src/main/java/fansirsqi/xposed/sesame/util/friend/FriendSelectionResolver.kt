package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityState
import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.entity.friend.FriendProfile
import fansirsqi.xposed.sesame.entity.friend.FriendRelation
import fansirsqi.xposed.sesame.entity.friend.FriendRelationFilter
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionCountSpec
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionScope
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionSpec
import fansirsqi.xposed.sesame.util.maps.UserMap

object FriendSelectionResolver {

    @JvmStatic
    fun resolveIds(
        spec: FriendSelectionSpec?,
        config: FriendCenterConfig
    ): Set<String> {
        val selection = spec ?: return emptySet()
        val included = selectedIds(selection, config)
        if (included.isEmpty()) return emptySet()
        val excluded = expandIds(selection.excludeUserIds, selection.excludeGroupIds, config)

        return included.filterTo(linkedSetOf()) { userId ->
            if (userId in excluded) return@filterTo false
            val profile = config.profiles[userId] ?: return@filterTo false
            !profile.removed &&
                profile.relation != FriendRelation.REMOVED &&
                passesRelation(profile, selection.relationFilter) &&
                passesCapability(profile, selection) &&
                !profile.globalBlocked
        }
    }

    @JvmStatic
    fun contains(
        spec: FriendSelectionSpec?,
        userId: String?,
        config: FriendCenterConfig
    ): Boolean {
        val normalizedId = userId?.trim().orEmpty()
        if (normalizedId.isEmpty()) return false
        return normalizedId in resolveIds(spec, config)
    }

    @JvmStatic
    fun resolveCountMap(
        spec: FriendSelectionCountSpec?,
        config: FriendCenterConfig
    ): Map<String, Int> {
        val countSpec = spec ?: return emptyMap()
        val ids = resolveIds(countSpec.selection, config)
        if (ids.isEmpty()) return emptyMap()

        val groups = config.groups.associateBy { it.id }
        val result = linkedMapOf<String, Int>()
        ids.forEach { userId ->
            val userOverride = countSpec.userCountOverrides[userId]
            val groupOverride = countSpec.selection.includeGroupIds.firstNotNullOfOrNull { rawGroupId ->
                val groupId = rawGroupId.trim()
                if (groups[groupId]?.memberIds?.any { it.trim() == userId } == true) {
                    countSpec.groupCountOverrides[groupId]
                } else {
                    null
                }
            }
            val count = userOverride ?: groupOverride ?: countSpec.defaultCount
            if (count > 0) result[userId] = count
        }
        return result
    }

    @JvmStatic
    fun resolveCurrentIds(
        spec: FriendSelectionSpec?,
        ownerUserId: String = UserMap.currentUid.orEmpty()
    ): Set<String> {
        val config = FriendRepository.syncCurrentUserMap(ownerUserId)
        return resolveIds(spec, config)
    }

    private fun selectedIds(
        selection: FriendSelectionSpec,
        config: FriendCenterConfig
    ): LinkedHashSet<String> =
        if (selection.selectionScope == FriendSelectionScope.ALL_FRIENDS) {
            config.profiles.keys.mapNotNullTo(linkedSetOf()) { normalize(it) }
        } else {
            expandIds(selection.includeUserIds, selection.includeGroupIds, config)
        }

    private fun expandIds(
        userIds: Set<String>,
        groupIds: Set<String>,
        config: FriendCenterConfig
    ): LinkedHashSet<String> {
        val result = userIds.mapNotNullTo(linkedSetOf()) { normalize(it) }
        val groups = config.groups.associateBy { it.id }
        groupIds.forEach { rawGroupId ->
            val group = groups[rawGroupId.trim()] ?: return@forEach
            group.memberIds.mapNotNullTo(result) { normalize(it) }
        }
        return result
    }

    private fun passesRelation(
        profile: FriendProfile,
        filter: FriendRelationFilter
    ): Boolean =
        when (filter) {
            FriendRelationFilter.MUTUAL_ONLY -> profile.relation == FriendRelation.MUTUAL
            FriendRelationFilter.ALL_KNOWN ->
                profile.relation == FriendRelation.MUTUAL ||
                    profile.relation == FriendRelation.ONE_WAY

            FriendRelationFilter.INCLUDE_SELF ->
                profile.relation == FriendRelation.SELF ||
                    profile.relation == FriendRelation.MUTUAL ||
                    profile.relation == FriendRelation.ONE_WAY
        }

    private fun passesCapability(
        profile: FriendProfile,
        selection: FriendSelectionSpec
    ): Boolean {
        val filter = selection.capabilityFilter ?: return true
        val moduleKeys = filter.moduleKeys.mapNotNull { normalize(it) }
        if (moduleKeys.isEmpty()) return true

        return moduleKeys.all { moduleKey ->
            val state = profile.capabilities[moduleKey]?.state
            when {
                state == null -> filter.includeUnknown
                state == FriendCapabilityState.UNKNOWN -> filter.includeUnknown
                else -> state in filter.requiredStates
            }
        }
    }

    private fun normalize(value: String): String? = value.trim().takeIf { it.isNotEmpty() }
}
