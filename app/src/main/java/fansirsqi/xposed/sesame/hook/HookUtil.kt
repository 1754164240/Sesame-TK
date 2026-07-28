package fansirsqi.xposed.sesame.hook

import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.hook.modern.ModernXposedRuntime
import fansirsqi.xposed.sesame.hook.modern.ReflectionHelper
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap


object HookUtil {
    private const val TAG = "HookUtil"

    val rpcHookMap = ConcurrentHashMap<Any, Array<Any?>>()

    private var lastToastTime = 0L

    private var microContextCache: Any? = null

    /**
     * Hook RpcBridgeExtension.rpc 方法，记录请求信息
     */
    fun hookRpcBridgeExtension(classLoader: ClassLoader, isdebug: Boolean, debugUrl: String) {
        try {
            val className = "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension"
            val jsonClassName = General.JSON_OBJECT_NAME // 替换为你项目中的实际 JSON 类名

            val jsonClass = Class.forName(jsonClassName, false, classLoader)
            val appClass = ReflectionHelper.findClass("com.alibaba.ariver.app.api.App", classLoader)
            val pageClass = ReflectionHelper.findClass("com.alibaba.ariver.app.api.Page", classLoader)
            val apiContextClass = ReflectionHelper.findClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext", classLoader)
            val bridgeCallbackClass = ReflectionHelper.findClass("com.alibaba.ariver.engine.api.bridge.extension.BridgeCallback", classLoader)
            val rpcClass = ReflectionHelper.findClass(className, classLoader)
            val rpcMethod = ReflectionHelper.findMethodExact(
                rpcClass,
                "rpc",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                jsonClass,
                String::class.java,
                jsonClass,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                appClass,
                pageClass,
                apiContextClass,
                bridgeCallbackClass
            )
            ModernXposedRuntime.hook(
                rpcMethod,
                before = { invocation ->
                        val args = invocation.args
                        if (args.size > 15) {// 参数校验
                            // 1. 获取方法名
                            val methodName = args[0] as? String ?: return@hook
                            // 2. 获取参数 (这是一个反射得到的 com.alibaba.fastjson.JSONObject 对象)
                            val rawParams = args[4]

                            // 3. 这里的 rawParams 是阿里内部的 JSON 对象，不是 org.json.JSONObject
                            // 需要转换一下。最稳妥的方法是 toString() 然后再转 org.json.JSONObject
                            if (rawParams != null) {
                                val jsonString = rawParams.toString()
                                val jsonObject = JSONObject(jsonString)
                                // ✅✅✅ 关键：把拦截到的数据扔给 VIPHook 进行分发
                                TokenHooker.handleRpc(methodName, jsonObject)
                            }

                            val callback = args[15]
                            val recordArray = arrayOfNulls<Any>(4).apply {
                                this[0] = System.currentTimeMillis()
                                this[1] = args[0] ?: "null" // method name
                                this[2] = args[4] ?: "null" // params
                            }
                            callback?.let { rpcHookMap[it] = recordArray }
                        }
                },
                after = { invocation ->
                        val args = invocation.args
                        if (args.size > 15) {
                            val callback = args[15]
                            val recordArray = rpcHookMap.remove(callback)
                            recordArray?.let {
                                try {
                                    val time = it[0]
                                    val method = it.getOrNull(1)
                                    val params = it.getOrNull(2)
                                    val data = it.getOrNull(3)

                                    val dataIsNullValue: Boolean = data == null
                                    if (!dataIsNullValue) {

                                        val res = JSONObject().apply {
                                            put("TimeStamp", time)
                                            put("Method", method)
                                            put("Params", params)
                                            put("Data", data)
                                        }

                                        val prettyRecord = """
{
"TimeStamp": $time,
"Method": "$method",
"Params": $params,
"Data": $data
}
""".trimIndent()

                                        if (isdebug) {
                                            HookSender.sendHookData(res, debugUrl)
                                        }
                                        Log.capture(prettyRecord)
                                    }
                                } catch (e: Exception) {
                                    Log.record(TAG, "JSON 构建失败: ${e.message}")
                                }
                            }
                        }
                }
            )
            Log.record(TAG, "Hook RpcBridgeExtension#rpc 成功")
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "Hook RpcBridgeExtension#rpc 失败", t)
        }
    }

    fun hookOtherService(classLoader: ClassLoader) {
        try {
            //hook 服务不在后台
            val monitorClass = ReflectionHelper.findClass(
                "com.alipay.mobile.common.fgbg.FgBgMonitorImpl",
                classLoader
            )
            ModernXposedRuntime.replaceWithConstant(
                ReflectionHelper.findMethodExact(monitorClass, "isInBackground"),
                false
            )
            ModernXposedRuntime.replaceWithConstant(
                ReflectionHelper.findMethodExact(
                    monitorClass,
                    "isInBackground",
                    Boolean::class.javaPrimitiveType!!
                ),
                false
            )
            ModernXposedRuntime.replaceWithConstant(
                ReflectionHelper.findMethodExact(monitorClass, "isInBackgroundV2"),
                false
            )
            //hook 服务在前台
            val miscUtilsClass = ReflectionHelper.findClass(
                "com.alipay.mobile.common.transport.utils.MiscUtils",
                classLoader
            )
            ModernXposedRuntime.replaceWithConstant(
                ReflectionHelper.findMethodExact(
                    miscUtilsClass,
                    "isAtFrontDesk",
                    classLoader.loadClass("android.content.Context")
                ),
                true
            )
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "hookOtherService 失败", e)
        }
    }

    /**
     * Hook DefaultBridgeCallback.sendJSONResponse 方法，记录响应内容
     */
    fun hookDefaultBridgeCallback(classLoader: ClassLoader) {
        try {
            val className = "com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback"
            val jsonClassName = General.JSON_OBJECT_NAME
            val jsonClass = Class.forName(jsonClassName, false, classLoader)
            val callbackClass = ReflectionHelper.findClass(className, classLoader)
            val responseMethod = ReflectionHelper.findMethodExact(callbackClass, "sendJSONResponse", jsonClass)
            ModernXposedRuntime.hook(responseMethod, before = { invocation ->
                    val callback = invocation.thisObject
                    val recordArray = rpcHookMap[callback]
                    if (recordArray != null && invocation.args.isNotEmpty()) {
                        recordArray[3] = invocation.args[0].toString()
                    }
            })
            Log.record(TAG, "Hook DefaultBridgeCallback#sendJSONResponse 成功")
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "Hook DefaultBridgeCallback#sendJSONResponse 失败", t)
        }
    }

    /**
     * 突破目标应用最大可登录账号数量限制
     * @param classLoader 类加载器
     */
    fun fuckAccounLimit(classLoader: ClassLoader) {
        Log.record(TAG, "Hook AccountManagerListAdapter#getCount")
        val adapterClass = ReflectionHelper.findClass(
            "com.alipay.mobile.security.accountmanager.data.AccountManagerListAdapter",
            classLoader
        )
        val getCountMethod = ReflectionHelper.findMethodExact(adapterClass, "getCount")
        ModernXposedRuntime.hook(getCountMethod, after = { invocation ->
                    // 获取真实账号列表大小
                    try {
                        val list = ReflectionHelper.getObjectField(invocation.thisObject, "queryAccountList") as? List<*>
                        if (list != null) {
                            invocation.result = list.size  // 设置返回值为真实数量
                            val now = System.currentTimeMillis()
                            if (now - lastToastTime > 1000 * 60) { // 每N秒最多显示一次
                                Toast.show("🎉 TK已尝试为您突破限制")
                                lastToastTime = now
                            }
                        }
//                        Log.runtime(TAG, "Hook AccountManagerListAdapter#getCount but return is null")
                    } catch (e: Throwable) {
                        // 错误日志处理（你可以替换为自己的日志方法）
                        e.printStackTrace()
                        Log.error(TAG, "Hook AccountManagerListAdapter#getCount failed: ${e.message}")
                    }
        })
        Log.record(TAG, "Hook AccountManagerListAdapter#getCount END")
    }


    fun getMicroApplicationContext(classLoader: ClassLoader): Any? {
        if (microContextCache != null) return microContextCache
        return runCatching {
            val appClass = ReflectionHelper.findClass(
                "com.alipay.mobile.framework.AlipayApplication", classLoader
            )
            val appInstance = ReflectionHelper.callStaticMethod(appClass, "getInstance")
            ReflectionHelper.callMethod(appInstance, "getMicroApplicationContext")
                .also { microContextCache = it }
        }.onFailure {
            Log.printStackTrace(TAG, it)
        }.getOrNull()
    }

    fun getServiceObject(classLoader: ClassLoader, serviceName: String): Any? = runCatching {
        val microContext = getMicroApplicationContext(classLoader)
        ReflectionHelper.callMethod(microContext, "findServiceByInterface", serviceName)
    }.onFailure {
        Log.printStackTrace(TAG, it)
    }.getOrNull()

    fun getUserObject(classLoader: ClassLoader): Any? = runCatching {
        val serviceClassName = "com.alipay.mobile.personalbase.service.SocialSdkContactService"
        val serviceClass = ReflectionHelper.findClass(serviceClassName, classLoader)
        val serviceObject = getServiceObject(classLoader, serviceClass.name)
        ReflectionHelper.callMethod(serviceObject, "getMyAccountInfoModelByLocal")
    }.onFailure {
        Log.printStackTrace(TAG, it)
    }.getOrNull()

    fun getUserId(classLoader: ClassLoader): String? = runCatching {
        val userObject = getUserObject(classLoader)
        ReflectionHelper.getObjectField(userObject, "userId") as? String
    }.onFailure {
        Log.printStackTrace(TAG, it)
    }.getOrNull()

    fun hookUser(classLoader: ClassLoader) {
        runCatching {
            UserMap.unload()
            val selfId = getUserId(classLoader)
            UserMap.setCurrentUserId(selfId) //有些地方要用到 要set一下
            val clsUserIndependentCache = classLoader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.UserIndependentCache")
            val clsAliAccountDaoOp = classLoader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountDaoOp")
            val aliAccountDaoOp = ReflectionHelper.callStaticMethod(clsUserIndependentCache, "getCacheObj", clsAliAccountDaoOp)
            val allFriends = ReflectionHelper.callMethod(aliAccountDaoOp, "getAllFriends") as? List<*> ?: emptyList<Any>()
            if (allFriends.isEmpty()) return
            val friendClass = allFriends.firstOrNull()?.javaClass ?: return
            val userIdField = ReflectionHelper.findField(friendClass, "userId")
            val accountField = ReflectionHelper.findField(friendClass, "account")
            val nameField = ReflectionHelper.findField(friendClass, "name")
            val nickNameField = ReflectionHelper.findField(friendClass, "nickName")
            val remarkNameField = ReflectionHelper.findField(friendClass, "remarkName")
            val friendStatusField = ReflectionHelper.findField(friendClass, "friendStatus")
            var selfEntity: UserEntity? = null
            allFriends.forEach { userObject ->
                runCatching {
                    val userId = userIdField.get(userObject) as? String
                    val account = accountField.get(userObject) as? String
                    val name = nameField.get(userObject) as? String
                    val nickName = nickNameField.get(userObject) as? String
                    val remarkName = remarkNameField.get(userObject) as? String
                    val friendStatus = friendStatusField.get(userObject) as? Int
                    val userEntity = UserEntity(userId, account, friendStatus, name, nickName, remarkName)
                    if (userId == selfId) selfEntity = userEntity
                    UserMap.add(userEntity)
                }.onFailure {
                    Log.record(TAG, "addUserObject err:")
                    Log.printStackTrace(it)
                }
            }

            UserMap.saveSelf(selfEntity)
            UserMap.save(selfId)
            Log.record(TAG, "userCache load scuess !")
        }.onFailure {
            Log.printStackTrace(TAG, "hookUser 失败", it)
        }
    }
}
