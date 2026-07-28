package fansirsqi.xposed.sesame.hook.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInvocationTest {

    private val executable = Fixture::class.java.getDeclaredMethod(
        "sum",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType
    )

    @Test
    fun `前置回调可以修改原调用参数`() {
        val invocation = HookInvocation(executable, Fixture(), arrayOf(1, 2)) { args ->
            (args[0] as Int) + (args[1] as Int)
        }

        val result = invocation.execute(
            before = { it.args[0] = 4 },
            after = {}
        )

        assertEquals(6, result)
        assertFalse(invocation.isReturnEarly)
    }

    @Test
    fun `前置回调设置结果会跳过原调用`() {
        var proceeded = false
        val invocation = HookInvocation(executable, Fixture(), arrayOf(1, 2)) {
            proceeded = true
            3
        }

        val result = invocation.execute(
            before = { it.result = 9 },
            after = {}
        )

        assertEquals(9, result)
        assertTrue(invocation.isReturnEarly)
        assertFalse(proceeded)
    }

    @Test
    fun `后置回调可以覆盖原调用结果`() {
        val invocation = HookInvocation(executable, Fixture(), arrayOf(1, 2)) { 3 }

        val result = invocation.execute(
            before = {},
            after = { it.result = 7 }
        )

        assertEquals(7, result)
    }

    @Test
    fun `原调用异常会在后置回调之后继续抛出`() {
        val failure = IllegalStateException("boom")
        var observed: Throwable? = null
        val invocation = HookInvocation(executable, Fixture(), arrayOf(1, 2)) { throw failure }

        val thrown = runCatching {
            invocation.execute(
                before = {},
                after = { observed = it.throwable }
            )
        }.exceptionOrNull()

        assertSame(failure, observed)
        assertSame(failure, thrown)
    }

    @Test
    fun `前置回调异常时使用原始参数继续调用`() {
        val invocation = HookInvocation(executable, Fixture(), arrayOf(1, 2)) { args ->
            (args[0] as Int) + (args[1] as Int)
        }

        val result = invocation.execute(
            before = {
                it.args[0] = 10
                throw IllegalArgumentException("callback")
            },
            after = {}
        )

        assertEquals(3, result)
    }

    @Test
    fun `后置回调异常时保留原调用结果`() {
        val invocation = HookInvocation(executable, Fixture(), arrayOf(1, 2)) { 3 }

        val result = invocation.execute(
            before = {},
            after = {
                it.result = 8
                throw IllegalArgumentException("callback")
            }
        )

        assertEquals(3, result)
    }

    private class Fixture {
        @Suppress("unused")
        fun sum(left: Int, right: Int): Int = left + right
    }
}
