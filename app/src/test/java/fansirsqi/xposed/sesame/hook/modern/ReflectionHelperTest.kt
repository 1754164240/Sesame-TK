package fansirsqi.xposed.sesame.hook.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReflectionHelperTest {

    @Test
    fun `按指定类加载器查找类`() {
        val type = ReflectionHelper.findClass(String::class.java.name, javaClass.classLoader)

        assertEquals(String::class.java, type)
    }

    @Test
    fun `调用私有继承方法并访问继承字段`() {
        val child = ChildFixture()

        val result = ReflectionHelper.callMethod(child, "format", 7)
        val value = ReflectionHelper.getObjectField(child, "value")

        assertEquals("number:7", result)
        assertEquals("base", value)
    }

    @Test
    fun `根据基本类型装箱和重载选择最具体方法`() {
        val fixture = OverloadFixture()

        assertEquals("int", ReflectionHelper.callMethod(fixture, "select", 1))
        assertEquals("string", ReflectionHelper.callMethod(fixture, "select", "x"))
        assertEquals("nullable", ReflectionHelper.callMethod(fixture, "nullable", null))
    }

    @Test
    fun `调用静态方法并创建私有构造实例`() {
        val created = ReflectionHelper.newInstance(PrivateFixture::class.java, "value")
        val staticResult = ReflectionHelper.callStaticMethod(StaticFixture::class.java, "join", "a", 2)

        assertEquals("value", ReflectionHelper.getObjectField(created, "text"))
        assertEquals("a2", staticResult)
    }

    @Test
    fun `找不到成员时抛出明确异常`() {
        assertThrows(NoSuchMethodException::class.java) {
            ReflectionHelper.callMethod(OverloadFixture(), "missing")
        }
        assertThrows(NoSuchFieldException::class.java) {
            ReflectionHelper.findField(OverloadFixture::class.java, "missing")
        }
    }

    private open class BaseFixture {
        @Suppress("unused")
        private val value = "base"

        @Suppress("unused")
        private fun format(number: Number): String = "number:$number"
    }

    private class ChildFixture : BaseFixture()

    private class OverloadFixture {
        @Suppress("unused")
        private fun select(value: Int): String = "int"

        @Suppress("unused")
        private fun select(value: String): String = "string"

        @Suppress("unused")
        private fun nullable(value: String?): String = if (value == null) "nullable" else value
    }

    private class PrivateFixture private constructor(
        @Suppress("unused") private val text: String
    )

    private object StaticFixture {
        @JvmStatic
        @Suppress("unused")
        private fun join(text: String, number: Int): String = text + number
    }
}
