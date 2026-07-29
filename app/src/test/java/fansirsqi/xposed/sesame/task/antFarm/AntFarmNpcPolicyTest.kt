package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntFarmNpcPolicyTest {

    @Test
    fun `解析无NPC和目标NPC快照`() {
        val empty = AntFarmNpcPolicy.parseSnapshot(
            farmResponse()
        )
        val target = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(npc("target", reward = 88.0))
        )

        assertTrue(empty.recognized)
        assertNull(empty.npc)
        assertTrue(target.recognized)
        assertEquals("target", target.npc?.animalId)
        assertEquals(88.0, target.npc?.reward ?: 0.0, 0.001)
    }

    @Test
    fun `未知容器不形成可执行快照`() {
        val snapshot = AntFarmNpcPolicy.parseSnapshot(
            """{"success":true,"data":{}}"""
        )

        assertFalse(snapshot.recognized)
        assertNull(snapshot.npc)
    }

    @Test
    fun `缺少身份的NPC不能解释为明确无NPC`() {
        val snapshot = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(
                """
                    {
                      "subAnimalType":"NPC",
                      "currentFarmId":"farm",
                      "masterFarmId":"master"
                    }
                """.trimIndent()
            )
        )

        assertFalse(snapshot.recognized)
        assertNull(snapshot.npc)
    }

    @Test
    fun `非对象动物条目不能形成可执行快照`() {
        val snapshot = AntFarmNpcPolicy.parseSnapshot(
            """{"success":true,"subFarmVO":{"farmId":"farm","animals":[1]}}"""
        )

        assertFalse(snapshot.recognized)
        assertNull(snapshot.npc)
    }

    @Test
    fun `奖励阈值只允许尝试领取`() {
        val below = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(npc("target", reward = 87.0))
        )
        val threshold = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(npc("target", reward = 88.0))
        )
        val serverFull = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(npc("target", reward = 1.0, limitReached = true))
        )

        assertFalse(AntFarmNpcPolicy.shouldClaimReward(below, "target", 88.0))
        assertTrue(AntFarmNpcPolicy.shouldClaimReward(threshold, "target", 88.0))
        assertTrue(AntFarmNpcPolicy.shouldClaimReward(serverFull, "target", null))
    }

    @Test
    fun `领取必须由奖励下降或NPC消失确认`() {
        val before = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(npc("target", reward = 88.0))
        )
        val unchanged = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(npc("target", reward = 88.0))
        )
        val decreased = AntFarmNpcPolicy.parseSnapshot(
            farmResponse(npc("target", reward = 0.0))
        )
        val removed = AntFarmNpcPolicy.parseSnapshot(farmResponse())

        assertFalse(AntFarmNpcPolicy.isRewardClaimConfirmed(before, unchanged))
        assertTrue(AntFarmNpcPolicy.isRewardClaimConfirmed(before, decreased))
        assertTrue(AntFarmNpcPolicy.isRewardClaimConfirmed(before, removed))
    }

    private fun farmResponse(animal: String? = null): String {
        val animals = animal?.let { "[$it]" } ?: "[]"
        return """{"success":true,"subFarmVO":{"farmId":"farm","animals":$animals}}"""
    }

    private fun npc(
        animalId: String,
        reward: Double,
        limitReached: Boolean = false
    ): String {
        return """
            {
              "animalId":"$animalId",
              "subAnimalType":"NPC",
              "currentFarmId":"farm",
              "masterFarmId":"master",
              "npcBizReward":$reward,
              "reachNpcBizRewardLimit":$limitReached
            }
        """.trimIndent()
    }
}
