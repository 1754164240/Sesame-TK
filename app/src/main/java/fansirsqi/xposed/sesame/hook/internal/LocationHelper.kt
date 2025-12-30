package fansirsqi.xposed.sesame.hook.internal

import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Log.error
import org.json.JSONObject

object LocationHelper {

    private const val TAG = "LocationInfoHelper"
    private var classLoader: ClassLoader? = null
    private var cachedLocation: JSONObject? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun interface LocationCallback {
        fun onLocationResult(location: JSONObject?)
    }

    fun init(loader: ClassLoader) {
        classLoader = loader
    }

    @JvmStatic
    fun getLocation(): JSONObject? {
        return cachedLocation
    }

    @JvmStatic
    fun requestLocation(callback: LocationCallback) {
        Thread {
            try {
                if (classLoader == null) {
                    mainHandler.post {
                        cachedLocation = JSONObject().apply {
                            put("status", "ClassLoader 未初始化")
                        }
                        callback.onLocationResult(cachedLocation)
                    }
                    return@Thread
                }

                val lnsctrUtilsClass = XposedHelpers.findClass("com.alipay.mobile.common.lnsctr.LnsctrUtils", classLoader)
                val latitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLatitude") as? Double
                val longitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLongitude") as? Double

                
                mainHandler.post {
                    cachedLocation = if (latitude != null && longitude != null) {
                        JSONObject().apply {
                            put("latitude", latitude)
                            put("longitude", longitude)
                        }
                    } else {
                        JSONObject().apply {
                            put("status", "等待支付宝初始化中...")
                        }
                    }
                    callback.onLocationResult(cachedLocation)
                }
            } catch (e: Throwable) {
                Log.error(TAG, "获取经纬度失败: ${e.message}")
                mainHandler.post {
                    cachedLocation = JSONObject().apply {
                        put("status", "获取经纬度失败: ${e.message}")
                    }
                    callback.onLocationResult(cachedLocation)
                }
            }
        }.start()
    }

}
