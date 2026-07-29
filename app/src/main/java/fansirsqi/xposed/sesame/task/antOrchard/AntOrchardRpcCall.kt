package fansirsqi.xposed.sesame.task.antOrchard

import fansirsqi.xposed.sesame.hook.RequestManager
import org.json.JSONArray
import org.json.JSONObject

object AntOrchardRpcCall {
    private const val VERSION = "20260721.01"
    private const val CHARITY_GAME_CENTER_VERSION = "10.8.20"
    private const val ENTRY_SOURCE = "ch_appcenter__chsub_9patch"

    fun orchardIndex(): String {
        return RequestManager.requestString("com.alipay.antfarm.orchardIndex",
            "[{\"inHomepage\":\"true\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
                    + VERSION + "\"}]");
    }

    /**
     * 获取额外信息（包含每日肥料、施肥礼盒）
     * @param from 来源：entry(首页), water(施肥后)
     */
    fun extraInfoGet(from: String = "entry"): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.extraInfoGet",
            "[{\"from\":\"$from\",\"requestType\":\"NORMAL\",\"sceneCode\":\"FUGUO\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"$VERSION\"}]"
        )
    }

    fun extraInfoSet(): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.extraInfoSet",
            "[{\"bizCode\":\"fertilizerPacket\",\"bizParam\":{\"action\":\"queryCollectFertilizerPacket\"},\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"$VERSION\"}]"
        )
    }

    // 修改：增加 LIMITED_TIME_CHALLENGE 和 LOTTERY_PLUS 类型
    fun querySubplotsActivity(treeLevel: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.querySubplotsActivity",
            "[{\"activityType\":[\"WISH\",\"BATTLE\",\"HELP_FARMER\",\"DEFOLIATION\",\"CAMP_TAKEOVER\",\"LIMITED_TIME_CHALLENGE\",\"LOTTERY_PLUS\"],\"inHomepage\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"treeLevel\":\"$treeLevel\",\"version\":\"$VERSION\"}]"
        )
    }

    fun triggerSubplotsActivity(activityId: String, activityType: String, optionKey: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.triggerSubplotsActivity",
            "[{\"activityId\":\"$activityId\",\"activityType\":\"$activityType\",\"optionKey\":\"$optionKey\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"$VERSION\"}]"
        )
    }

    fun receiveOrchardRights(activityId: String, activityType: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.receiveOrchardRights",
            "[{\"activityId\":\"$activityId\",\"activityType\":\"$activityType\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"$VERSION\"}]"
        )
    }

    /* 七日礼包 */
    fun drawLottery(): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.drawLottery",
            "[{\"lotteryScene\":\"receiveLotteryPlus\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"$VERSION\"}]"
        )
    }

    /**
     * 切换种植场景
     * @param plantScene main(果树) 或 yeb(摇钱树)
     */
    fun switchPlantScene(plantScene: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.switchPlantScene",
            "[{\"plantScene\":\"$plantScene\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"$VERSION\"}]"
        )
    }

    /**
     * 施肥
     * @param wua 用户标识
     * @param source 来源标识，可自定义
     * @param useBatchSpread 一键5次
     * @param plantScene 场景：main 或 yeb
     */
    fun orchardSpreadManure(wua: String, source: String, useBatchSpread: Boolean = false, plantScene: String = "main"): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.orchardSpreadManure",
            "[{\"plantScene\":\"$plantScene\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"$source\",\"useBatchSpread\":$useBatchSpread,\"version\":\"$VERSION\",\"wua\":\"$wua\"}]"
        )
    }

    fun receiveTaskAward(sceneCode: String, taskType: String): String {
        return RequestManager.requestString(
            "com.alipay.antiep.receiveTaskAward",
            "[{\"ignoreLimit\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"$sceneCode\",\"source\":\"ch_alipaysearch__chsub_normal\",\"taskType\":\"$taskType\",\"version\":\"$VERSION\"}]"
        )
    }

    internal fun buildOrchardListTaskArgs(
        source: String = ENTRY_SOURCE
    ): String {
        val args = JSONObject().apply {
            put("addWidget", false)
            put("appMode", "normal")
            put("enableSwitchSceneList", JSONArray().put("main").put("yeb"))
            put("enableTeamType", JSONArray().put("help").put("team"))
            put("hasYebActivityEntrance", true)
            put("plantHiddenMMC", "false")
            put("requestType", "NORMAL")
            put("sceneCode", "ORCHARD")
            put("source", source)
            put("version", VERSION)
        }
        return JSONArray().put(args).toString()
    }

    fun orchardListTask(source: String = ENTRY_SOURCE): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.orchardListTask",
            buildOrchardListTaskArgs(source)
        )
    }

    fun orchardSign(): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.orchardSign",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"signScene\":\"ANTFARM_ORCHARD_SIGN_V2\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"$VERSION\"}]"
        )
    }

    fun finishTask(
        userId: String,
        sceneCode: String,
        taskType: String,
        source: String = ENTRY_SOURCE
    ): String {
        return RequestManager.requestString(
            "com.alipay.antiep.finishTask",
            "[{\"outBizNo\":\"${userId}${System.currentTimeMillis()}\",\"requestType\":\"NORMAL\",\"sceneCode\":\"$sceneCode\",\"source\":\"$source\",\"taskType\":\"$taskType\",\"userId\":\"$userId\",\"version\":\"$VERSION\"}]"
        )
    }

    fun triggerTbTask(
        taskId: String,
        taskPlantType: String,
        source: String = ENTRY_SOURCE
    ): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.triggerTbTask",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"$source\",\"taskId\":\"$taskId\",\"taskPlantType\":\"$taskPlantType\",\"version\":\"$VERSION\"}]"
        )
    }

    internal fun buildQueryOptionalPlayArgs(): String {
        val args = JSONObject().apply {
            put("bizType", "ANTORCHARD")
            put(
                "commonDegradeFilterRequest",
                JSONObject().apply {
                    put("appMode", "normal")
                    put("deviceLevel", "high")
                    put("h5Version", VERSION)
                    put("unityDeviceLevel", "high")
                }
            )
            put(
                "playTypeList",
                JSONArray().put("TOP_UP_COUPON").put("TASK_TRIGGER")
            )
            put("requestType", "RPC")
            put("sceneCode", "ORCHARD")
            put("source", "H5")
            put("version", CHARITY_GAME_CENTER_VERSION)
        }
        return JSONArray().put(args).toString()
    }

    fun queryOptionalPlay(): String {
        return RequestManager.requestString(
            "com.alipay.charitygamecenter.queryOptionalPlay",
            buildQueryOptionalPlayArgs()
        )
    }

    internal fun buildLeyuanClaimArgs(
        sceneCode: String,
        taskType: String,
        awardCount: Int
    ): String {
        val args = JSONObject().apply {
            put("awardCountForReceive", awardCount)
            put("ignoreLimit", true)
            put("requestType", "RPC")
            put("sceneCode", sceneCode)
            put("source", "antorchard")
            put("taskType", taskType)
            put("version", VERSION)
        }
        return JSONArray().put(args).toString()
    }

    fun receiveLeyuanTaskAward(
        sceneCode: String,
        taskType: String,
        awardCount: Int
    ): String {
        return RequestManager.requestString(
            "com.alipay.antieptask.receiveTaskAwardantorchard",
            buildLeyuanClaimArgs(sceneCode, taskType, awardCount)
        )
    }

    //砸蛋
    fun smashedGoldenEgg(count: Int): String {
        val jsonArgs = """
        [
            {
                "batchSmashCount": $count,
                "requestType": "NORMAL",
                "sceneCode": "ORCHARD",
                "source": "ch_appcenter__chsub_9patch",
                "version": "$VERSION"
            }
        ]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.smashedGoldenEgg",
            jsonArgs
        )
    }

    /**
     * 收取果园回访奖励
     * @param diversionSource 引流来源（如：widget、tmall）
     * @param source 具体来源（如：widget_shoufei、upgrade_tmall_exchange_task）
     * @return 请求结果字符串
     */
    fun receiveOrchardVisitAward(
        diversionSource: String,
        source: String
    ): String {
        val requestParams = """
        [{"diversionSource":"$diversionSource",
          "requestType":"NORMAL",
          "sceneCode":"ORCHARD",
          "source":"$source",
          "version":"$VERSION"}]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.receiveOrchardVisitAward",
            requestParams
        )
    }

    fun orchardSyncIndex(Wua: String): String {
        val jsonArgs = """
         [{
             "requestType": "NORMAL",
             "sceneCode": "ORCHARD",
             "source": "ch_appcenter__chsub_9patch",
             "syncIndexTypes": "LIMITED_TIME_CHALLENGE",
             "useWua": true,
             "version": "$VERSION",
             "wua": "$Wua"
         }]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.orchardSyncIndex",
            jsonArgs
        )
    }

    fun noticeGame(appId: String): String {
        val jsonArgs = """
          [{
             "appId": "2021004165643274",
             "requestType": "NORMAL",
             "sceneCode": "ORCHARD",
             "source": "ch_appcenter__chsub_9patch",
             "version": "$VERSION"
         }]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.noticeGame",
            jsonArgs
        )
    }

    fun achieveBeShareP2P(shareId: String): String {
        return RequestManager.requestString(
            "com.alipay.antiep.achieveBeShareP2P",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM_ORCHARD_SHARE_P2P\",\"shareId\":\"$shareId\",\"source\":\"share\",\"version\":\"$VERSION\"}]"
        )
    }

    /* 摇钱树收余额奖励 */
    fun moneyTreeTrigger(): String {
        return RequestManager.requestString(
            "com.alipay.yebbffweb.needle.yebHome.moneyTree.trigger",
            "[{\"sceneType\":\"default\",\"type\":\"trigger\"}]"
        )
    }
}
