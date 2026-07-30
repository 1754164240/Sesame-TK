package fansirsqi.xposed.sesame.task.antOcean

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntOceanAiFishConfigTest {

    @Test
    fun `AI摸鱼开关属于神奇海洋且默认关闭`() {
        val fields = requireNotNull(AntOcean().fields)

        assertTrue(fields.containsKey("aiFish"))
        assertFalse(fields["aiFish"]?.value as Boolean)
    }
}
