package fansirsqi.xposed.sesame.data

import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FishPondConfigMigrationTest {

    @Test
    fun `旧鱼池配置迁移到农场且不覆盖新值`() {
        val legacy = ModelFields().apply {
            addField(BooleanModelField("fishPondTask", "旧任务", true))
            addField(BooleanModelField("autoFish", "旧钓鱼", true))
            addField(IntegerModelField("fishDailyLimit", "旧上限", 88, 0, 200))
        }
        val orchard = ModelFields().apply {
            addField(BooleanModelField("autoFish", "新钓鱼", false))
        }
        val source = hashMapOf(
            "AntFishPond" to legacy,
            "AntOrchard" to orchard
        )

        val migrated = Config.migrateLegacyFishPondFields(source)
        val migratedOrchard = migrated.getValue("AntOrchard")

        assertTrue(migratedOrchard["fishPondTask"]?.value as Boolean)
        assertFalse(migratedOrchard["autoFish"]?.value as Boolean)
        assertEquals(88, migratedOrchard["fishDailyLimit"]?.value)
        assertNotSame(source, migrated)
        assertFalse(orchard.containsKey("fishPondTask"))
    }
}
