package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.friend.FriendSelectionCountSpec
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionScope
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionSpec

object LegacyFriendSelectionAdapter {
    @JvmStatic
    fun fromIds(ids: Set<String>?): FriendSelectionSpec =
        FriendSelectionSpec(
            selectionScope = FriendSelectionScope.EXPLICIT,
            includeUserIds = ids.orEmpty().mapNotNullTo(linkedSetOf()) { normalize(it) }
        )

    @JvmStatic
    fun fromCounts(
        counts: Map<String, Int>?,
        defaultCount: Int = 1
    ): FriendSelectionCountSpec {
        val normalizedCounts = linkedMapOf<String, Int>()
        counts.orEmpty().forEach { (rawId, count) ->
            normalize(rawId)?.let { normalizedCounts[it] = count }
        }
        return FriendSelectionCountSpec(
            selection = FriendSelectionSpec(
                selectionScope = FriendSelectionScope.EXPLICIT,
                includeUserIds = LinkedHashSet(normalizedCounts.keys)
            ),
            defaultCount = defaultCount,
            userCountOverrides = normalizedCounts
        )
    }

    private fun normalize(value: String): String? = value.trim().takeIf { it.isNotEmpty() }
}
