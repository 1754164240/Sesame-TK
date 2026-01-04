package fansirsqi.xposed.sesame.hook

import android.Manifest
import androidx.annotation.RequiresPermission
import fansirsqi.xposed.sesame.entity.RpcEntity
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.NetworkUtils

/**
 * @author Byseven
 * @date 2025/1/6
 * @apiNote
 */
object RequestManager {
    private fun checkResult(result: String?, method: String?): String {
        // 处理 null 返回值，避免 NullPointerException
        if (result == null) {
            Log.record("RequestManager", "RPC 返回 null: $method")
            return ""
        }
        if (result.isBlank() || result.isEmpty() || result.trim { it <= ' ' }.isEmpty()) {
            Log.record("RequestManager", "RPC 响应为空: $method")
            return ""
        }
        return result
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getRpcBridge(): RpcBridge? {
        if (!NetworkUtils.isNetworkAvailable()) {
            Log.record("网络未连接，等待5秒")
            fansirsqi.xposed.sesame.util.CoroutineUtils.sleepCompat(5000)
            if (!NetworkUtils.isNetworkAvailable()) {
                val networkType = NetworkUtils.getNetworkType()
                Log.record("网络仍未连接，当前网络类型: $networkType，放弃本次请求...")
                return null
            }
        }
        var rpcBridge = ApplicationHook.rpcBridge
        if (rpcBridge == null) {
            Log.record("ApplicationHook.rpcBridge 为空，等待5秒")
            fansirsqi.xposed.sesame.util.CoroutineUtils.sleepCompat(5000)
            rpcBridge = ApplicationHook.rpcBridge
        }
        return rpcBridge
    }

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity): String {
        val rpcBridge = getRpcBridge() ?: return ""
        val result = rpcBridge.requestString(rpcEntity, 3, 1200)
        return checkResult(result, rpcEntity.methodName)
    }

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): String {
        val rpcBridge = getRpcBridge() ?: return ""
        val result = rpcBridge.requestString(rpcEntity, tryCount, retryInterval)
        return checkResult(result, rpcEntity.methodName)
    }

    @JvmStatic
    fun requestString(method: String?, data: String?): String {
        val rpcBridge = getRpcBridge() ?: return ""
        val result = rpcBridge.requestString(method, data)
        return checkResult(result, method)
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, relation: String?): String {
        val rpcBridge = getRpcBridge() ?: return ""
        val result = rpcBridge.requestString(method, data, relation)
        return checkResult(result, method)
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, appName: String?, methodName: String?, facadeName: String?): String {
        val rpcBridge = getRpcBridge() ?: return ""
        val result = rpcBridge.requestString(method, data, appName, methodName, facadeName)
        return checkResult(result, method)
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, tryCount: Int, retryInterval: Int): String {
        val rpcBridge = getRpcBridge() ?: return ""
        val result = rpcBridge.requestString(method, data, tryCount, retryInterval)
        return checkResult(result, method)
    }

    fun requestString(method: String?, data: String?, relation: String?, tryCount: Int, retryInterval: Int): String {
        val rpcBridge = getRpcBridge() ?: return ""
        val result = rpcBridge.requestString(method, data, relation, tryCount, retryInterval)
        return checkResult(result, method)
    }

    @JvmStatic
    fun requestObject(rpcEntity: RpcEntity?, tryCount: Int, retryInterval: Int) {
        val rpcBridge = getRpcBridge() ?: return
        rpcBridge.requestObject(rpcEntity, tryCount, retryInterval)
    }

}
