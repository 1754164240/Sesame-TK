package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.friend.FriendSelectionScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFriendSelectionAdapterTest {

    @Test
    fun legacyIdsRemainExplicitAndAreNormalized() {
        val spec = LegacyFriendSelectionAdapter.fromIds(linkedSetOf(" 100 ", "", "200", "100"))

        assertEquals(FriendSelectionScope.EXPLICIT, spec.selectionScope)
        assertEquals(linkedSetOf("100", "200"), spec.includeUserIds)
    }

    @Test
    fun legacyCountsKeepPerUserValuesIncludingZero() {
        val spec = LegacyFriendSelectionAdapter.fromCounts(
            linkedMapOf(" 100 " to 3, "200" to 0, "" to 9),
            defaultCount = 2
        )

        assertEquals(FriendSelectionScope.EXPLICIT, spec.selection.selectionScope)
        assertEquals(linkedSetOf("100", "200"), spec.selection.includeUserIds)
        assertEquals(linkedMapOf("100" to 3, "200" to 0), spec.userCountOverrides)
        assertEquals(2, spec.defaultCount)
    }

    @Test
    fun nullLegacyValuesSelectNobody() {
        assertTrue(LegacyFriendSelectionAdapter.fromIds(null).includeUserIds.isEmpty())
        assertTrue(LegacyFriendSelectionAdapter.fromCounts(null).selection.includeUserIds.isEmpty())
    }
}
