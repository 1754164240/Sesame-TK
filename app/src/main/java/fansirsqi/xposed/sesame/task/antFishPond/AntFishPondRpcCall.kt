package fansirsqi.xposed.sesame.task.antFishPond

import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONArray
import org.json.JSONObject

interface FishPondGateway {
    fun fishpondIndex(): String
    fun fishpondSyncIndex(syncTypes: List<String>): String
    fun querySubplotsActivity(): String
    fun triggerSubplotsActivity(activityType: String, actionType: String): String
    fun listTask(): String
    fun sign(signKey: String): String
    fun fishpondExchangeReward(): String
    fun fishpondAdNotice(adBizNo: String): String =
        """{"success":false,"resultDesc":"尚未实现广告通知"}"""
    fun queryAdTaskConfig(spaceCode: String): String =
        """{"success":false,"resultDesc":"尚未实现广告配置查询"}"""
    fun requestAdExposure(spaceCode: String, pageUrl: String): String =
        """{"success":false,"resultDesc":"尚未实现广告曝光"}"""
    fun finishTask(taskType: String, sceneCode: String): String
    fun finishTask(
        taskType: String,
        sceneCode: String,
        adBizNo: String?
    ): String = finishTask(taskType, sceneCode)
    fun receiveTaskAward(taskType: String, sceneCode: String): String
    fun fishpondAngle(riskToken: String): String
    fun fishpondAngleRodPositioning(bizNo: String, areaType: String): String
}

object AntFishPondRpcCall {
    private const val VERSION = "20260211.01"
    private const val SOURCE = "farmpool"
    private const val SCENE_GAME_CENTER = "GameCenter"

    private fun baseArgs(): JSONObject {
        return JSONObject()
            .put("requestType", "NORMAL")
            .put("sceneCode", SCENE_GAME_CENTER)
            .put("source", SOURCE)
            .put("version", VERSION)
    }

    private fun indexArgs(): JSONObject = baseArgs().put("appMode", "normal")

    private fun request(method: String, args: JSONObject): String {
        return RequestManager.requestString(method, JSONArray().put(args).toString())
    }

    fun fishpondIndex(): String {
        return request(
            "com.alipay.antfishpond.fishpondIndex",
            indexArgs().put("darwinSceneList", JSONArray().put("taskFullAreaClick"))
        )
    }

    fun fishpondSyncIndex(syncTypes: List<String>): String {
        return request(
            "com.alipay.antfishpond.fishpondSyncIndex",
            indexArgs().put("syncTypeList", JSONArray(syncTypes))
        )
    }

    fun querySubplotsActivity(): String {
        return request("com.alipay.antfishpond.querySubplotsActivity", indexArgs())
    }

    fun triggerSubplotsActivity(activityType: String, actionType: String): String {
        return request(
            "com.alipay.antfishpond.triggerSubplotsActivity",
            baseArgs()
                .put("activityType", activityType)
                .put("actionType", actionType)
        )
    }

    fun listTask(): String {
        return request("com.alipay.antfishpond.listTask", indexArgs())
    }

    fun sign(signKey: String): String {
        return request("com.alipay.antfishpond.sign", baseArgs().put("signKey", signKey))
    }

    fun fishpondExchangeReward(): String {
        return request("com.alipay.antfishpond.fishpondExchangeReward", baseArgs())
    }

    fun buildAdNoticeArgs(adBizNo: String): String {
        return JSONArray()
            .put(baseArgs().put("adBizNo", adBizNo))
            .toString()
    }

    internal fun buildAdTaskConfigArgs(spaceCode: String): String =
        JSONArray()
            .put(JSONObject().put("spaceCode", spaceCode))
            .toString()

    internal fun buildAdExposureArgs(
        spaceCode: String,
        pageUrl: String,
        session: String
    ): String {
        val positionRequest = JSONObject()
            .put("extMap", JSONObject())
            .put("referInfo", JSONObject())
            .put("searchInfo", JSONObject())
            .put("spaceCode", spaceCode)
        val pageInfo = JSONObject()
            .put("adComponentType", "FEEDS")
            .put("adComponentVersion", "4.31.18")
            .put("enableFusion", true)
            .put("networkType", "WIFI")
            .put("pageFrom", "ch_ecopromotion")
            .put("pageNo", 1)
            .put("pageUrl", pageUrl)
            .put("session", session)
            .put("unionAppId", "2060090000304921")
            .put("xlightRuntimeSDKversion", "4.31.18")
            .put("xlightSDKType", "h5")
            .put("xlightSDKVersion", "4.31.18")
        return JSONArray()
            .put(
                JSONObject()
                    .put("positionRequest", positionRequest)
                    .put("sdkPageInfo", pageInfo)
            )
            .toString()
    }

    fun fishpondAdNotice(adBizNo: String): String {
        return RequestManager.requestString(
            "com.alipay.antfishpond.fishpondAdNotice",
            buildAdNoticeArgs(adBizNo)
        )
    }

    fun queryAdTaskConfig(spaceCode: String): String =
        RequestManager.requestString(
            "com.alipay.adtask.biz.mobilegw.service.applayer.query",
            buildAdTaskConfigArgs(spaceCode)
        )

    fun requestAdExposure(spaceCode: String, pageUrl: String): String {
        val session = "u_${RandomUtil.getRandomString(5)}_${RandomUtil.getRandomString(5)}"
        return RequestManager.requestString(
            "com.alipay.adexchange.ad.facade.xlightPlugin",
            buildAdExposureArgs(spaceCode, pageUrl, session)
        )
    }

    fun buildFinishTaskArgs(
        taskType: String,
        sceneCode: String,
        adBizNo: String?,
        outBizNo: String
    ): String {
        val args = JSONObject()
            .put("outBizNo", outBizNo)
            .put("requestType", "RPC")
            .put("sceneCode", sceneCode)
            .put("source", "ADBASICLIB")
            .put("taskType", taskType)
        if (!adBizNo.isNullOrBlank()) {
            args.put(
                "finishBusinessInfo",
                JSONObject().put("pwPreBizId", adBizNo)
            )
        }
        return JSONArray().put(args).toString()
    }

    fun finishTask(taskType: String, sceneCode: String): String {
        return finishTask(taskType, sceneCode, null)
    }

    fun finishTask(
        taskType: String,
        sceneCode: String,
        adBizNo: String?
    ): String {
        val outBizNo =
            "${taskType}_${System.currentTimeMillis()}_${RandomUtil.getRandomString(8)}"
        return RequestManager.requestString(
            "com.alipay.antiep.finishTask",
            buildFinishTaskArgs(taskType, sceneCode, adBizNo, outBizNo)
        )
    }

    fun receiveTaskAward(taskType: String, sceneCode: String): String {
        val args = baseArgs()
            .put("ignoreLimit", false)
            .put("sceneCode", sceneCode)
            .put("taskType", taskType)
        return request("com.alipay.antiep.receiveTaskAward", args)
    }

    fun fishpondAngle(riskToken: String): String {
        return request(
            "com.alipay.antfishpond.fishpondAngle",
            baseArgs()
                .put("bizNo", "")
                .put("riskToken", riskToken)
        )
    }

    fun fishpondAngleRodPositioning(bizNo: String, areaType: String): String {
        return request(
            "com.alipay.antfishpond.fishpondAngleRodPositioning",
            baseArgs()
                .put("bizNo", bizNo)
                .put("areaType", areaType)
        )
    }
}

class AntFishPondRpcGateway : FishPondGateway {
    override fun fishpondIndex(): String = AntFishPondRpcCall.fishpondIndex()

    override fun fishpondSyncIndex(syncTypes: List<String>): String =
        AntFishPondRpcCall.fishpondSyncIndex(syncTypes)

    override fun querySubplotsActivity(): String =
        AntFishPondRpcCall.querySubplotsActivity()

    override fun triggerSubplotsActivity(activityType: String, actionType: String): String =
        AntFishPondRpcCall.triggerSubplotsActivity(activityType, actionType)

    override fun listTask(): String = AntFishPondRpcCall.listTask()

    override fun sign(signKey: String): String = AntFishPondRpcCall.sign(signKey)

    override fun fishpondExchangeReward(): String =
        AntFishPondRpcCall.fishpondExchangeReward()

    override fun fishpondAdNotice(adBizNo: String): String =
        AntFishPondRpcCall.fishpondAdNotice(adBizNo)

    override fun queryAdTaskConfig(spaceCode: String): String =
        AntFishPondRpcCall.queryAdTaskConfig(spaceCode)

    override fun requestAdExposure(spaceCode: String, pageUrl: String): String =
        AntFishPondRpcCall.requestAdExposure(spaceCode, pageUrl)

    override fun finishTask(taskType: String, sceneCode: String): String =
        AntFishPondRpcCall.finishTask(taskType, sceneCode)

    override fun finishTask(
        taskType: String,
        sceneCode: String,
        adBizNo: String?
    ): String = AntFishPondRpcCall.finishTask(taskType, sceneCode, adBizNo)

    override fun receiveTaskAward(taskType: String, sceneCode: String): String =
        AntFishPondRpcCall.receiveTaskAward(taskType, sceneCode)

    override fun fishpondAngle(riskToken: String): String =
        AntFishPondRpcCall.fishpondAngle(riskToken)

    override fun fishpondAngleRodPositioning(bizNo: String, areaType: String): String =
        AntFishPondRpcCall.fishpondAngleRodPositioning(bizNo, areaType)
}
