package fansirsqi.xposed.sesame.hook.modern

import android.util.Log
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.XposedEnv
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class HookEntry : XposedModule() {
    private val tag = "LibxposedEntry"
    private val applicationHook = ApplicationHook()
    private lateinit var processName: String

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        ModernXposedRuntime.initialize(this)
        processName = param.processName
        applicationHook.xposedInterface = this
        log(Log.INFO, tag, "模块已加载到进程 $processName")
        log(Log.INFO, tag, "框架: $frameworkName $frameworkVersion ($frameworkVersionCode)")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            if (General.PACKAGE_NAME != param.packageName) return
            XposedEnv.classLoader = param.classLoader
            XposedEnv.appInfo = param.applicationInfo
            XposedEnv.packageName = param.packageName
            XposedEnv.processName = processName
            applicationHook.loadPackage(param)
            log(Log.INFO, tag, "已注入 ${param.packageName}，进程 $processName")
        } catch (throwable: Throwable) {
            log(Log.ERROR, tag, "注入 ${param.packageName} 失败", throwable)
        }
    }
}
