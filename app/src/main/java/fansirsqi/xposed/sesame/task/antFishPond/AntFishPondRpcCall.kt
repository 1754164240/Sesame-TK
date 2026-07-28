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
    fun finishTask(taskType: String, sceneCode: String): String
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

    fun finishTask(taskType: String, sceneCode: String): String {
        val args = JSONObject()
            .put(
                "outBizNo",
                "${taskType}_${System.currentTimeMillis()}_${RandomUtil.getRandomString(8)}"
            )
            .put("requestType", "RPC")
            .put("sceneCode", sceneCode)
            .put("source", "ADBASICLIB")
            .put("taskType", taskType)
        return request("com.alipay.antiep.finishTask", args)
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

    override fun finishTask(taskType: String, sceneCode: String): String =
        AntFishPondRpcCall.finishTask(taskType, sceneCode)

    override fun receiveTaskAward(taskType: String, sceneCode: String): String =
        AntFishPondRpcCall.receiveTaskAward(taskType, sceneCode)

    override fun fishpondAngle(riskToken: String): String =
        AntFishPondRpcCall.fishpondAngle(riskToken)

    override fun fishpondAngleRodPositioning(bizNo: String, areaType: String): String =
        AntFishPondRpcCall.fishpondAngleRodPositioning(bizNo, areaType)
}
