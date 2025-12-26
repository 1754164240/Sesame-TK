package fansirsqi.xposed.sesame.task.antStall;
import org.json.JSONArray;
import org.json.JSONObject;
import fansirsqi.xposed.sesame.hook.RequestManager;
import java.util.UUID;
/**
 * @author Constanline
 * @since 2023/08/22
 */
public class AntStallRpcCall {
    //0.1.2406171355.5
    //0.1.2312271038.27
    private static final String VERSION = "0.1.2512051721.49";
    public static String home() {
        return RequestManager.requestString("com.alipay.antstall.self.home",
                "[{\"arouseAppParams\":{},\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String settle(String assetId, int settleCoin) {
        return RequestManager.requestString("com.alipay.antstall.self.settle",
                "[{\"assetId\":\"" + assetId + "\",\"coinType\":\"MASTER\",\"settleCoin\":" + settleCoin +
                        ",\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String shopList() {
        return RequestManager.requestString("com.alipay.antstall.shop.list",
                "[{\"freeTop\":false,\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String preOneKeyClose() {
        return RequestManager.requestString("com.alipay.antstall.user.shop.close.preOneKey",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String oneKeyClose() {
        return RequestManager.requestString("com.alipay.antstall.user.shop.oneKeyClose",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String preShopClose(String shopId, String billNo) {
        return RequestManager.requestString("com.alipay.antstall.user.shop.close.pre",
                "[{\"billNo\":\"" + billNo + "\",\"shopId\":\"" + shopId +
                        "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String shopClose(String shopId) {
        return RequestManager.requestString("com.alipay.antstall.user.shop.close",
                "[{\"shopId\":\"" + shopId + "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String oneKeyOpen() {
        return RequestManager.requestString("com.alipay.antstall.user.shop.oneKeyOpen",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String shopOpen(String friendSeatId, String friendUserId, String shopId) {
        return RequestManager.requestString("com.alipay.antstall.user.shop.open",
                "[{\"friendSeatId\":\"" + friendSeatId + "\",\"friendUserId\":\"" + friendUserId + "\",\"shopId\":\"" +
                        shopId + "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String rankCoinDonate() {
        return RequestManager.requestString("com.alipay.antstall.rank.coin.donate",
                "[{\"source\":\"ANTFARM\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String friendHome(String userId) {
        return RequestManager.requestString("com.alipay.antstall.friend.home",
                "[{\"arouseAppParams\":{},\"friendUserId\":\"" + userId +
                        "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String taskList() {
        return RequestManager.requestString("com.alipay.antstall.task.list",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String signToday() {
        return RequestManager.requestString("com.alipay.antstall.sign.today",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String finishTask(String outBizNo, String taskType) {
        return RequestManager.requestString("com.alipay.antiep.finishTask",
                "[{\"outBizNo\":\"" + outBizNo +
                        "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTSTALL_TASK\",\"source\":\"AST\",\"systemType\":\"android\",\"taskType\":\"" +
                        taskType + "\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String xlightPlugin() {
        return RequestManager.requestString("com.alipay.adexchange.ad.facade.xlightPlugin",
                "[{\"positionRequest\":{\"extMap\":{\"xlightPlayInstanceId\":\"300004\"},\"referInfo\":{}," +
                        "\"spaceCode\":\"ANT_FARM_NEW_VILLAGE\"},\"sdkPageInfo\":{\"adComponentType\":\"FEEDS\"," +
                        "\"adComponentVersion\":\"4.11.13\",\"enableFusion\":true,\"networkType\":\"WIFI\"," +
                        "\"pageFrom\":\"ch_url-https://68687809.h5app.alipay.com/www/game.html\",\"pageNo\":1," +
                        "\"pageUrl\":\"https://render.alipay.com/p/yuyan/180020010001256918/multi-stage-task.html?caprMode=sync&spaceCodeFeeds=ANT_FARM_NEW_VILLAGE&usePlayLink=true&xlightPlayInstanceId=300004\"," +
                        "\"session\":\"u_54b721d9fffd6_1904b8eba8f\",\"unionAppId\":\"2060090000304921\"," +
                        "\"usePlayLink\":\"true\",\"xlightSDKType\":\"h5\",\"xlightSDKVersion\":\"4.11.13\"}}]");
    }
    public static String finish(String playBizId, JSONObject jsonObject) {
        return RequestManager.requestString("com.alipay.adtask.biz.mobilegw.service.interaction.finish",
                "[{\"extendInfo\":{\"iepTaskSceneCode\":\"ANTSTALL_TASK\",\"iepTaskType\":" +
                        "\"ANTSTALL_XLIGHT_VARIABLE_AWARD\"},\"playBizId\":\"" + playBizId +
                        "\",\"playEventInfo\":" + jsonObject + ",\"source\":\"adx\" }]");
    }
    public static String queryCallAppSchema(String sceneCode) {
        return RequestManager.requestString("alipay.antmember.callApp.queryCallAppSchema",
                "[{\"sceneCode\":\"" + sceneCode + "\" }]");
    }
    public static String receiveTaskAward(String taskType) {
        return RequestManager.requestString("com.alipay.antiep.receiveTaskAward",
                "[{\"ignoreLimit\":true,\"requestType\":\"RPC\",\"sceneCode\":\"ANTSTALL_TASK\",\"source\":\"AST\",\"systemType\":\"android\",\"taskType\":\"" +
                        taskType + "\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String taskFinish(String taskType) {
        return RequestManager.requestString("com.alipay.antstall.task.finish",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"taskType\":\"" + taskType + "\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String taskAward(String amount, String prizeId, String taskType) {
        return RequestManager.requestString("com.alipay.antstall.task.award",
                "[{\"amount\":" + amount + ",\"prizeId\":\"" + prizeId +
                        "\",\"source\":\"search\",\"systemType\":\"android\",\"taskType\":\""
                        + taskType + "\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String taskBenefit() {
        return RequestManager.requestString("com.alipay.antstall.task.benefit",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String collectManure() {
        return RequestManager.requestString("com.alipay.antstall.manure.collectManure",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String queryManureInfo() {
        return RequestManager.requestString("com.alipay.antstall.manure.queryManureInfo",
                "[{\"queryManureType\":\"ANTSTALL\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" +
                        VERSION + "\"}]");
    }
    public static String projectList() {
        return RequestManager.requestString("com.alipay.antstall.project.list",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String projectDetail(String projectId) {
        return RequestManager.requestString("com.alipay.antstall.project.detail",
                "[{\"projectId\":\"" + projectId +
                        "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String projectDonate(String projectId) {
        return RequestManager.requestString("com.alipay.antstall.project.donate",
                "[{\"bizNo\":\"" + UUID.randomUUID().toString() + "\",\"projectId\":\"" + projectId +
                        "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String roadmap() {
        return RequestManager.requestString("com.alipay.antstall.village.roadmap",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String nextVillage() {
        return RequestManager.requestString("com.alipay.antstall.user.ast.next.village",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String rankInviteRegister() {
        return RequestManager.requestString("com.alipay.antstall.rank.invite.register",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String friendInviteRegister(String friendUserId) {
        return RequestManager.requestString("com.alipay.antstall.friend.invite.register",
                "[{\"friendUserId\":\"" + friendUserId
                        + "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    /* 助力好友 */
    public static String shareP2P() {
        return RequestManager.requestString("com.alipay.antiep.shareP2P",
                "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTSTALL_P2P_SHARER\",\"source\":\"ANTSTALL\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String achieveBeShareP2P(String shareId) {
        return RequestManager.requestString("com.alipay.antiep.achieveBeShareP2P",
                "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTSTALL_P2P_SHARER\",\"shareId\":\""
                        + shareId
                        + "\",\"source\":\"ANTSTALL\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String shopSendBackPre(String billNo, String seatId, String shopId, String shopUserId) {
        return RequestManager.requestString("com.alipay.antstall.friend.shop.sendback.pre",
                "[{\"billNo\":\"" + billNo + "\",\"seatId\":\"" + seatId + "\",\"shopId\":\"" + shopId
                        + "\",\"shopUserId\":\"" + shopUserId
                        + "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String shopSendBack(String seatId) {
        return RequestManager.requestString("com.alipay.antstall.friend.shop.sendback",
                "[{\"seatId\":\"" + seatId
                        + "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String rankInviteOpen() {
        return RequestManager.requestString("com.alipay.antstall.rank.invite.open",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String oneKeyInviteOpenShop(String friendUserId, String mySeatId) {
        return RequestManager.requestString("com.alipay.antstall.user.shop.oneKeyInviteOpenShop",
                "[{\"friendUserId\":\"" + friendUserId + "\",\"mySeatId\":\"" + mySeatId
                        + "\",\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String dynamicLoss() {
        return RequestManager.requestString("com.alipay.antstall.dynamic.loss",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\"" + VERSION + "\"}]");
    }
    public static String throwManure(JSONArray dynamicList) {
        return RequestManager.requestString("com.alipay.antstall.manure.throwManure",
                "[{\"dynamicList\":" + dynamicList
                        + ",\"sendMsg\":false,\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    public static String settleReceivable() {
        return RequestManager.requestString("com.alipay.antstall.self.settle.receivable",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    /**
     * 查找下一个可以贴罚单的好友
     *
     */
    public static String nextTicketFriend() {
        return RequestManager.requestString("com.alipay.antstall.friend.nextTicketFriend",
                "[{\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
    /**
     * 贴罚单
     */
    public static String ticket(String billNo, String seatId, String shopId, String shopUserId, String seatUserId) {
        return RequestManager.requestString("com.alipay.antstall.friend.paste.ticket",
                "[{\"billNo\":\"" + billNo + "\",\"seatId\":\"" + seatId + "\",\"shopId\":\"" + shopId
                        + "\",\"shopUserId\":\"" + shopUserId + "\",\"seatUserId\": \"" + seatUserId + "\","
                        + "\"source\":\"search\",\"systemType\":\"android\",\"version\":\""
                        + VERSION + "\"}]");
    }
}