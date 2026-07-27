package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntForestResponsePolicyTest {

    @Test
    fun `动物派遣能量已领取按幂等完成处理`() {
        assertTrue(AntForestResponsePolicy.isAnimalEnergyAlreadyCollected("ENERGY_HAS_COLLECTED"))
        assertFalse(AntForestResponsePolicy.isAnimalEnergyAlreadyCollected("SYSTEM_ERROR"))
        assertFalse(AntForestResponsePolicy.isAnimalEnergyAlreadyCollected(null))
    }
}
