package fansirsqi.xposed.sesame.task.antFishPond

import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.ModelOrder
import fansirsqi.xposed.sesame.model.CustomSettings
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.task.antOcean.AntOcean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntFishPondConfigTest {

    @Test
    fun `配置默认关闭且钓鱼上限范围为零到二百`() {
        val fields = AntFishPond().fields

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
    }

    @Test
    fun `鱼池属于森林分组并注册在海洋之后`() {
        val model = AntFishPond()
        assertEquals(ModelGroup.FOREST, model.group)
        assertEquals("AntOcean.png", model.icon)

        val order = ModelOrder.allConfig
        val oceanIndex = order.indexOf(AntOcean::class.java)
        val fishPondIndex = order.indexOf(AntFishPond::class.java)
        assertTrue(oceanIndex >= 0)
        assertEquals(oceanIndex + 1, fishPondIndex)
    }

    @Test
    fun `鱼池可加入每日只运行一次筛选`() {
        assertEquals("antFishPond", CustomSettings.getModuleId("福气鱼池"))
        assertTrue(
            CustomSettings.onlyOnceDailyList.expandValue.any {
                it.id == "antFishPond" && it.name == "福气鱼池"
            }
        )
    }
}
