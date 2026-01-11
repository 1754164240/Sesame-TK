package fansirsqi.xposed.sesame.task.antSports;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List; //健康岛导入的

import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.RequestManager;

public class AntSportsRpcCall {
    private static final String chInfo = "ch_appcenter__chsub_9patch",


    timeZone = "Asia\\/Shanghai", version = "3.0.1.2", alipayAppVersion = String.valueOf(ApplicationHook.getAlipayVersion()),
            cityCode = "330100", appId = "2021002116659397";

    private static final String FEATURES = "["
            + "\"DAILY_STEPS_RANK_V2\","
            + "\"STEP_BATTLE\","
            + "\"CLUB_HOME_CARD\","
            + "\"NEW_HOME_PAGE_STATIC\","
            + "\"CLOUD_SDK_AUTH\","
            + "\"STAY_ON_COMPLETE\","
            + "\"EXTRA_TREASURE_BOX\","
            + "\"NEW_HOME_PAGE_STATIC\","
            + "\"SUPPORT_AI\","
            + "\"SUPPORT_TAB3\","
            + "\"SUPPORT_FLYRABBIT\","
            + "\"SUPPORT_NEW_MATCH\","
            + "\"EXTERNAL_ADVERTISEMENT_TASK\","
            + "\"PROP\","
            + "\"PROPV2\","
            + "\"ASIAN_GAMES\""
            + "]";

    // 运动任务查询 新
    public static String queryCoinTaskPanel() {
        String args1 = "[{"
                + "\"apiVersion\":\"energy\","
                + "\"canAddHome\":false,"
                + "\"chInfo\":\"medical_health\","
                + "\"clientAuthStatus\":\"not_support\","
                + "\"clientOS\":\"android\","
                + "\"features\":" + FEATURES + ","
                + "\"topTaskId\":\"\""
                + "}]";
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.queryCoinTaskPanel", args1);
    }

    //完整签到，如果任务需要就签到哦
    public static String signUpTask(String taskId) {
        // features 列表可根据需要保持一致

        String args = "[\n" +
                "    {\n" +
                "        \"apiVersion\": \"energy\",\n" +
                "        \"chInfo\": \"medical_health\",\n" +
                "        \"clientOS\": \"android\",\n" +
                "        \"features\": " + FEATURES + ",\n" +
                "        \"taskCenId\": \"\",\n" +
                "        \"taskId\": \"" + taskId + "\"\n" +
                "    }\n" +
                "]";

        return RequestManager.requestString(
                "com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signUpTask",
                args
        );
    }

    // 去完成任务
    public static String completeExerciseTasks(String taskId) {
        String args1 = "[\n" +
                "    {\n" +
                "        \"chInfo\": \"ch_appcenter__chsub_9patch\",\n" +
                "        \"clientOS\": \"android\",\n" +
                "        \"features\": " +FEATURES+","+
                "        \"taskAction\": \"JUMP\",\n" +
                "        \"taskId\": \""+taskId+"\"\n" +
                "    }\n" +
                "]";
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args1);
    }
    //我新加的，上面的是旧版接口
    public static String completeTask(String taskId) {
        String args1 = "[{"
                + "\"apiVersion\":\"energy\","
                + "\"chInfo\":\"medical_health\","
                + "\"clientOS\":\"android\","
                + "\"features\":" + FEATURES + ","
                + "\"taskAction\":\"JUMP\","
                + "\"taskId\":\"" + taskId + "\""
                + "}]";
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args1);
    }

    public static String queryMainPage() {
        String args = "{\n" +
                "    \"apiVersion\": \"energy\",\n" +
                "    \"chInfo\": \"ch_shouquan_shouye\",\n" +
                "    \"cityCode\": "+cityCode+",\n" +
                "    \"clientOS\": \"android\",\n" +
                "    \"features\": " + FEATURES + ",\n" +
                "    \"timezone\": \"Asia/Shanghai\"\n" +
                "}";
        return RequestManager.requestString(
                "com.alipay.sportshealth.biz.rpc.queryMainPage",
                args
        );
    }

    /**
     * 运动健康签到/查询接口
     * @param operatorType 操作类型，支持 "signIn"（签到）、"query"（查询）等
     * @return 接口调用结果
     */
    public static String signInCoinTask(String operatorType) {
        String args1 = "[{"
                + "\"apiVersion\":\"energy\","
                + "\"chInfo\":\"medical_health\","
                + "\"clientOS\":\"android\","
                + "\"features\":" + FEATURES + ","
                + "\"operatorType\":\"" + operatorType + "\""
                + "}]";
        // 调用请求管理器发送请求
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signInCoinTask", args1);
    }
    public static String queryCoinBubbleModule() {
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryCoinBubbleModule",
                "[{\"bubbleId\":\"\",\"canAddHome\":false,\"chInfo\":\"" + chInfo
                        + "\",\"clientAuthStatus\":\"not_support\",\"clientOS\":\"android\",\"distributionChannel\":\"\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_AI\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"]}]");
    }
    // 领取任务奖励 - 默认不领取所有能量球（抢好友能量球）
    public static String pickBubbleTaskEnergy(String medEnergyBallInfoRecordId) {
        return pickBubbleTaskEnergy(medEnergyBallInfoRecordId, false);
    }

    // 领取任务奖励 - 可指定是否领取所有能量球（抢好友能量球）
    // 对应文档：com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
    // {
    //   "apiVersion": "energy",
    //   "chInfo": "healthstep",
    //   "medEnergyBallInfoRecordIds":["824a1eb3..."],
    //   "pickAllEnergyBall": false,
    //   "source": "SPORT"
    // }
    public static String pickBubbleTaskEnergy(String medEnergyBallInfoRecordId, boolean pickAllEnergyBall) {
        String args1 = "[{"
                + "\"apiVersion\":\"energy\","
                + "\"chInfo\":\"medical_health\","
                + "\"clientOS\":\"android\","
                + "\"features\":" + FEATURES + ","
                + "\"medEnergyBallInfoRecordIds\":[\"" + medEnergyBallInfoRecordId + "\"],"
                + "\"pickAllEnergyBall\":" + pickAllEnergyBall + ","
                + "\"source\":\"SPORT\""
                + "}]";
        return RequestManager.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", args1);
    }

    public static String queryEnergyBubbleModule() {
        // 构建请求的参数 JSON 字符串
        String features = "[\"DAILY_STEPS_RANK_V2\", \"STEP_BATTLE\", \"CLUB_HOME_CARD\", \"NEW_HOME_PAGE_STATIC\", \"CLOUD_SDK_AUTH\", \"STAY_ON_COMPLETE\", \"EXTRA_TREASURE_BOX\", \"NEW_HOME_PAGE_STATIC\", \"SUPPORT_AI\", \"SUPPORT_TAB3\", \"SUPPORT_FLYRABBIT\", \"SUPPORT_NEW_MATCH\", \"EXTERNAL_ADVERTISEMENT_TASK\", \"PROP\", \"PROPV2\", \"ASIAN_GAMES\"]";

        String args1 = "[{\"apiVersion\":\"energy\",\"bubbleId\":\"\",\"canAddHome\":false,\"chInfo\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\",\"clientAuthStatus\":\"not_support\",\"clientOS\":\"android\",\"distributionChannel\":\"\",\"features\":" + features + ",\"outBizNo\":\"\"}]";

        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryEnergyBubbleModule", args1);
    }
    public static String pickBubbleTaskEnergy() {
        // features 数组
        String features = "[\"DAILY_STEPS_RANK_V2\", \"STEP_BATTLE\", \"CLUB_HOME_CARD\", \"NEW_HOME_PAGE_STATIC\", \"CLOUD_SDK_AUTH\", \"STAY_ON_COMPLETE\", \"EXTRA_TREASURE_BOX\", \"NEW_HOME_PAGE_STATIC\", \"SUPPORT_AI\", \"SUPPORT_TAB3\", \"SUPPORT_FLYRABBIT\", \"SUPPORT_NEW_MATCH\", \"EXTERNAL_ADVERTISEMENT_TASK\", \"PROP\", \"PROPV2\", \"ASIAN_GAMES\"]";

        // 构建请求的 JSON 字符串，medEnergyBallInfoRecordIds 传空数组
        String args1 = "[{\"apiVersion\":\"energy\",\"chInfo\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\",\"clientOS\":\"android\",\"features\":" + features + ",\"medEnergyBallInfoRecordIds\":[],\"pickAllEnergyBall\":true,\"source\":\"SPORT\"}]";

        // 调用请求方法并返回结果
        return RequestManager.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", args1);
    }

    public static String receiveCoinAsset(String assetId, int coinAmount) {
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinCenterRpc.receiveCoinAsset",
                "[{\"assetId\":\"" + assetId
                        + "\",\"chInfo\":\"" + chInfo
                        + "\",\"clientOS\":\"android\",\"coinAmount\":"
                        + coinAmount
                        + ",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"tracertPos\":\"首页金币收集\"}]");
    }
    public static String queryMyHomePage() {
        return RequestManager.requestString("alipay.antsports.walk.map.queryMyHomePage", "[{\"alipayAppVersion\":\""
                + alipayAppVersion + "\",\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"pathListUsePage\":true,\"timeZone\":\""
                + timeZone + "\"}]");
    }
    public static String join(String pathId) {
        return RequestManager.requestString("alipay.antsports.walk.map.join", "[{\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"pathId\":\""
                + pathId + "\"}]");
    }
    public static String openAndJoinFirst() {
        return RequestManager.requestString("alipay.antsports.walk.user.openAndJoinFirst", "[{\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"]}]");
    }
    public static String go(String day, String rankCacheKey, int stepCount) {
        return RequestManager.requestString("alipay.antsports.walk.map.go", "[{\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"day\":\"" + day
                + "\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"needAllBox\":true,\"rankCacheKey\":\""
                + rankCacheKey + "\",\"timeZone\":\"" + timeZone + "\",\"useStepCount\":" + stepCount
                + "}]");
    }
    public static String openTreasureBox(String boxNo, String userId) {
        return RequestManager.requestString("alipay.antsports.walk.treasureBox.openTreasureBox", "[{\"boxNo\":\"" + boxNo
                + "\",\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"userId\":\""
                + userId + "\"}]");
    }
    public static String queryBaseList() {
        return RequestManager.requestString("alipay.antsports.walk.path.queryBaseList", "[{\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"]}]");
    }
    public static String queryProjectList(int index) {
        return RequestManager.requestString("alipay.antsports.walk.charity.queryProjectList", "[{\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"index\":"
                + index + ",\"projectListUseVertical\":true}]");
    }
    public static String donate(int donateCharityCoin, String projectId) {
        return RequestManager.requestString("alipay.antsports.walk.charity.donate", "[{\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"donateCharityCoin\":" + donateCharityCoin
                + ",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"projectId\":\""
                + projectId + "\"}]");
    }
    public static String queryWalkStep() {
        return RequestManager.requestString("alipay.antsports.walk.user.queryWalkStep", "[{\"chInfo\":\"" + chInfo
                + "\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"timeZone\":\""
                + timeZone + "\"}]");
    }
    public static String walkDonateSignInfo(int count) {
        return RequestManager.requestString("alipay.charity.mobile.donate.walk.walkDonateSignInfo",
                "[{\"needDonateAction\":false,\"source\":\"walkDonateHome\",\"steps\":" + count
                        + ",\"timezoneId\":\""
                        + timeZone + "\"}]");
    }
    public static String donateWalkHome(int count) {
        return RequestManager.requestString("alipay.charity.mobile.donate.walk.home",
                "[{\"module\":\"3\",\"steps\":" + count + ",\"timezoneId\":\"" + timeZone + "\"}]");
    }
    public static String exchange(String actId, int count, String donateToken) {
        return RequestManager.requestString("alipay.charity.mobile.donate.walk.exchange",
                "[{\"actId\":\"" + actId + "\",\"count\":"
                        + count + ",\"donateToken\":\"" + donateToken + "\",\"timezoneId\":\""
                        + timeZone + "\",\"ver\":0}]");
    }
    // 运动币兑好礼
    public static String queryItemDetail(String itemId) {
        String arg = "[{\"itemId\":\"" + itemId + "\"}]";
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryItemDetail", arg);
    }
    public static String exchangeItem(String itemId, int coinAmount) {
        String arg = "[{\"coinAmount\":" + coinAmount + ",\"itemId\":\"" + itemId + "\"}]";
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.exchangeItem", arg);
    }
    public static String queryExchangeRecordPage(String exchangeRecordId) {
        String arg = "[{\"exchangeRecordId\":\"" + exchangeRecordId + "\"}]";
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryExchangeRecordPage", arg);
    }
    /*
     * 新版 走路线
     */
    // 查询用户
    public static String queryUser() {
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.queryUser",
                "[{"
                        + "\"apiVersion\":\"energy\","
                        + "\"chInfo\":\"medical_health\","
                        + "\"clientOS\":\"android\","
                        + "\"features\":" + FEATURES
                        + "}]");
    }
    // 查询主题列表
    public static String queryThemeList() {
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.theme.queryThemeList",
                "[{"
                        + "\"apiVersion\":\"energy\","
                        + "\"chInfo\":\"medical_health\","
                        + "\"clientOS\":\"android\","
                        + "\"features\":" + FEATURES
                        + "}]");
    }

    // 查询世界地图 (新版 API)
    public static String queryWorldMap(String themeId) {
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.queryWorldMap",
                "[{"
                        + "\"apiVersion\":\"energy\","
                        + "\"chInfo\":\"medical_health\","
                        + "\"clientOS\":\"android\","
                        + "\"features\":" + FEATURES+","
                        + "\"themeId\":\"" + themeId + "\""
                        + "}]");
    }


    public static String queryCityPath(String cityId) {
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.queryCityPath",
                "[{"
                        + "\"apiVersion\":\"energy\","
                        + "\"chInfo\":\"ch_othertinyapp\","
                        + "\"cityId\":\"" + cityId + "\","
                        + "\"clientOS\":\"android\","
                        + "\"features\":" + FEATURES
                        + "}]");
    }
    // 查询路线
    public static String queryPath(String date, String pathId) {

        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.queryPath",
                "[{"
                        + "\"apiVersion\":\"energy\","
                        + "\"chInfo\":\"medical_health\","
                        + "\"clientOS\":\"android\","
                        + "\"date\":\"" + date + "\","
                        + "\"enableNewVersion\":true,"
                        + "\"features\":" + FEATURES + "," // 复用之前定义的 features 常量
                        + "\"pathId\":\"" + pathId + "\","
                        + "\"timeZone\":\"" + timeZone + "\""
                        + "}]");
    }
    // 加入路线
    public static String joinPath(String pathId) {
        // 构造请求体
        String requestBody = "[{"
                + "\"apiVersion\":\"energy\","
                + "\"chInfo\":\"ch_othertinyapp\","
                + "\"clientOS\":\"android\","
                + "\"features\":" + FEATURES + ","
                + "\"pathId\":\"" + pathId + "\""
                + "}]";

        return RequestManager.requestString(
                "com.alipay.sportsplay.biz.rpc.walk.joinPath",
                requestBody
        );
    }
    // 行走路线
    public static String walkGo(String date, String pathId, int useStepCount) {
        String requestBody = "[{"
                + "\"apiVersion\":\"energy\","
                + "\"chInfo\":\"ch_othertinyapp\","
                + "\"clientOS\":\"android\","
                + "\"date\":\"" + date + "\","
                + "\"features\":" + FEATURES + ","
                + "\"pathId\":\"" + pathId + "\","
                + "\"source\":\"ch_othertinyapp\","
                + "\"timeZone\":\"" + timeZone + "\","
                + "\"useStepCount\":" + useStepCount
                + "}]";

        return RequestManager.requestString(
                "com.alipay.sportsplay.biz.rpc.walk.go",
                requestBody
        );
    }
    // 开启宝箱
    // eventBillNo = boxNo(WalkGo)
    public static String receiveEvent(String eventBillNo) {
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.receiveEvent",
                "[{\"eventBillNo\":\"" + eventBillNo + "\"}]");
    }
    // 查询路线奖励
    public static String queryPathReward(String appId, String pathId) {
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.queryPathReward", "[{\"appId\":\""
                + appId + "\",\"pathId\":\"" + pathId + "\",\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }
    /* 这个好像没用 */
    public static String exchangeSuccess(String exchangeId) {
        String args1 = "[{\"exchangeId\":\"" + exchangeId
                + "\",\"timezone\":\"GMT+08:00\",\"version\":\"" + version + "\"}]";
        return RequestManager.requestString("alipay.charity.mobile.donate.exchange.success", args1);
    }
    /* 文体中心 */
    public static String userTaskGroupQuery(String groupId) {
        return RequestManager.requestString("alipay.tiyubiz.sports.userTaskGroup.query",
                "[{\"cityCode\":\"" + cityCode + "\",\"groupId\":\"" + groupId + "\"}]");
    }
    public static String userTaskComplete(String bizType, String taskId) {
        return RequestManager.requestString("alipay.tiyubiz.sports.userTask.complete",
                "[{\"bizType\":\"" + bizType + "\",\"cityCode\":\"" + cityCode + "\",\"completedTime\":"
                        + System.currentTimeMillis() + ",\"taskId\":\"" + taskId + "\"}]");
    }
    public static String userTaskRightsReceive(String taskId, String userTaskId) {
        return RequestManager.requestString("alipay.tiyubiz.sports.userTaskRights.receive",
                "[{\"taskId\":\"" + taskId + "\",\"userTaskId\":\"" + userTaskId + "\"}]");
    }
    public static String queryAccount() {
        return RequestManager.requestString("alipay.tiyubiz.user.asset.query.account",
                "[{\"accountType\":\"TIYU_SEED\"}]");
    }
    public static String queryRoundList() {
        return RequestManager.requestString("alipay.tiyubiz.wenti.walk.queryRoundList",
                "[{}]");
    }
    public static String participate(int bettingPoints, String InstanceId, String ResultId, String roundId) {
        return RequestManager.requestString("alipay.tiyubiz.wenti.walk.participate",
                "[{\"bettingPoints\":" + bettingPoints + ",\"guessInstanceId\":\"" + InstanceId
                        + "\",\"guessResultId\":\"" + ResultId
                        + "\",\"newParticipant\":false,\"roundId\":\"" + roundId
                        + "\",\"stepTimeZone\":\"Asia/Shanghai\"}]");
    }
    public static String pathFeatureQuery() {
        return RequestManager.requestString("alipay.tiyubiz.path.feature.query",
                "[{\"appId\":\"" + appId
                        + "\",\"features\":[\"USER_CURRENT_PATH_SIMPLE\"],\"sceneCode\":\"wenti_shijiebei\"}]");
    }
    public static String pathMapJoin(String pathId) {
        return RequestManager.requestString("alipay.tiyubiz.path.map.join",
                "[{\"appId\":\"" + appId + "\",\"pathId\":\"" + pathId + "\"}]");
    }
    public static String pathMapHomepage(String pathId) {
        return RequestManager.requestString("alipay.tiyubiz.path.map.homepage",
                "[{\"appId\":\"" + appId + "\",\"pathId\":\"" + pathId + "\"}]");
    }
    public static String stepQuery(String countDate, String pathId) {
        return RequestManager.requestString("alipay.tiyubiz.path.map.step.query",
                "[{\"appId\":\"" + appId + "\",\"countDate\":\"" + countDate
                        + "\",\"pathId\":\""
                        + pathId + "\",\"timeZone\":\"Asia/Shanghai\"}]");
    }
    public static String tiyubizGo(String countDate, int goStepCount, String pathId, String userPathRecordId) {
        return RequestManager.requestString("alipay.tiyubiz.path.map.go",
                "[{\"appId\":\"" + appId + "\",\"countDate\":\"" + countDate
                        + "\",\"goStepCount\":"
                        + goStepCount + ",\"pathId\":\"" + pathId
                        + "\",\"timeZone\":\"Asia/Shanghai\",\"userPathRecordId\":\""
                        + userPathRecordId + "\"}]");
    }
    public static String rewardReceive(String pathId, String userPathRewardId) {
        return RequestManager.requestString("alipay.tiyubiz.path.map.reward.receive",
                "[{\"appId\":\"" + appId + "\",\"pathId\":\"" + pathId + "\",\"userPathRewardId\":\""
                        + userPathRewardId + "\"}]");
    }
    /* 抢好友大战 */
    // 查询抢好友主页
    // 对应文档：alipay.antsports.club.home.queryClubHome
    // 请求：{"apiVersion":"energy","chInfo":"healthstep","timeZone":"Asia/Shanghai"}
    public static String queryClubHome() {
        String args = "[" +
                "{" +
                "\"apiVersion\":\"energy\"," +
                "\"chInfo\":\"healthstep\"," +
                "\"timeZone\":\"Asia/Shanghai\"" +
                "}" +
                "]";
        return RequestManager.requestString("alipay.antsports.club.home.queryClubHome", args);
    }

    // 兼容旧接口名：内部直接走 pickBubbleTaskEnergy（基于 bubbleId）
    public static void collectBubble(String bubbleId) {
        try {
            pickBubbleTaskEnergy(bubbleId, false);
        } catch (Throwable ignored) {
        }
    }

    // 查询训练项目
    // 对应文档：alipay.antsports.club.train.queryTrainItem
    // 请求：{"apiVersion":"energy","chInfo":"healthstep"}
    public static String queryTrainItem() {
        String args = "[" +
                "{" +
                "\"apiVersion\":\"energy\"," +
                "\"chInfo\":\"healthstep\"" +
                "}" +
                "]";
        return RequestManager.requestString("alipay.antsports.club.train.queryTrainItem", args);
    }

    // 训练好友
    // 对应文档：alipay.antsports.club.train.trainMember
    // 请求：{"apiVersion":"energy","bizId":"...","chInfo":"healthstep","itemType":"skate","memberId":"...","originBossId":"..."}
    public static String trainMember(String bizId, String itemType, String memberId, String originBossId) {
        String args = "[" +
                "{" +
                "\"apiVersion\":\"energy\"," +
                "\"bizId\":\"" + bizId + "\"," +
                "\"chInfo\":\"healthstep\"," +
                "\"itemType\":\"" + itemType + "\"," +
                "\"memberId\":\"" + memberId + "\"," +
                "\"originBossId\":\"" + originBossId + "\"" +
                "}" +
                "]";
        return RequestManager.requestString("alipay.antsports.club.train.trainMember", args);
    }

    // 查询好友是否可抢
    // 对应文档：alipay.antsports.club.ranking.queryMemberPriceRanking
    // 请求：{"apiVersion":"energy","buyMember":true,"chInfo":"healthstep","coinBalance":26}
    public static String queryMemberPriceRanking(int coinBalance) {
        String args = "[" +
                "{" +
                "\"apiVersion\":\"energy\"," +
                "\"buyMember\":true," +
                "\"chInfo\":\"healthstep\"," +
                "\"coinBalance\":" + coinBalance +
                "}" +
                "]";
        return RequestManager.requestString("alipay.antsports.club.ranking.queryMemberPriceRanking", args);
    }

    // 查询玩家
    // 对应文档：alipay.antsports.club.trade.queryClubMember
    public static String queryClubMember(String memberId, String originBossId) {
        String args = "[" +
                "{" +
                "\"apiVersion\":\"energy\"," +
                "\"chInfo\":\"healthstep\"," +
                "\"memberId\":\"" + memberId + "\"," +
                "\"originBossId\":\"" + originBossId + "\"" +
                "}" +
                "]";
        return RequestManager.requestString("alipay.antsports.club.trade.queryClubMember", args);
    }

    // 抢好友
    // 对应文档：alipay.antsports.club.trade.buyMember
    // 请求：{"apiVersion":"energy","chInfo":"healthstep","currentBossId":"...","memberId":"...","originBossId":"...","priceInfo":{...},"roomId":"..."}
    public static String buyMember(String currentBossId, String memberId, String originBossId, String priceInfo, String roomId) {
        String requestData = "[" +
                "{" +
                "\"apiVersion\":\"energy\"," +
                "\"chInfo\":\"healthstep\"," +
                "\"currentBossId\":\"" + currentBossId + "\"," +
                "\"memberId\":\"" + memberId + "\"," +
                "\"originBossId\":\"" + originBossId + "\"," +
                "\"priceInfo\":" + priceInfo + "," +
                "\"roomId\":\"" + roomId + "\"" +
                "}" +
                "]";
        return RequestManager.requestString("alipay.antsports.club.trade.buyMember", requestData);
    }
    public class NeverlandRpcCall {

        /**
         * 健康岛 - 查询签到状态
         * <p>
         * RPC: com.alipay.neverland.biz.rpc.querySign
         * <p>
         * 请求示例：
         * [
         * {
         * "signType": 3,
         * "source": "jkdsportcard"
         * }
         * ]
         * <p>
         * 响应示例：
         * {
         * "success": true,
         * "data": {
         * "continuousSignInfo": {
         * "bizDate": 1764463885808,
         * "continuitySignedDayCount": 0,
         * "signedToday": false,
         * "signInNodes": [
         * {
         * "continuitySignDayAtOnce": 1,
         * "rewardAmount": 5,
         * "rewardType": "ENERGY",
         * "signed": false,
         * "todayFlg": true
         * },
         * {
         * "continuitySignDayAtOnce": 2,
         * "rewardAmount": 8,
         * "rewardType": "ENERGY",
         * "signed": false,
         * "todayFlg": false,
         * "tomorrowNodeFlg": true
         * }
         * ...
         * ]
         * },
         * "signCount": 0,
         * "type": "LIAN_XU_QIAN_DAO"
         * }
         * }
         *
         * @param signType 签到类型（健康岛固定为 3）
         * @param source   来源（固定 "jkdsportcard"）
         * @return RPC 返回的 JSON 字符串
         */
        public static String querySign(int signType, String source) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.querySign",
                    "[{\"signType\":" + signType + "," +
                            "\"source\":\"" + source + "\"}]"
            );
        }

        /**
         * 健康岛 - 执行签到
         * <p>
         * RPC: com.alipay.neverland.biz.rpc.takeSign
         * <p>
         * 请求示例：
         * [
         * {
         * "signType": 3,
         * "source": "jkdsportcard"
         * }
         * ]
         * <p>
         * 响应示例：
         * {
         * "success": true,
         * "data": {
         * "continuousDoSignInVO": {
         * "rewardAmount": 5,
         * "rewardType": "ENERGY"
         * },
         * "continuousSignInfo": {
         * "signedToday": true,
         * "continuitySignedDayCount": 1,
         * "signInNodes": [
         * {
         * "continuitySignDayAtOnce": 1,
         * "rewardAmount": 5,
         * "rewardType": "ENERGY",
         * "signed": true,
         * "todayFlg": true
         * },
         * {
         * "continuitySignDayAtOnce": 2,
         * "rewardAmount": 8,
         * "rewardType": "ENERGY",
         * "signed": false,
         * "tomorrowNodeFlg": true
         * }
         * ...
         * ]
         * }
         * }
         * }
         *
         * @param signType 签到类型（健康岛固定为 3）
         * @param source   来源（固定 "jkdsportcard"）
         * @return RPC 返回 JSON 字符串
         */
        public static String takeSign(int signType, String source) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.takeSign",
                    "[{\"signType\":" + signType + "," +
                            "\"source\":\"" + source + "\"}]"
            );
        }

        /**
         * 健康岛 - 查询泡泡任务
         * <p>
         * RPC：com.alipay.neverland.biz.rpc.queryBubbleTask
         * <p>
         * 请求示例：
         * [
         * {
         * "source": "jkdsportcard",
         * "sportsAuthed": true
         * }
         * ]
         * <p>
         * 响应示例（节选）：
         * {
         * "success": true,
         * "data": {
         * "bubbleTaskVOS": [
         * {
         * "taskId": "adTask300",
         * "title": "早晚打卡",
         * "energyNum": "5",
         * "initState": false
         * },
         * {
         * "taskId": "OFFLINE_BALL",
         * "title": "离线奖励",
         * "energyNum": "15"
         * }
         * ]
         * }
         * }
         *
         * @return RPC 返回的 JSON 字符串
         */
        public static String queryBubbleTask() {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryBubbleTask",
                    "[{\"source\":\"jkdsportcard\",\"sportsAuthed\":true}]"
            );
        }

        /**
         * 健康岛 - 收取泡泡能量
         * <p>
         * RPC：com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
         * <p>
         * 请求示例：
         * [
         * {
         * "medEnergyBallInfoRecordIds":[
         * "f304daa3bcac17f3c2e55d6c6ed73ddd",
         * "2f7901ab2ef000cb575f297a4049bb94"
         * ],
         * "pickAllEnergyBall": true,
         * "source": "jkdsportcard"
         * }
         * ]
         * <p>
         * 响应示例：
         * {
         * "success": true,
         * "data": {
         * "balance": "19118",
         * "changeAmount": "90"
         * }
         * }
         *
         * @param ids 泡泡 RecordId 列表
         * @return RPC 返回的 JSON 字符串
         */
        public static String pickBubbleTaskEnergy(List<String> ids) {

            StringBuilder sb = new StringBuilder();
            sb.append("[{\"medEnergyBallInfoRecordIds\":[");

            for (int i = 0; i < ids.size(); i++) {
                sb.append("\"").append(ids.get(i)).append("\"");
                if (i < ids.size() - 1) sb.append(",");
            }

            sb.append("],\"pickAllEnergyBall\":true,\"source\":\"jkdsportcard\"}]");

            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy",
                    sb.toString()
            );
        }

        /**
         * 健康岛 - 获取任务大厅列表
         * <p>
         * RPC: com.alipay.neverland.biz.rpc.queryTaskCenter
         * <p>
         * 请求示例：
         * [
         *   {
         *     "apDid":"6b30jO17Z6Wbr2ggRytFxB09hZdhixfSekjytgi9Ytc=",
         *     "cityCode":"",
         *     "deviceLevel":"high",
         *     "newGame":0,
         *     "source":"jkdsportcard"
         *   }
         * ]
         * <p>
         * 响应示例（节选）：
         * {
         *   "success": true,
         *   "data": {
         *     "taskCenterTaskVOS": [
         *       {
         *         "taskId":"AP601235652",
         *         "taskType":"LIGHT_TASK",
         *         "taskStatus":"SIGNUP_COMPLETE",
         *         "title":"浏览30秒商品橱窗",
         *         "jumpLink":"...bizId=6042591d9bf54cccb1526ced12867e93..."
         *       },
         *       {
         *         "taskId":"AP15304824",
         *         "taskType":"PROMOKERNEL_TASK",
         *         "title":"逛1秒打卡抽现金",
         *         "jumpLink":"alipays://...&bizId=6042591d9bf54cccb1526ced12867e93..."
         *       }
         *     ]
         *   }
         * }
         *
         * @return RPC 返回的 JSON 字符串
         */
        public static String queryTaskCenter() { //这里的apDid暂时写死，应该不会变
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryTaskCenter",
                    "[{\"apDid\":\"" + "6b30jO17Z6Wbr2ggRytFxB09hZdhixfSekjytgi9Ytc=" + "\","
                            + "\"cityCode\":\"\","
                            + "\"deviceLevel\":\"high\","
                            + "\"newGame\":0,"
                            + "\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 查询指定类型任务信息  打开任务只中心弹出的浏览任务，   使用energyReceive进行领取
         * RPC: com.alipay.neverland.biz.rpc.queryTaskInfo
         *
         * @param source  来源，例如 "health-island"
         * @param type    任务类型，例如 "LIGHT_FEEDS_TASK"
         * @return RPC 返回 JSON 字符串
         *  * RPC: com.alipay.neverland.biz.rpc.queryTaskInfo
         *  *
         *          * 请求示例：
         *                 * [
         *                 *   {"source":"health-island","type":"LIGHT_FEEDS_TASK"}
         *  * ]
         *          *
         *          * @param source 来源
         *  * @param type   任务类型
         *  * @return RPC 返回的 JSON 字符串
         *
         */

        public static String queryTaskInfo(String source, String type) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryTaskInfo",
                    "[{\"source\":\"" + source + "\",\"type\":\"" + type + "\"}]"
            );
        }

        /**
         * 领取能量任务奖励
         * RPC: com.alipay.neverland.biz.rpc.energyReceive
         *
         * @param encryptValue 任务唯一标识
         * @param energyNum    能量数量
         * @param type         任务类型，例如 "LIGHT_FEEDS_TASK"
         * @return RPC 返回 JSON 字符串
         */
        public static String energyReceive(String encryptValue, int energyNum, String type, String lightTaskId) {
            // 动态构建 JSON 参数，如果有 lightTaskId 则添加该字段
            StringBuilder paramJson = new StringBuilder("[{");

            paramJson.append("\"encryptValue\":\"").append(encryptValue).append("\",")
                    .append("\"energyNum\":").append(energyNum).append(",")
                    .append("\"source\":\"jkdsportcard\",")
                    .append("\"type\":\"").append(type).append("\"");

            // 只有在 lightTaskId 存在时，才加入该字段
            if (lightTaskId != null && !lightTaskId.isEmpty()) {
                paramJson.append(",\"lightTaskId\":\"").append(lightTaskId).append("\"");
            }

            paramJson.append("}]");

            // 发起请求
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.energyReceive",
                    paramJson.toString()
            );
        }

        /**
         * 健康岛 - 提交 Neverland 任务（用于 PROMOKERNEL_TASK 无 bizId 或特殊任务）
         * <p>
         *     //实测 {
         *     "scene": "MED_TASK_HALL",
         *     "source": "jkdsportcard",
         *     "taskType": "ADD_HEAD_TASK"
         * } 这种也可以
         * RPC: com.alipay.neverland.biz.rpc.taskSend
         * <p>
         * 请求示例：
         * [
         *   { ... task json from taskCenterTaskVOS ... }
         * ]
         * <p>
         * 响应示例（节选）：
         * { "success": true, "data": {} }
         *
         * @param taskObj 来自 taskCenterTaskVOS 的 task JSONObject
         * @return RPC 返回的 JSON 字符串
         */
        public static String taskSend(JSONObject taskObj) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.taskSend",
                    "[" + taskObj.toString() + "]"
            );
        }


        public static String taskReceive(JSONObject taskObj) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.taskReceive",
                    "[" + taskObj.toString() + "]"
            );
        }

        /**
         * 广告任务 - 完成任务
         * <p>
         * RPC: com.alipay.adtask.biz.mobilegw.service.task.finish
         * <p>
         * 请求示例：
         * [
         *   {"bizId":"6042591d9bf54cccb1526ced12867e93"}
         * ]
         * <p>
         * 响应示例：
         * {
         *   "success": true,
         *   "bizId":"6042591d9bf54cccb1526ced12867e93",
         *   "extendInfo": {
         *     "rewardInfo": { "rewardAmount":"10", "rewardTypeName":"无权益" }
         *   }
         * }
         *
         * @param bizId 广告任务的 bizId
         * @return RPC 返回的 JSON 字符串
         */
        public static String finish(String bizId) {
            return RequestManager.requestString(
                    "com.alipay.adtask.biz.mobilegw.service.task.finish",
                    "[{\"bizId\":\"" + bizId + "\"}]"
            );
        }

        /**
         * 健康岛 - 查询地图列表
         * RPC: com.alipay.neverland.biz.rpc.queryMapList
         * @return RPC 返回 JSON 字符串
         */
        public static String queryMapList() {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryMapList",
                    "[{\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 健康岛 - 查询地图详情（旧版）
         * RPC: com.alipay.neverland.biz.rpc.queryMapInfo
         * 用于获取 buildingEnergyProcess / buildingEnergyFinal / mapStatus 等信息
         */
        public static String queryMapInfo(String mapId, String branchId) throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("branchId", branchId);
            obj.put("drilling", false);
            obj.put("mapId", mapId);
            obj.put("source", "jkdsportcard");

            JSONArray arr = new JSONArray();
            arr.put(obj);
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryMapInfo",
                    arr.toString()
            );
        }

        /**
         * 健康岛 - 查询地图详情（新接口）
         * RPC: com.alipay.neverland.biz.rpc.queryMapInfoNew
         * 用于获取 buildingEnergyProcess / buildingEnergyFinal / mapStatus 等信息
         */
        public static String queryMapInfoNew(String mapId) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryMapInfoNew",
                    "[{\"mapId\":\"" + mapId + "\",\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 健康岛 - 查询基础信息（用于判断是否新游戏/建造模式）
         * RPC: com.alipay.neverland.biz.rpc.queryBaseinfo
         * 示例中 data.newGame = true 表示为新游戏，需要走建造逻辑
         */
        public static String queryBaseinfo() {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryBaseinfo",
                    "[{\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 健康岛 - 建造建筑
         * RPC: com.alipay.neverland.biz.rpc.build
         *
         * multiNum: 1-10，10倍约等于消耗 50 能量
         */
        public static String build(String branchId, String mapId, int multiNum) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.build",
                    "[{\"branchId\":\"" + branchId + "\",\"mapId\":\"" + mapId + "\",\"multiNum\":" + multiNum + ",\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 查询单个地图奖励详情
         * RPC: com.alipay.neverland.biz.rpc.queryMapDetail
         * @param mapId 地图 ID
         * @return RPC 返回 JSON 字符串
         */
        public static String queryMapDetail(String mapId) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryMapDetail",
                    "[{\"mapId\":\"" + mapId + "\",\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 领取地图关卡奖励
         * RPC: com.alipay.neverland.biz.rpc.mapStageReward
         *
         * @param branchId 分支 ID（如 MASTER）
         * @param level    关卡等级
         * @param mapId    地图 ID（如 MM13）
         * @return RPC 返回 JSON 字符串
         */
        public static String mapStageReward(String branchId, int level, String mapId) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.mapStageReward",
                    "[{" +
                            "\"branchId\":\"" + branchId + "\"," +
                            "\"level\":" + level + "," +
                            "\"mapId\":\"" + mapId + "\"," +
                            "\"source\":\"jkdsportcard\"" +
                            "}]"
            );
        }

        /**
         * 领取奖励
         * RPC: com.alipay.neverland.biz.rpc.mapChooseReward
         * @param branchId 分支 ID（通常 MASTER）
         * @param mapId 地图 ID
         * @param rewardId 奖励 ID
         * @return RPC 返回 JSON 字符串
         */
        public static String chooseReward(String branchId, String mapId, String rewardId) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.mapChooseReward",
                    "[{\"branchId\":\"" + branchId + "\",\"channel\":\"jkdsportcard\",\"mapId\":\"" + mapId + "\",\"rewardId\":\"" + rewardId + "\",\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 选择地图
         * RPC: com.alipay.neverland.biz.rpc.mapChooseFree
         * @param branchId 分支 ID（通常 MASTER）
         * @param mapId 地图 ID
         * @return RPC 返回 JSON 字符串
         */
        public static String chooseMap(String branchId, String mapId) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.mapChooseFree",
                    "[{\"branchId\":\"" + branchId + "\",\"mapId\":\"" + mapId + "\",\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 行走
         * RPC: com.alipay.neverland.biz.rpc.walkGrid
         * @param branchId 分支 ID（通常 MASTER）
         * @param mapId 地图 ID
         * @param drilling 是否钻探（通常 false）
         * @return RPC 返回 JSON 字符串
         */
        public static String walkGrid(String branchId, String mapId, boolean drilling) {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.walkGrid",
                    "[{\"branchId\":\"" + branchId + "\",\"mapId\":\"" + mapId + "\",\"drilling\":" + drilling + ",\"source\":\"jkdsportcard\"}]"
            );
        }

        /**
         * 查询用户能量
         * RPC: com.alipay.neverland.biz.rpc.queryUserAccount
         * @return RPC 返回 JSON 字符串
         */
        public static String queryUserEnergy() {
            return RequestManager.requestString(
                    "com.alipay.neverland.biz.rpc.queryUserAccount",
                    "[{\"source\":\"jkdsportcard\"}]"
            );
        }
    }
}