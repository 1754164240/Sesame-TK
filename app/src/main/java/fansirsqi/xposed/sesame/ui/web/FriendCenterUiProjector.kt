package fansirsqi.xposed.sesame.ui.web

import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityState
import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.entity.friend.FriendProfile
import fansirsqi.xposed.sesame.entity.friend.FriendRelation
import fansirsqi.xposed.sesame.entity.friend.FriendRelationFilter
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionScope
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionSpec
import fansirsqi.xposed.sesame.util.friend.FriendSelectionResolver
import fansirsqi.xposed.sesame.util.friend.LegacyFriendSelectionAdapter

data class FriendCenterUiGroup(
    val id: String,
    val name: String,
    val memberCount: Int
)

data class FriendCenterUiEntry(
    val userId: String,
    val displayName: String,
    val effective: Boolean,
    val tags: List<String>
)

data class FriendCenterUiProjection(
    val selectionScope: String,
    val totalCount: Int,
    val effectiveCount: Int,
    val invalidCount: Int,
    val effectiveUserIds: List<String>,
    val groups: List<FriendCenterUiGroup>,
    val relationOptions: List<String>,
    val capabilityOptions: List<String>,
    val entries: List<FriendCenterUiEntry>
)

object FriendCenterUiProjector {

    @JvmStatic
    fun decorateListItems(
        config: FriendCenterConfig,
        items: List<SettingsListItemUiContract>
    ): List<SettingsListItemUiContract> {
        return items.map { item ->
            val profile = config.profiles[item.id]
            item.copy(
                tags = SettingsUiContract.safeTags(
                    item.tags + if (profile == null) {
                        emptyList()
                    } else {
                        profileTags(profile)
                    }
                )
            )
        }
    }

    @JvmStatic
    fun project(
        config: FriendCenterConfig,
        spec: FriendSelectionSpec
    ): FriendCenterUiProjection {
        val candidateIds = candidateIds(config, spec)
        val effectiveIds = FriendSelectionResolver.resolveIds(
            spec,
            config
        )
        val entries = candidateIds.map { userId ->
            val profile = config.profiles[userId]
            FriendCenterUiEntry(
                userId = userId,
                displayName = profile?.displayName
                    ?.trim()
                    ?.ifEmpty { userId }
                    ?: userId,
                effective = userId in effectiveIds,
                tags = SettingsUiContract.safeTags(
                    profileTags(profile)
                )
            )
        }
        val groups = config.groups.mapNotNull { group ->
            val id = group.id.trim()
            if (id.isEmpty()) {
                return@mapNotNull null
            }
            FriendCenterUiGroup(
                id = id,
                name = group.name.trim().ifEmpty { id },
                memberCount = group.memberIds
                    .map(String::trim)
                    .count(String::isNotEmpty)
            )
        }.distinctBy { it.id }
        val capabilities = config.profiles.values
            .flatMap { it.capabilities.keys }
            .map(String::trim)
            .filter { it.matches(Regex("""[A-Za-z0-9_.-]{1,48}""")) }
            .distinct()
            .sorted()
        return FriendCenterUiProjection(
            selectionScope = spec.selectionScope.name,
            totalCount = candidateIds.size,
            effectiveCount = effectiveIds.size,
            invalidCount = candidateIds.size - effectiveIds.size,
            effectiveUserIds = candidateIds.filter(effectiveIds::contains),
            groups = groups,
            relationOptions = FriendRelationFilter.entries.map { it.name },
            capabilityOptions = capabilities,
            entries = entries
        )
    }

    @JvmStatic
    fun projectLegacyIds(
        config: FriendCenterConfig,
        ids: Set<String>?
    ): FriendCenterUiProjection {
        return project(
            config,
            LegacyFriendSelectionAdapter.fromIds(ids)
        )
    }

    @JvmStatic
    fun projectLegacyCounts(
        config: FriendCenterConfig,
        counts: Map<String, Int>?
    ): FriendCenterUiProjection {
        return project(
            config,
            LegacyFriendSelectionAdapter.fromCounts(counts).selection
        )
    }

    private fun candidateIds(
        config: FriendCenterConfig,
        spec: FriendSelectionSpec
    ): List<String> {
        val result = linkedSetOf<String>()
        if (spec.selectionScope == FriendSelectionScope.ALL_FRIENDS) {
            config.profiles.keys.mapNotNullTo(result, ::normalize)
        } else {
            spec.includeUserIds.mapNotNullTo(result, ::normalize)
            val groups = config.groups.associateBy { it.id.trim() }
            spec.includeGroupIds.forEach { rawGroupId ->
                groups[rawGroupId.trim()]
                    ?.memberIds
                    ?.mapNotNullTo(result, ::normalize)
            }
        }
        return result.toList()
    }

    private fun profileTags(profile: FriendProfile?): List<String> {
        if (profile == null) {
            return listOf("不可用")
        }
        val tags = mutableListOf(
            "关系:${relationLabel(profile.relation)}"
        )
        if (profile.globalBlocked) {
            tags += "全局黑名单"
        }
        if (profile.removed || profile.relation == FriendRelation.REMOVED) {
            tags += "已移除"
        }
        profile.capabilities.forEach { (rawKey, capability) ->
            val key = rawKey.trim()
            if (!key.matches(Regex("""[A-Za-z0-9_.-]{1,48}"""))) {
                return@forEach
            }
            tags += "能力:$key=${capabilityLabel(capability.state)}"
        }
        return tags
    }

    private fun relationLabel(relation: FriendRelation): String {
        return when (relation) {
            FriendRelation.SELF -> "自己"
            FriendRelation.MUTUAL -> "互为好友"
            FriendRelation.ONE_WAY -> "单向好友"
            FriendRelation.REMOVED -> "已移除"
            FriendRelation.UNKNOWN -> "未知"
        }
    }

    private fun capabilityLabel(
        state: FriendCapabilityState
    ): String {
        return when (state) {
            FriendCapabilityState.OPEN -> "开放"
            FriendCapabilityState.NOT_OPEN -> "未开放"
            FriendCapabilityState.UNAVAILABLE -> "不可用"
            FriendCapabilityState.UNKNOWN -> "未知"
        }
    }

    private fun normalize(value: String): String? {
        return value.trim().takeIf(String::isNotEmpty)
    }
}
