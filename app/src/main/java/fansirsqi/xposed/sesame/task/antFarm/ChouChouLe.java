package fansirsqi.xposed.sesame.task.antFarm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import fansirsqi.xposed.sesame.util.GlobalThreadPools;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.ResChecker;

/**
 * 小鸡抽抽乐功能类
 */
public class ChouChouLe {
    private static final String TAG = ChouChouLe.class.getSimpleName();

    /** 任务状态枚举 */
    public enum TaskStatus {
        TODO, FINISHED, RECEIVED, DONATION
    }

    /**
     * 任务信息结构体
     */
    private static class TaskInfo {
        String taskStatus;
        String title;
        String taskId;
        String innerAction;
        int rightsTimes;
        int rightsTimesLimit;
        String awardType;
        int awardCount;
        String targetUrl;

        /**
         * 获取剩余次数
         */
        int getRemainingTimes() {
            return Math.max(0, rightsTimesLimit - rightsTimes);
        }
    }

    /**
     * 抽抽乐主入口
     * 返回值判断是否真的完成任务，是否全部执行完毕且无剩余（任务已做、奖励已领、抽奖已完）
     */
    public boolean chouchoule() {
        boolean allFinished = true;
        try {
            String response = AntFarmRpcCall.queryLoveCabin(UserMap.INSTANCE.getCurrentUid());
            JSONObject jo = new JSONObject(response);
            if (!ResChecker.checkRes(TAG, jo)) {
                return false;
            }

            JSONObject drawMachineInfo = jo.optJSONObject("drawMachineInfo");
            if (drawMachineInfo == null) {
                Log.error(TAG, "抽抽乐🎁[获取抽抽乐活动信息失败]");
                return false;
            }

            // 执行普通抽抽乐
            if (drawMachineInfo.has("dailyDrawMachineActivityId")) {
                allFinished &= doChouchoule("dailyDraw");
            }

            // 执行IP抽抽乐
            if (drawMachineInfo.has("ipDrawMachineActivityId")) {
                allFinished &= doChouchoule("ipDraw");
            }

            return allFinished;
        } catch (Throwable t) {
            Log.printStackTrace("chouchoule err:", t);
            return false;
        }
    }

    /**
     * 执行抽抽乐
     *
     * @param drawType "dailyDraw" 或 "ipDraw"
     * 返回是否该类型已全部完成
     */
    private boolean doChouchoule(String drawType) {
        boolean doubleCheck;
        try {
            do {
                doubleCheck = false;
                JSONObject jo = new JSONObject(AntFarmRpcCall.chouchouleListFarmTask(drawType));
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error(TAG, drawType.equals("ipDraw") ? "IP抽抽乐任务列表获取失败" : "抽抽乐任务列表获取失败");
                    return false;
                }

                JSONArray farmTaskList = jo.getJSONArray("farmTaskList");
                List<TaskInfo> tasks = parseTasks(farmTaskList);

                for (TaskInfo task : tasks) {
                    if (TaskStatus.FINISHED.name().equals(task.taskStatus)) {
                        // 检查饲料上限
//                        if (task.awardType.equals("ALLPURPOSE") &&
//                                task.awardCount + AntFarm.foodStock > AntFarm.foodStockLimit) {
//                            Log.record(TAG, "抽抽乐任务[" + task.title + "]的奖励领取后会使饲料超出上限，暂不领取");
//                            continue;
//                        }
                        if (receiveTaskAward(drawType, task.taskId)) {
                            GlobalThreadPools.sleepCompat(300L);
                            doubleCheck = true;
                        }
                    } else if (TaskStatus.TODO.name().equals(task.taskStatus)) {
                        if (task.getRemainingTimes() > 0 && !"DONATION".equals(task.innerAction))  {
                            if (doChouTask(drawType, task)) {
                                doubleCheck = true;
                            }
                        }
                    }
                }
            } while (doubleCheck);
        } catch (Throwable t) {
            Log.printStackTrace("doChouchoule err:", t);
            return false;
        }

        // 执行抽奖
        boolean drawSuccess;
        if ("ipDraw".equals(drawType)) {
            drawSuccess = handleIpDraw();
        } else {
            drawSuccess = handleDailyDraw();
        }

        if (!drawSuccess) return false;

        // 最后校验是否真的全部完成
        return verifyFinished(drawType);
    }

    /*
     校验是否还有未完成的任务或抽奖
     */
    private boolean verifyFinished(String drawType) {
        try {
            // 校验任务
            JSONObject jo = new JSONObject(AntFarmRpcCall.chouchouleListFarmTask(drawType));
            if (!ResChecker.checkRes(TAG, jo)) return false;

            JSONArray farmTaskList = jo.getJSONArray("farmTaskList");
            List<TaskInfo> tasks = parseTasks(farmTaskList);
            for (TaskInfo task : tasks) {
                if (TaskStatus.FINISHED.name().equals(task.taskStatus)) {
                    return false;
                } else if (TaskStatus.TODO.name().equals(task.taskStatus)) {
                    // 还有剩余次数且不是捐赠任务
                    if (task.getRemainingTimes() > 0 && !"DONATION".equals(task.innerAction)) {
                        return false;
                    }
                }
            }

            // 校验抽奖次数
            JSONObject drawJo;
            if ("ipDraw".equals(drawType)) {
                drawJo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity_New("ipDrawMachine", "dailyDrawMachine"));
            } else {
                drawJo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity_New("dailyDrawMachine", "ipDrawMachine"));
            }
            if (!ResChecker.checkRes(TAG, drawJo)) return false;
            int drawTimes = drawJo.optInt("drawTimes", 0);
            if (drawTimes > 0) return false;

            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 解析任务列表
     */
    private List<TaskInfo> parseTasks(JSONArray array) throws Exception {
        List<TaskInfo> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            TaskInfo info = new TaskInfo();
            info.taskStatus = item.getString("taskStatus");
            info.title = item.getString("title");
            info.taskId = item.getString("bizKey");
            info.innerAction = item.optString("innerAction");
            info.rightsTimes = item.optInt("rightsTimes", 0);
            info.rightsTimesLimit = item.optInt("rightsTimesLimit", 0);
            info.awardType = item.optString("awardType");
            info.awardCount = item.optInt("awardCount", 0);
            info.targetUrl = item.optString("targetUrl", "");
            list.add(info);
        }
        return list;
    }

    /**
     * 执行任务
     */
    private boolean doChouTask(String drawType, TaskInfo task) {
        try {
            String taskName = drawType.equals("ipDraw") ? "IP抽抽乐" : "抽抽乐";

            // 特殊任务：浏览广告
            if (task.taskId.equals("SHANGYEHUA_DAILY_DRAW_TIMES") ||
                    task.taskId.equals("SHANGYEHUA_IP_DRAW_TIMES")) {
                return handleAdTask(drawType, task);
            }

            // 普通任务
            if (task.title.equals("消耗饲料换机会")) {
                if (AntFarm.foodStock < 90) {
                    Log.record(TAG, "饲料余量(" + AntFarm.foodStock + "g)少于90g，跳过任务: " + task.title);
                    return false; // 返回 false 避免 doubleCheck，且不执行后续 RPC
                }
            }
            String s = AntFarmRpcCall.chouchouleDoFarmTask(drawType, task.taskId);
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG, jo)) {
                Log.farm(taskName + "🧾️[任务: " + task.title + "]");
                if(task.title.equals("消耗饲料换机会")) {
                    GlobalThreadPools.sleepCompat(300L);
                } else {
                    GlobalThreadPools.sleepCompat(1000L);
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            Log.printStackTrace("执行抽抽乐任务 err:", t);
            return false;
        }
    }

    /**
     * 处理广告任务
     */
    private boolean handleAdTask(String drawType, TaskInfo task) {
        try {
            String referToken = AntFarm.loadAntFarmReferToken();
            String taskSceneCode = drawType.equals("ipDraw") ?
                    "ANTFARM_IP_DRAW_TASK" : "ANTFARM_DAILY_DRAW_TASK";

            // 如果有referToken，尝试执行广告任务
            if (referToken != null && !referToken.isEmpty()) {
                String response = AntFarmRpcCall.xlightPlugin(referToken, "HDWFCJGXNZW_CUSTOM_20250826173111");
                JSONObject jo = new JSONObject(response);

                if (jo.optString("retCode").equals("0")) {
                    JSONObject resData = jo.getJSONObject("resData");
                    JSONArray adList = resData.optJSONArray("adList");

                    if (adList != null && adList.length() > 0) {
                        // 检查是否有猜一猜任务
                        JSONObject playingResult = resData.optJSONObject("playingResult");
                        if (playingResult != null &&
                                "XLIGHT_GUESS_PRICE_FEEDS".equals(playingResult.optString("playingStyleType"))) {
                            return handleGuessTask(drawType, task, adList, playingResult);
                        }
                    }
                }
                Log.record(TAG, "浏览广告任务[没有可用广告或不支持，使用普通完成方式]");
            }else {
                Log.record(TAG, "浏览广告任务[没有可用Token，请手动看一起广告]");
            }

            // 没有token或广告任务失败，使用普通完成方式
            String outBizNo = task.taskId + "_" + System.currentTimeMillis() + "_" +
                    Integer.toHexString((int)(Math.random() * 0xFFFFFF));
            String response = AntFarmRpcCall.finishTask(task.taskId, taskSceneCode, outBizNo);
            JSONObject jo = new JSONObject(response);

            if (jo.optBoolean("success", false)) {
                Log.farm((drawType.equals("ipDraw") ? "IP抽抽乐" : "抽抽乐") +
                        "🧾️[任务: " + task.title + "]");
                GlobalThreadPools.sleepCompat(3000L);
                return true;
            }
            return false;
        } catch (Throwable t) {
            Log.printStackTrace("处理广告任务 err:", t);
            return false;
        }
    }

    /**
     * 处理猜一猜任务
     */
    private boolean handleGuessTask(String drawType, TaskInfo task,
                                    JSONArray adList, JSONObject playingResult) {
        try {
            // 找到正确价格
            int correctPrice = -1;
            String targetAdId = "";

            for (int i = 0; i < adList.length(); i++) {
                JSONObject ad = adList.getJSONObject(i);
                String schemaJson = ad.optString("schemaJson", "");
                if (!schemaJson.isEmpty()) {
                    JSONObject schema = new JSONObject(schemaJson);
                    int price = schema.optInt("price", -1);
                    if (price > 0) {
                        if (correctPrice == -1 || Math.abs(price - 11888) < Math.abs(correctPrice - 11888)) {
                            correctPrice = price;
                            targetAdId = ad.optString("adId", "");
                        }
                    }
                }
            }

            if (correctPrice > 0 && !targetAdId.isEmpty()) {
                // 提交猜价格结果
                String playBizId = playingResult.optString("playingBizId", "");
                JSONObject eventRewardDetail = playingResult.optJSONObject("eventRewardDetail");
                if (eventRewardDetail != null) {
                    JSONArray eventRewardInfoList = eventRewardDetail.optJSONArray("eventRewardInfoList");
                    if (eventRewardInfoList != null && eventRewardInfoList.length() > 0) {
                        JSONObject playEventInfo = eventRewardInfoList.getJSONObject(0);

                        String taskSceneCode = drawType.equals("ipDraw") ?
                                "ANTFARM_IP_DRAW_TASK" : "ANTFARM_DAILY_DRAW_TASK";

                        String response = AntFarmRpcCall.finishAdTask(
                                playBizId, playEventInfo, task.taskId, taskSceneCode);
                        JSONObject jo = new JSONObject(response);

                        if (jo.optJSONObject("resData") != null &&
                                jo.getJSONObject("resData").optBoolean("success", false)) {
                            Log.farm((drawType.equals("ipDraw") ? "IP抽抽乐" : "抽抽乐") +
                                    "🧾️[猜价格任务完成: " + task.title + ", 猜中价格: " + correctPrice + "]");
                            GlobalThreadPools.sleepCompat(300L);
                            return true;
                        }
                    }
                }
            }

            Log.record(TAG, "猜价格任务[未找到合适价格，使用普通完成方式]");
            return false;
        } catch (Throwable t) {
            Log.printStackTrace("处理猜价格任务 err:", t);
            return false;
        }
    }

    /**
     * 领取任务奖励
     */
    private boolean receiveTaskAward(String drawType, String taskId) {
        try {
            String s = AntFarmRpcCall.chouchouleReceiveFarmTaskAward(drawType, taskId);
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG, jo)) {
                return true;
            }
        } catch (Throwable t) {
            Log.printStackTrace("receiveFarmTaskAward err:", t);
        }
        return false;
    }

    /**
     * 执行IP抽抽乐抽奖
     */
    private boolean handleIpDraw() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity_New(
                    "ipDrawMachine", "dailyDrawMachine"));
            if (!ResChecker.checkRes(TAG, jo)) {
                return false;
            }

            JSONObject activity = jo.optJSONObject("drawMachineActivity");
            if (activity == null) return true;
            long endTime = activity.optLong("endTime", 0);
            if (endTime > 0 && System.currentTimeMillis() > endTime) {
                Log.record(TAG, "该[" + activity.optString("activityId") + "]抽奖活动已结束");
                return true;
            }

            int drawTimes = jo.optInt("drawTimes", 0);
            boolean allSuccess = true;
            for (int i = 0; i < drawTimes; i++) {
                allSuccess &= drawPrize("IP抽抽乐", AntFarmRpcCall.drawMachineIP());
                GlobalThreadPools.sleepCompat(300L);
            }
            return allSuccess;
        } catch (Throwable t) {
            Log.printStackTrace("handleIpDraw err:", t);
            return false;
        }
    }

    /**
     * 执行普通抽抽乐抽奖
     */
    private boolean handleDailyDraw() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity_New(
                    "dailyDrawMachine", "ipDrawMachine"));
            if (!ResChecker.checkRes(TAG, jo)) {
                return false;
            }

            JSONObject activity = jo.optJSONObject("drawMachineActivity");
            if (activity == null) return true;
            long endTime = activity.optLong("endTime", 0);
            if (endTime > 0 && System.currentTimeMillis() > endTime) {
                Log.record(TAG, "该[" + activity.optString("activityId") + "]抽奖活动已结束");
                return true;
            }

            String activityId = activity.optString("activityId");
            int drawTimes = jo.optInt("drawTimes", 0);
            boolean allSuccess = true;
            for (int i = 0; i < drawTimes; i++) {
                allSuccess &= drawPrize("日常抽抽乐", AntFarmRpcCall.drawMachineDaily(activityId));
                GlobalThreadPools.sleepCompat(300L);
            }
            return allSuccess;
        } catch (Throwable t) {
            Log.printStackTrace("handleDailyDraw err:", t);
            return false;
        }
    }

    /**
     * 领取抽抽乐奖品
     *
     * @param prefix 抽奖类型前缀
     * @param response 服务器返回的结果
     * 返回是否领取成功
     */
    private boolean drawPrize(String prefix, String response) {
        try {
            JSONObject jo = new JSONObject(response);
            if (ResChecker.checkRes(TAG, jo)) {

                JSONObject prize = jo.optJSONObject("drawMachinePrize");
                if (prize != null) {
                    String title = prize.optString("title",
                            prize.optString("prizeName", "未知奖品"));
                    Log.farm(prefix + "🎁[领取: " + title +"]");
                } else {
                    Log.farm(prefix + "🎁[领取: 未知奖品]"+response);
                }
                return true;
            }
        } catch (Throwable t) {
            Log.printStackTrace("drawPrize err:", t);
        }
        return false;
    }
}
