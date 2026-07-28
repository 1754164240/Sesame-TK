package fansirsqi.xposed.sesame.hook.modern

import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object ReflectionHelper {
    private val primitiveWrappers = mapOf(
        Boolean::class.javaPrimitiveType to Boolean::class.javaObjectType,
        Byte::class.javaPrimitiveType to Byte::class.javaObjectType,
        Char::class.javaPrimitiveType to Char::class.javaObjectType,
        Short::class.javaPrimitiveType to Short::class.javaObjectType,
        Int::class.javaPrimitiveType to Int::class.javaObjectType,
        Long::class.javaPrimitiveType to Long::class.javaObjectType,
        Float::class.javaPrimitiveType to Float::class.javaObjectType,
        Double::class.javaPrimitiveType to Double::class.javaObjectType,
        Void.TYPE to Void::class.java
    )

    @JvmStatic
    fun findClass(className: String, classLoader: ClassLoader?): Class<*> =
        Class.forName(className, false, classLoader)

    @JvmStatic
    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>?): Method {
        val exactTypes = parameterTypes.map { requireNotNull(it) }.toTypedArray()
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, *exactTypes).accessible()
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        throw NoSuchMethodException("${clazz.name}#$methodName(${exactTypes.joinToString { it.name }})")
    }

    @JvmStatic
    fun findConstructorExact(clazz: Class<*>, vararg parameterTypes: Class<*>?): Constructor<*> =
        clazz.getDeclaredConstructor(*parameterTypes.map { requireNotNull(it) }.toTypedArray()).accessible()

    @JvmStatic
    fun findField(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName).accessible()
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("${clazz.name}#$fieldName")
    }

    @JvmStatic
    fun getObjectField(instance: Any?, fieldName: String): Any? {
        val receiver = requireNotNull(instance) { "字段接收对象不能为空" }
        return findField(receiver.javaClass, fieldName).get(receiver)
    }

    @JvmStatic
    fun callMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
        val receiver = requireNotNull(instance) { "方法接收对象不能为空" }
        val method = findBestMethod(receiver.javaClass, methodName, false, args)
        return invoke(method, receiver, args)
    }

    @JvmStatic
    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        val method = findBestMethod(clazz, methodName, true, args)
        return invoke(method, null, args)
    }

    @JvmStatic
    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val constructor = findBestExecutable(clazz.declaredConstructors.toList(), args)
            ?: throw NoSuchMethodException("${clazz.name}(${argumentTypeNames(args)})")
        constructor.accessible()
        return try {
            constructor.newInstance(*prepareArguments(constructor, args))
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }

    private fun findBestMethod(
        clazz: Class<*>,
        methodName: String,
        requireStatic: Boolean,
        args: Array<out Any?>
    ): Method {
        val candidates = buildList {
            var current: Class<*>? = clazz
            while (current != null) {
                addAll(current.declaredMethods)
                current = current.superclass
            }
            addAll(clazz.methods)
        }.distinctBy { methodSignature(it) }
            .filter { it.name == methodName && Modifier.isStatic(it.modifiers) == requireStatic }

        return findBestExecutable(candidates, args)?.accessible()
            ?: throw NoSuchMethodException("${clazz.name}#$methodName(${argumentTypeNames(args)})")
    }

    private fun <T : Executable> findBestExecutable(
        candidates: List<T>,
        args: Array<out Any?>
    ): T? = candidates
        .mapNotNull { executable -> matchScore(executable, args)?.let { executable to it } }
        .minWithOrNull(compareBy<Pair<T, Int>> { it.second }.thenByDescending { inheritanceDepth(it.first.declaringClass) })
        ?.first

    private fun matchScore(executable: Executable, args: Array<out Any?>): Int? {
        val parameterTypes = executable.parameterTypes
        if (!executable.isVarArgs && parameterTypes.size != args.size) return null
        if (executable.isVarArgs && args.size < parameterTypes.size - 1) return null

        var score = 0
        val fixedCount = if (executable.isVarArgs) parameterTypes.size - 1 else parameterTypes.size
        for (index in 0 until fixedCount) {
            score += parameterScore(parameterTypes[index], args[index]) ?: return null
        }

        if (executable.isVarArgs) {
            val arrayType = parameterTypes.last()
            if (args.size == parameterTypes.size && parameterScore(arrayType, args.last()) != null) {
                score += parameterScore(arrayType, args.last())!!
            } else {
                val componentType = requireNotNull(arrayType.componentType)
                for (index in fixedCount until args.size) {
                    score += (parameterScore(componentType, args[index]) ?: return null) + 4
                }
            }
        }
        return score
    }

    private fun parameterScore(parameterType: Class<*>, value: Any?): Int? {
        if (value == null) return if (parameterType.isPrimitive) null else 20 - inheritanceDepth(parameterType)
        val wrappedParameter = wrap(parameterType)
        val valueType = value.javaClass
        if (wrappedParameter == valueType) return if (parameterType.isPrimitive) 1 else 0
        if (!wrappedParameter.isAssignableFrom(valueType)) return null
        return 2 + typeDistance(valueType, wrappedParameter)
    }

    private fun typeDistance(source: Class<*>, target: Class<*>): Int {
        if (source == target) return 0
        if (target.isInterface && source.interfaces.any { target.isAssignableFrom(it) }) return 1
        var distance = 0
        var current: Class<*>? = source
        while (current != null && current != target) {
            distance++
            current = current.superclass
        }
        return distance
    }

    private fun prepareArguments(executable: Executable, args: Array<out Any?>): Array<Any?> {
        if (!executable.isVarArgs) return Array(args.size) { args[it] }
        val parameterTypes = executable.parameterTypes
        if (args.size == parameterTypes.size && parameterTypes.last().isInstance(args.last())) {
            return Array(args.size) { args[it] }
        }

        val fixedCount = parameterTypes.size - 1
        val prepared = arrayOfNulls<Any?>(parameterTypes.size)
        for (index in 0 until fixedCount) prepared[index] = args[index]
        val componentType = requireNotNull(parameterTypes.last().componentType)
        val varargArray = ReflectArray.newInstance(componentType, args.size - fixedCount)
        for (index in fixedCount until args.size) {
            ReflectArray.set(varargArray, index - fixedCount, args[index])
        }
        prepared[fixedCount] = varargArray
        return prepared
    }

    private fun invoke(method: Method, receiver: Any?, args: Array<out Any?>): Any? {
        method.accessible()
        return try {
            method.invoke(receiver, *prepareArguments(method, args))
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }

    private fun wrap(type: Class<*>): Class<*> = primitiveWrappers[type] ?: type

    private fun inheritanceDepth(type: Class<*>): Int {
        var depth = 0
        var current: Class<*>? = type
        while (current != null) {
            depth++
            current = current.superclass
        }
        return depth
    }

    private fun methodSignature(method: Method): String =
        method.name + method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name }

    private fun argumentTypeNames(args: Array<out Any?>): String =
        args.joinToString { it?.javaClass?.name ?: "null" }

    @Suppress("DEPRECATION")
    private fun <T : Executable> T.accessible(): T = apply { isAccessible = true }

    @Suppress("DEPRECATION")
    private fun Field.accessible(): Field = apply { isAccessible = true }
}
