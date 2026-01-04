package fansirsqi.xposed.sesame.hook.internal

import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.newutil.DataStore
import org.json.JSONObject

object LocationHelper {

    private const val TAG = "LocationInfoHelper"
    private var classLoader: ClassLoader? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val LOCATION_KEY = "cached_location"

    fun interface LocationCallback {
        fun onLocationResult(location: JSONObject?)
    }

    fun init(loader: ClassLoader) {
        classLoader = loader
    }

    /**
     * 从 DataStore 获取缓存的位置信息
     * @return 位置信息 JSONObject，如果未缓存则返回 null
     */
    @JvmStatic
    fun getLocation(): JSONObject? {
        return try {
            DataStore.get<String>(LOCATION_KEY, String::class.java)?.let { jsonString ->
                JSONObject(jsonString)
            }
        } catch (e: Exception) {
            Log.error(TAG, "从 DataStore 读取位置信息失败: ${e.message}")
            null
        }
    }

    @JvmStatic
    fun requestLocation(callback: LocationCallback) {
        Thread {
            try {
                if (classLoader == null) {
                    val errorLocation = JSONObject().apply {
                        put("status", "ClassLoader 未初始化")
                    }
                    saveLocationToDataStore(errorLocation)
                    mainHandler.post {
                        callback.onLocationResult(errorLocation)
                    }
                    return@Thread
                }

                val lnsctrUtilsClass = XposedHelpers.findClass("com.alipay.mobile.common.lnsctr.LnsctrUtils", classLoader)
                val latitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLatitude") as? Double
                val longitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLongitude") as? Double

                val location = if (latitude != null && longitude != null) {
                    JSONObject().apply {
                        put("latitude", latitude)
                        put("longitude", longitude)
                    }
                } else {
                    JSONObject().apply {
                        put("status", "等待支付宝初始化中...")
                    }
                }

                saveLocationToDataStore(location)
                
                mainHandler.post {
                    callback.onLocationResult(location)
                }
            } catch (e: Throwable) {
                Log.error(TAG, "获取经纬度失败: ${e.message}")
                val errorLocation = JSONObject().apply {
                    put("status", "获取经纬度失败: ${e.message}")
                }
                saveLocationToDataStore(errorLocation)
                mainHandler.post {
                    callback.onLocationResult(errorLocation)
                }
            }
        }.start()
    }

    /**
     * 保存位置信息到 DataStore
     * @param location 要保存的位置信息 JSONObject
     */
    private fun saveLocationToDataStore(location: JSONObject) {
        try {
            DataStore.put(LOCATION_KEY, location.toString())
        } catch (e: Exception) {
            Log.error(TAG, "保存位置信息到 DataStore 失败: ${e.message}")
        }
    }

}
