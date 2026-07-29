package fansirsqi.xposed.sesame.entity.friend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendCenterEntitiesTest {

    @Test
    fun unknownScopeFallsBackToExplicit() {
        assertEquals(FriendSelectionScope.EXPLICIT, FriendSelectionScope.fromJson("FUTURE_SCOPE"))
        assertEquals(FriendSelectionScope.EXPLICIT, FriendSelectionScope.fromJson(""))
        assertEquals(FriendSelectionScope.EXPLICIT, FriendSelectionScope.fromJson(null))
        assertEquals(FriendSelectionScope.ALL_FRIENDS, FriendSelectionScope.fromJson("all_friends"))
    }

    @Test
    fun selectionDefaultsDoNotSelectAnyone() {
        val spec = FriendSelectionSpec()

        assertEquals(FriendSelectionScope.EXPLICIT, spec.selectionScope)
        assertEquals(FriendRelationFilter.MUTUAL_ONLY, spec.relationFilter)
        assertTrue(spec.includeUserIds.isEmpty())
        assertTrue(spec.includeGroupIds.isEmpty())
        assertTrue(spec.excludeUserIds.isEmpty())
        assertTrue(spec.excludeGroupIds.isEmpty())
    }
}
