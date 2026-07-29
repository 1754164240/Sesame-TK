package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantTaskSafetyPolicyTest {

    @Test
    fun `带广告业务标识的商家任务固定跳过`() {
        assertEquals(
            MerchantTaskDecision.SKIP_AD,
            MerchantTaskSafetyPolicy.classify(hasAdBusinessId = true)
        )
    }

    @Test
    fun `无广告业务标识的商家任务保留原有处理`() {
        assertEquals(
            MerchantTaskDecision.CONTINUE,
            MerchantTaskSafetyPolicy.classify(hasAdBusinessId = false)
        )
    }
}
