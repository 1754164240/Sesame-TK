package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import org.junit.Assert.assertEquals
import org.junit.Test

class ForestMultiplierConfigTest {

    @Test
    fun `收好友倍卡默认关闭且替换阈值默认零`() {
        val fields = AntForest().fields

        assertEquals(AntForest.ApplyPropType.CLOSE, fields["robExpandCard"]?.value)
        val replaceDays =
            fields["robExpandCardReplaceRemainDays"] as IntegerModelField
        assertEquals(0, replaceDays.value)
        assertEquals(0, replaceDays.minLimit)
        assertEquals(365, replaceDays.maxLimit)
    }
}
