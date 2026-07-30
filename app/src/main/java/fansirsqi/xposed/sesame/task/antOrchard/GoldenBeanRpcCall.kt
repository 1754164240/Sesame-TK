package fansirsqi.xposed.sesame.task.antOrchard

import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONArray
import org.json.JSONObject

interface GoldenBeanGateway {
    fun index(): String
    fun sync(syncTypes: List<String>): String
    fun fortuneDraw(): String
    fun finishTask(taskType: String, sceneCode: String): String
    fun receiveTaskAward(taskType: String, sceneCode: String): String
}

object GoldenBeanRpcCall {
    private const val VERSION = "20260723.01"
    private const val BIZ_TYPE = "MASTER"
    private const val SOURCE = "babafarm"

    private fun baseArgs(): JSONObject = JSONObject()
        .put("bizType", BIZ_TYPE)
        .put("source", SOURCE)
        .put("version", VERSION)

    private fun request(method: String, args: String): String =
        RequestManager.requestString(method, args)

    internal fun buildIndexArgs(): String =
        JSONArray().put(baseArgs().put("darwinSceneList", JSONArray())).toString()

    internal fun buildSyncArgs(syncTypes: List<String>): String =
        JSONArray()
            .put(baseArgs().put("syncTypeList", JSONArray(syncTypes)))
            .toString()

    internal fun buildFortuneDrawArgs(): String =
        JSONArray().put(baseArgs()).toString()

    internal fun buildFinishTaskArgs(
        taskType: String,
        sceneCode: String,
        outBizNo: String
    ): String {
        val args = baseArgs()
            .put("finishBusinessInfo", JSONObject().put("bizType", BIZ_TYPE))
            .put("outBizNo", outBizNo)
            .put("sceneCode", sceneCode)
            .put("taskType", taskType)
        return JSONArray().put(args).toString()
    }

    internal fun buildReceiveTaskAwardArgs(
        taskType: String,
        sceneCode: String
    ): String {
        val args = baseArgs()
            .put("bizInfo", JSONObject().put("bizType", BIZ_TYPE))
            .put("ignoreLimit", true)
            .put("sceneCode", sceneCode)
            .put("taskType", taskType)
        return JSONArray().put(args).toString()
    }

    fun index(): String =
        request("com.alipay.goldenbean.index", buildIndexArgs())

    fun sync(syncTypes: List<String>): String =
        request("com.alipay.goldenbean.sync", buildSyncArgs(syncTypes))

    fun fortuneDraw(): String =
        request("com.alipay.goldenbean.fortuneDraw", buildFortuneDrawArgs())

    fun finishTask(taskType: String, sceneCode: String): String {
        val outBizNo =
            "${System.currentTimeMillis()}${RandomUtil.getRandomString(12)}"
        return request(
            "com.alipay.antieptask.finishTaskantorchard",
            buildFinishTaskArgs(taskType, sceneCode, outBizNo)
        )
    }

    fun receiveTaskAward(taskType: String, sceneCode: String): String =
        request(
            "com.alipay.antieptask.receiveTaskAwardantorchard",
            buildReceiveTaskAwardArgs(taskType, sceneCode)
        )
}

class GoldenBeanRpcGateway : GoldenBeanGateway {
    override fun index(): String = GoldenBeanRpcCall.index()

    override fun sync(syncTypes: List<String>): String =
        GoldenBeanRpcCall.sync(syncTypes)

    override fun fortuneDraw(): String = GoldenBeanRpcCall.fortuneDraw()

    override fun finishTask(taskType: String, sceneCode: String): String =
        GoldenBeanRpcCall.finishTask(taskType, sceneCode)

    override fun receiveTaskAward(taskType: String, sceneCode: String): String =
        GoldenBeanRpcCall.receiveTaskAward(taskType, sceneCode)
}
