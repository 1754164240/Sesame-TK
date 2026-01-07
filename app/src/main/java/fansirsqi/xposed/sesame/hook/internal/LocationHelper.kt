package fansirsqi.xposed.sesame.hook.internal

import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.newutil.DataStore
import fansirsqi.xposed.sesame.util.Log
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
     */
    @JvmStatic
    fun getLocation(): JSONObject? {
        return try {
            // ✅ 改动1：直接读取为 Map，而不是 String
            val map = DataStore.get(LOCATION_KEY, Map::class.java)
            if (map != null) {
                // 将 Map 转回 JSONObject
                JSONObject(map)
            } else {
                null
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
                    val errorMap = mapOf("status" to "ClassLoader 未初始化")
                    saveLocationToDataStore(errorMap)
                    mainHandler.post {
                        callback.onLocationResult(JSONObject(errorMap))
                    }
                    return@Thread
                }

                val lnsctrUtilsClass = XposedHelpers.findClass("com.alipay.mobile.common.lnsctr.LnsctrUtils", classLoader)
                val latitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLatitude") as? Double
                val longitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLongitude") as? Double

                // ✅ 改动2：构建 Map 而不是 JSONObject
                val locationMap: Map<String, Any> = if (latitude != null && longitude != null) {
                    mapOf(
                        "latitude" to latitude,
                        "longitude" to longitude
                    )
                } else {
                    mapOf("status" to "等待支付宝初始化中...")
                }

                saveLocationToDataStore(locationMap)

                mainHandler.post {
                    // 回调仍然返回 JSONObject 保持兼容性
                    callback.onLocationResult(JSONObject(locationMap))
                }
            } catch (e: Throwable) {
                Log.error(TAG, "获取经纬度失败: ${e.message}")
                val errorMap = mapOf("status" to "获取经纬度失败: ${e.message}")
                saveLocationToDataStore(errorMap)
                mainHandler.post {
                    callback.onLocationResult(JSONObject(errorMap))
                }
            }
        }.start()
    }

    /**
     * 保存位置信息到 DataStore
     * ✅ 改动3：接收 Map 类型
     */
    private fun saveLocationToDataStore(locationMap: Map<String, Any>) {
        try {
            // 直接存 Map，Jackson 会将其序列化为嵌套的 JSON 对象，不会转义
            DataStore.put(LOCATION_KEY, locationMap)
        } catch (e: Exception) {
            Log.error(TAG, "保存位置信息到 DataStore 失败: ${e.message}")
        }
    }
}