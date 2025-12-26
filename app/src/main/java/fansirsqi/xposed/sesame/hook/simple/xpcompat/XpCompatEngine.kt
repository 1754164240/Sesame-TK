package fansirsqi.xposed.sesame.hook.simple.xpcompat

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

/**
 * 精简版 Xposed 兼容引擎
 */
object XpCompatEngine {
    
    /**
     * Hook 方法
     */
    fun hookMethod(method: Member, callback: XC_MethodHook) {
        XposedBridge.hookMethod(method, callback)
    }
    
    /**
     * Hook 所有构造函数
     */
    fun hookAllConstructors(hookClass: Class<*>, callback: XC_MethodHook) {
        for (constructor in hookClass.declaredConstructors) {
            hookMethod(constructor, callback)
        }
    }
}
