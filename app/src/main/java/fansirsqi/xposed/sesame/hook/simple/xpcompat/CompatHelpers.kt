package fansirsqi.xposed.sesame.hook.simple.xpcompat

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * 精简版 Xposed 兼容辅助类
 */
object CompatHelpers {
    
    /**
     * 查找并 Hook 方法
     */
    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any
    ) {
        if (parameterTypesAndCallback.isEmpty() || parameterTypesAndCallback.last() !is XC_MethodHook) {
            throw IllegalArgumentException("no callback defined")
        }
        
        val callback = parameterTypesAndCallback.last() as XC_MethodHook
        val paramTypes = parameterTypesAndCallback.dropLast(1).toTypedArray()
        
        val method = findMethodExact(clazz, methodName, *paramTypes)
        XpCompatEngine.hookMethod(method, callback)
    }

    /**
     * 查找并 Hook 构造函数（使用 ClassLoader）
     */
    fun findAndHookConstructor(
        className: String,
        classLoader: ClassLoader?,
        vararg parameterTypesAndCallback: Any
    ) {
        if (parameterTypesAndCallback.isEmpty() || parameterTypesAndCallback.last() !is XC_MethodHook) {
            throw IllegalArgumentException("no callback defined")
        }
        
        val callback = parameterTypesAndCallback.last() as XC_MethodHook
        val paramTypes = parameterTypesAndCallback.dropLast(1).toTypedArray()
        
        val clazz = XposedHelpers.findClass(className, classLoader)
        val constructor = findConstructorExact(clazz, classLoader, *paramTypes)
        XpCompatEngine.hookMethod(constructor, callback)
    }
    
    /**
     * 精确查找方法
     */
    private fun findMethodExact(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Any
    ): Method {
        return findMethodExact(clazz, null, methodName, *parameterTypes)
    }
    
    /**
     * 精确查找方法（带 ClassLoader）
     */
    private fun findMethodExact(
        clazz: Class<*>,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypes: Any
    ): Method {
        val paramClasses = parameterTypes.map { param ->
            when (param) {
                is Class<*> -> param
                is String -> {
                    if (param.contains("$")) {
                        val parts = param.split("$")
                        val outerClass = if (classLoader != null) {
                            XposedHelpers.findClass(parts[0], classLoader)
                        } else {
                            Class.forName(parts[0])
                        }
                        val innerClassName = parts[1]
                        outerClass.declaredClasses.find { it.simpleName == innerClassName }
                            ?: throw ClassNotFoundException("Inner class $innerClassName not found in ${parts[0]}")
                    } else {
                        if (classLoader != null) {
                            XposedHelpers.findClass(param, classLoader)
                        } else {
                            Class.forName(param)
                        }
                    }
                }
                else -> param.javaClass
            }
        }.toTypedArray()
        
        return XposedHelpers.findMethodExact(clazz, methodName, *paramClasses)
    }
    
    /**
     * 精确查找构造函数（带 ClassLoader）
     */
    private fun findConstructorExact(
        clazz: Class<*>,
        classLoader: ClassLoader?,
        vararg parameterTypes: Any
    ): java.lang.reflect.Constructor<*> {
        val paramClasses = parameterTypes.map { param ->
            when (param) {
                is Class<*> -> param
                is String -> {
                    if (param.contains("$")) {
                        val parts = param.split("$")
                        val outerClass = if (classLoader != null) {
                            XposedHelpers.findClass(parts[0], classLoader)
                        } else {
                            Class.forName(parts[0])
                        }
                        val innerClassName = parts[1]
                        outerClass.declaredClasses.find { it.simpleName == innerClassName }
                            ?: throw ClassNotFoundException("Inner class $innerClassName not found in ${parts[0]}")
                    } else {
                        if (classLoader != null) {
                            XposedHelpers.findClass(param, classLoader)
                        } else {
                            Class.forName(param)
                        }
                    }
                }
                else -> param.javaClass
            }
        }.toTypedArray()
        
        return XposedHelpers.findConstructorExact(clazz, *paramClasses)
    }

}
