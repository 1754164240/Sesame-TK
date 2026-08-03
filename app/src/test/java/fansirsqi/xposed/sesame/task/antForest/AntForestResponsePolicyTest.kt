package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntForestResponsePolicyTest {

    @Test
    fun `倍率卡待领取能量达到阈值时应收取`() {
        assertFalse(AntForestResponsePolicy.shouldCollectRobExpandEnergy(199.0, 200, false))
        assertTrue(AntForestResponsePolicy.shouldCollectRobExpandEnergy(200.0, 200, false))
        assertTrue(AntForestResponsePolicy.shouldCollectRobExpandEnergy(201.0, 200, false))
    }

    @Test
    fun `倍率卡达到每日上限时只收取非零能量`() {
        assertTrue(AntForestResponsePolicy.shouldCollectRobExpandEnergy(1.0, 200, true))
        assertFalse(AntForestResponsePolicy.shouldCollectRobExpandEnergy(0.0, 200, true))
    }

    @Test
    fun `动物派遣能量已领取按幂等完成处理`() {
        assertTrue(AntForestResponsePolicy.isAnimalEnergyAlreadyCollected("ENERGY_HAS_COLLECTED"))
        assertFalse(AntForestResponsePolicy.isAnimalEnergyAlreadyCollected("SYSTEM_ERROR"))
        assertFalse(AntForestResponsePolicy.isAnimalEnergyAlreadyCollected(null))
    }
}
