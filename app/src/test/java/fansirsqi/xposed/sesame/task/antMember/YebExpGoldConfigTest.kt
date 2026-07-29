package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class YebExpGoldConfigTest {

    @Test
    fun `余额宝体验金开关存在且默认关闭`() {
        val field = AntMember().fields["yebExpGold"]

        assertNotNull(field)
        assertFalse(field?.value as Boolean)
    }
}
