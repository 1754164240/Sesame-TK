package fansirsqi.xposed.sesame.hook

import de.robv.android.xposed.XposedBridge
import fansirsqi.xposed.sesame.data.General
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper


class LspEntry(
    base: io.github.libxposed.api.XposedInterface,
    param: XposedModuleInterface.ModuleLoadedParam
) : XposedModule(base, param) {
    var customHooker: ApplicationHook? = null
    private val processName = param.processName

    object XposedInfo {
        var apiVersion: Int = 0
        var frameworkName: String = ""
        var frameworkVersion: String = ""
        var frameworkVersionCode: Long = 0
        var scope: MutableList<String?> = mutableListOf()

    }

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                XposedInfo.apiVersion = service.apiVersion
                XposedInfo.frameworkName = service.frameworkName
                XposedInfo.frameworkVersion = service.frameworkVersion
                XposedInfo.frameworkVersionCode = service.frameworkVersionCode
                XposedInfo.scope = service.scope
                XposedBridge.log("Service bound: ${service.frameworkName} ${service.frameworkVersion}")
            }

            override fun onServiceDied(service: XposedService) {
                XposedBridge.log("Service died: ${service.frameworkName}")
            }
        })

        XposedBridge.log("LspEntry: Initialized for process $processName")
        val baseFw = "${base.frameworkName} ${base.frameworkVersion}"
        val fw = "${XposedInfo.frameworkName} ${XposedInfo.frameworkVersion}"
        XposedBridge.log("Current framework: $fw, API version: ${XposedInfo.apiVersion}")
        XposedBridge.log("LspEntry: Framework from base: $baseFw ")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        customHooker = ApplicationHook()
        if (General.PACKAGE_NAME == processName) {
            customHooker?.loadPackage(param)
        } else if (General.MODULE_PACKAGE_NAME == processName) {
            customHooker?.loadModelPackage(param)
        }
    }


}