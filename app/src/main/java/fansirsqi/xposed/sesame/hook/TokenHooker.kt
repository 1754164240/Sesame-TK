package fansirsqi.xposed.sesame.hook

import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.IdMapManager
import fansirsqi.xposed.sesame.util.maps.VipDataIdMap
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object TokenHooker {

    private const val TAG = "TokenHooker"
    private val missingTokenLogged = AtomicBoolean(false)

    /**
     * 方法名 -> handler
     * 注意：这里不需要改，Handler 仍然只接收 JSONObject，UserId 通过闭包在 start 中传入
     */
    private val rpcHandlerMap: MutableMap<String, (JSONObject) -> Unit> = mutableMapOf()

    /**
     * 初始化监听
     * @param currentUserId 从 ApplicationHook 传入的当前用户ID
     */
    fun start(currentUserId: String) {
        if (currentUserId.isEmpty()) {
            Log.error(TAG, "❌ 启动失败：传入的 UserId 为空")
            return
        }
        // 注册蚂蚁庄园 ReferToken 抓取
        // 这里 paramsJson 是 HookUtil 传来的
        // currentUserId 是 start 方法传进来的（闭包捕获）
        registerRpcHandler("com.alipay.adexchange.ad.facade.xlightPlugin") { paramsJson ->
            handleAntFarmToken(currentUserId, paramsJson)
        }

        Log.record(TAG, "✅ VIP业务监听已启动，当前绑定用户: $currentUserId")
    }

    /** 注册 RPC 回调处理器 */
    fun registerRpcHandler(methodName: String, handler: (JSONObject) -> Unit) {
        rpcHandlerMap[methodName] = handler
    }

    /**
     * 调用 handler
     * HookUtil 调用此方法时，不需要传 userId，因为它已经被 start 方法“记住”了
     */
    fun handleRpc(method: String, paramsJson: JSONObject) {
        rpcHandlerMap[method]?.invoke(paramsJson)
    }

    /**
     * 具体业务逻辑
     */
    private fun handleAntFarmToken(userId: String, paramsJson: JSONObject) {
        try {
            val token = ReferTokenParser.parse(paramsJson)
            if (token == null) {
                if (missingTokenLogged.compareAndSet(false, true)) {
                    Log.record(TAG, "本次广告请求未携带 referToken，已跳过")
                }
                return
            }
            missingTokenLogged.set(false)

            // 保存逻辑
            val vipData = IdMapManager.getInstance(VipDataIdMap::class.java)
            vipData.load(userId)
            vipData.add("AntFarmReferToken", token)

            if (vipData.save(userId)) {
                Log.other(TAG, "🎁 捕获到蚂蚁庄园 referToken 并已保存, uid=$userId")
            } else {
                Log.error(TAG, "保存 vipdata.json 失败, uid=$userId")
            }

        } catch (e: Exception) {
            Log.error(TAG, "解析 referToken 异常: ${e.message}")
        }
    }
}
