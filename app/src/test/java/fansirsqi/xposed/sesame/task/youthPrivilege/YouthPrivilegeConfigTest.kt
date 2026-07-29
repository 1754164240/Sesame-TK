package fansirsqi.xposed.sesame.task.youthPrivilege

import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.ModelOrder
import fansirsqi.xposed.sesame.task.antMember.AntMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouthPrivilegeConfigTest {

    @Test
    fun `青春特权新增动作默认全部关闭`() {
        val fields = YouthPrivilege().fields

        assertFalse(fields["youthPrivilegeCheckIn"]?.value as Boolean)
        assertFalse(fields["youthPrivilegeForestProps"]?.value as Boolean)
        assertFalse(fields["youthPrivilegeTasks"]?.value as Boolean)
    }

    @Test
    fun `青春特权属于会员分组并注册在会员之后`() {
        val model = YouthPrivilege()
        assertEquals(ModelGroup.MEMBER, model.group)
        assertEquals("AntMember.png", model.icon)

        val order = ModelOrder.allConfig
        val memberIndex = order.indexOf(AntMember::class.java)
        val youthIndex = order.indexOf(YouthPrivilege::class.java)
        assertTrue(memberIndex >= 0)
        assertEquals(memberIndex + 1, youthIndex)
    }
}
