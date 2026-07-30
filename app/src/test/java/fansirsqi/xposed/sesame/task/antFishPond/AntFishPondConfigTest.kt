package fansirsqi.xposed.sesame.task.antFishPond

import fansirsqi.xposed.sesame.model.CustomSettings
import fansirsqi.xposed.sesame.model.ModelOrder
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.task.antOrchard.AntOrchard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AntFishPondConfigTest {

    @Test
    fun `福气鱼池设置直接位于农场且没有独立模块`() {
        val fields = AntOrchard().fields

        assertFalse(fields["goldenBeanTreasure"]?.value as Boolean)
        assertFalse(fields["fishPondTask"]?.value as Boolean)
        assertFalse(fields["autoFish"]?.value as Boolean)
        val limit = fields["fishDailyLimit"] as IntegerModelField
        assertEquals(30, limit.value)
        assertEquals(0, limit.minLimit)
        assertEquals(200, limit.maxLimit)

        limit.setConfigValue("-1")
        assertEquals(0, limit.value)
        limit.setConfigValue("201")
        assertEquals(200, limit.value)

        assertFalse(
            ModelOrder.allConfig.map { it.simpleName }.contains("AntFishPond")
        )
        assertFalse(
            CustomSettings.onlyOnceDailyList.expandValue.any {
                it.id == "antFishPond"
            }
        )
    }

    @Test
    fun `历史鱼池任务标识归入农场每日单次运行`() {
        assertEquals("antOrchard", CustomSettings.getModuleId("福气鱼池"))
        assertEquals("antOrchard", CustomSettings.getModuleId("antFishPond"))
    }
}
