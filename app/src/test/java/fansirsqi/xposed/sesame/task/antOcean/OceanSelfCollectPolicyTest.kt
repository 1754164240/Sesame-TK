package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OceanSelfCollectPolicyTest {
    @Test
    fun `fullEnergy 优先于旧 energy 字段`() {
        val bubble = JSONObject()
            .put("fullEnergy", 18)
            .put("energy", 99)

        assertEquals(18, OceanSelfCollectPolicy.energyOf(bubble))
    }

    @Test
    fun `fullEnergy 无效时回退到 energy`() {
        val bubble = JSONObject()
            .put("fullEnergy", "unknown")
            .put("energy", "12")

        assertEquals(12, OceanSelfCollectPolicy.energyOf(bubble))
        assertNull(OceanSelfCollectPolicy.energyOf(JSONObject()))
    }

    @Test
    fun `只收取达到独立阈值的可用海洋能量`() {
        val bubble = JSONObject()
            .put("channel", "ocean")
            .put("collectStatus", "AVAILABLE")
            .put("fullEnergy", 10)

        assertTrue(OceanSelfCollectPolicy.shouldCollect(bubble, 10))
        assertFalse(OceanSelfCollectPolicy.shouldCollect(bubble, 11))
        assertFalse(
            OceanSelfCollectPolicy.shouldCollect(
                JSONObject(bubble.toString()).put("channel", "forest"),
                0
            )
        )
        assertFalse(
            OceanSelfCollectPolicy.shouldCollect(
                JSONObject(bubble.toString()).put("collectStatus", "WAITING"),
                0
            )
        )
        assertFalse(
            OceanSelfCollectPolicy.shouldCollect(
                JSONObject().put("channel", "ocean").put("collectStatus", "AVAILABLE"),
                0
            )
        )
    }
}
