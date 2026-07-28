package fansirsqi.xposed.sesame.hook.modern

import java.lang.reflect.Executable

class HookInvocation(
    val executable: Executable,
    val thisObject: Any?,
    initialArgs: Array<Any?>,
    private val proceedCall: (Array<Any?>) -> Any?
) {
    val args: Array<Any?> = initialArgs.copyOf()

    private var resultValue: Any? = null
    private var throwableValue: Throwable? = null

    var result: Any?
        get() = resultValue
        set(value) {
            resultValue = value
            throwableValue = null
            isReturnEarly = true
        }

    var throwable: Throwable?
        get() = throwableValue
        set(value) {
            throwableValue = value
            if (value != null) {
                resultValue = null
                isReturnEarly = true
            }
        }

    var isReturnEarly: Boolean = false
        private set

    fun execute(
        before: (HookInvocation) -> Unit,
        after: (HookInvocation) -> Unit
    ): Any? {
        val originalArgs = args.copyOf()
        try {
            before(this)
        } catch (_: Throwable) {
            return proceedCall(originalArgs)
        }

        if (!isReturnEarly) {
            captureProceedResult(args)
        }

        val savedResult = resultValue
        val savedThrowable = throwableValue
        val savedReturnEarly = isReturnEarly
        try {
            after(this)
        } catch (_: Throwable) {
            resultValue = savedResult
            throwableValue = savedThrowable
            isReturnEarly = savedReturnEarly
        }

        throwableValue?.let { throw it }
        return resultValue
    }

    private fun captureProceedResult(callArgs: Array<Any?>) {
        try {
            resultValue = proceedCall(callArgs)
            throwableValue = null
        } catch (throwable: Throwable) {
            resultValue = null
            throwableValue = throwable
        }
    }
}
