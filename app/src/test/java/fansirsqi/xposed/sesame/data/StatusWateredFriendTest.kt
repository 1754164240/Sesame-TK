package fansirsqi.xposed.sesame.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusWateredFriendTest {

    @Test
    fun `被浇水计数与主动浇水计数互不污染`() {
        val status = Status().apply {
            waterFriendLogList["owner-1-friend-1"] = 4
        }

        val changed = Status.incrementWateredFriend(
            status = status,
            ownerId = "owner-1",
            friendId = "friend-1"
        )

        assertTrue(changed)
        assertEquals(4, status.waterFriendLogList["owner-1-friend-1"])
        assertEquals(1, status.wateredFriendLogList["owner-1-friend-1"])
    }

    @Test
    fun `空用户或空好友不写被浇水计数`() {
        val status = Status()

        assertFalse(Status.incrementWateredFriend(status, null, "friend-1"))
        assertFalse(Status.incrementWateredFriend(status, "owner-1", ""))
        assertTrue(status.wateredFriendLogList.isEmpty())
    }
}
