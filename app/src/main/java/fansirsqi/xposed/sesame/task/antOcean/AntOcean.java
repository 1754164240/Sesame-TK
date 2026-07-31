package fansirsqi.xposed.sesame.task.antOcean;

import com.fasterxml.jackson.core.type.TypeReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import fansirsqi.xposed.sesame.entity.AlipayBeach;
import fansirsqi.xposed.sesame.entity.AlipayUser;
import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.Toast;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField;
import fansirsqi.xposed.sesame.util.DataStore;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskStatus;
import fansirsqi.xposed.sesame.task.antForest.AntForestRpcCall;
import fansirsqi.xposed.sesame.util.GlobalThreadPools;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.maps.BeachMap;
import fansirsqi.xposed.sesame.util.maps.IdMapManager;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.ResChecker;
import fansirsqi.xposed.sesame.util.StringUtil;
import lombok.Getter;

/**
 * @author Constanline
 * @since 2023/08/01
 */
public class AntOcean extends ModelTask {

    @Getter
    public enum ApplyAction {
        AVAILABLE(0, "可用"),
        NO_STOCK(1, "无库存"),
        ENERGY_LACK(2, "能量不足");

        private final int code;
        private final String desc;

        ApplyAction(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public static ApplyAction fromString(String value) {
            for (ApplyAction action : values()) {
                if (action.name().equalsIgnoreCase(value)) {
                    return action;
                }
            }
            Log.error("ApplyAction", "Unknown applyAction: " + value);
            return null;
        }
    }

    private static final String TAG = AntOcean.class.getSimpleName();

    @Override
    public String getName() {
        return "神奇海洋";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }

    @Override
    public String getIcon() {
        return "AntOcean.png";
    }

    private BooleanModelField dailyOceanTask;
    private BooleanModelField aiFish;
    private BooleanModelField cleanOcean;
    private ChoiceModelField cleanOceanType;
    private SelectModelField cleanOceanList;
    private BooleanModelField exchangeProp;
    private BooleanModelField usePropByType;
    private SelectAndCountModelField protectOceanList;
    private BooleanModelField PDL_task;
    private BooleanModelField collectOceanEnergy;
    private IntegerModelField oceanSelfCollectEnergyThreshold;
    private static ChoiceModelField userprotectType;

    public interface protectType {
        int DONT_PROTECT = 0;
        int PROTECT_ALL = 1;
        int PROTECT_BEACH = 2;
        String[] nickNames = {"不保护", "保护全部", "仅保护沙滩"};
    }

    private final Map<String, AtomicInteger> oceanTaskTryCount = new ConcurrentHashMap<>();

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(dailyOceanTask = new BooleanModelField("dailyOceanTask", "海洋任务", false));
        modelFields.addField(aiFish = new BooleanModelField("aiFish", "AI摸鱼", false));
        modelFields.addField(collectOceanEnergy = new BooleanModelField("collectOceanEnergy", "海洋能量 | 收取", false));
        modelFields.addField(oceanSelfCollectEnergyThreshold = new IntegerModelField("oceanSelfCollectEnergyThreshold", "海洋能量 | 最低收取克数", 0));
        modelFields.addField(cleanOcean = new BooleanModelField("cleanOcean", "清理 | 开启", false));
        modelFields.addField(cleanOceanType = new ChoiceModelField("cleanOceanType", "清理 | 动作", CleanOceanType.DONT_CLEAN, CleanOceanType.nickNames));
        modelFields.addField(cleanOceanList = new SelectModelField("cleanOceanList", "清理 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(exchangeProp = new BooleanModelField("exchangeProp", "神奇海洋 | 制作万能拼图", false));
        modelFields.addField(usePropByType = new BooleanModelField("usePropByType", "神奇海洋 | 使用万能拼图", false));
        modelFields.addField(userprotectType = new ChoiceModelField("userprotectType", "保护 | 类型", protectType.DONT_PROTECT, protectType.nickNames));
        modelFields.addField(protectOceanList = new SelectAndCountModelField("protectOceanList", "保护 | 海洋列表", new LinkedHashMap<>(), AlipayBeach::getList));
        modelFields.addField(PDL_task = new BooleanModelField("PDL_task", "潘多拉任务", false));
        return modelFields;
    }

    @Override
    public void runJava() {
        try {
            Log.record(TAG, "执行开始-" + getName());

            if (!queryOceanStatus()) {
                return;
            }
            queryHomePage();

            if (dailyOceanTask.getValue()) {
                receiveTaskAward();
            }

            if (aiFish.getValue()) {
                doAiFish();
            }

            if (!userprotectType.getValue().equals(protectType.DONT_PROTECT)) {
                protectOcean();
            }

            if (exchangeProp.getValue()) {
                exchangeProp();
            }
            if (usePropByType.getValue()) {
                usePropByType();
            }

            if (PDL_task.getValue()) {
                doOceanPDLTask();
            }

        } catch (Throwable t) {
            Log.printStackTrace(TAG,"start.run err:", t);
        } finally {
            Log.record(TAG, "执行结束-" + getName());
        }
    }

    private void doAiFish() {
        try {
            AiFishRunResult result = new AiFishWorkflow(new AiFishGateway() {
                @Override
                public String queryStatus() {
                    return AntAiFishRpcCall.status();
                }

                @Override
                public String queryHome() {
                    return AntAiFishRpcCall.homepage();
                }

                @Override
                public String listTasks(String sceneCode) {
                    return AntAiFishRpcCall.listTasks(sceneCode);
                }

                @Override
                public String finishTask(String sceneCode, String taskType) {
                    return AntAiFishRpcCall.finishTask(sceneCode, taskType);
                }

                @Override
                public String receiveTaskAward(String sceneCode, String taskType) {
                    return AntAiFishRpcCall.receiveTaskAward(sceneCode, taskType);
                }

                @Override
                public boolean hasCompletedToday(String taskType) {
                    return Status.hasFlagToday("antOcean::aiFish::" + taskType);
                }

                @Override
                public void markCompletedToday(String taskType) {
                    Status.setFlagToday("antOcean::aiFish::" + taskType);
                }

                @Override
                public String rescueFish() {
                    return AntAiFishRpcCall.rescueFish();
                }

                @Override
                public String touchFish() {
                    return AntAiFishRpcCall.touchFish();
                }

                @Override
                public void waitMillis(long millis) {
                    GlobalThreadPools.sleepCompat(millis);
                }
            }).run();
            for (String event : result.getEvents()) {
                Log.forest("神奇海洋🌊[" + event + "]");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "AI摸鱼执行异常:", t);
        }
    }

    public static void initBeach() {
        try {
            String response = AntOceanRpcCall.queryCultivationList();
            JSONObject jsonResponse = new JSONObject(response);
            if (ResChecker.checkRes(TAG + "查询种植列表失败:", jsonResponse)) {
                JSONArray cultivationList = jsonResponse.optJSONArray("cultivationItemVOList");
                if (cultivationList != null) {
                    IdMapManager.getInstance(BeachMap.class).clear();
                    for (int i = 0; i < cultivationList.length(); i++) {
                        OceanCultivation cultivation = OceanCultivationPolicy.parse(cultivationList.optJSONObject(i));
                        if (cultivation != null && cultivation.getAvailable()) {
                            switch (userprotectType.getValue()) {
                                case protectType.PROTECT_ALL:
                                    IdMapManager.getInstance(BeachMap.class).add(
                                            cultivation.getCultivationCode(),
                                            cultivation.getName() + "(" + cultivation.getEnergy() + "g)"
                                    );
                                    break;
                                case protectType.PROTECT_BEACH:
                                    if ("BEACH".equals(cultivation.getTemplateSubType())) {
                                        IdMapManager.getInstance(BeachMap.class).add(
                                                cultivation.getCultivationCode(),
                                                cultivation.getName() + "(" + cultivation.getEnergy() + "g)"
                                        );
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    Log.record(TAG, "初始化沙滩数据成功。");
                }
                IdMapManager.getInstance(BeachMap.class).save();
            } else {
                Log.error(TAG,"initBeach"+jsonResponse.optString("resultDesc", "未知错误"));
            }
        } catch (JSONException e) {
            Log.printStackTrace(TAG, "JSON 解析错误：", e);
            IdMapManager.getInstance(BeachMap.class).load();
        } catch (Exception e) {
            Log.printStackTrace(TAG, "初始化沙滩任务时出错", e);
            IdMapManager.getInstance(BeachMap.class).load();
        }
    }

    private Boolean queryOceanStatus() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryOceanStatus());
            if (ResChecker.checkRes(TAG, jo)) {
                if (!jo.getBoolean("opened")) {
                    getEnableField().setValue(false);
                    Log.record("请先开启神奇海洋,并完成引导教程");
                    return false;
                }
                initBeach();
                return true;
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryOceanStatus err:",t);
        }
        return false;
    }
    private void queryHomePage() {
        try {
            JSONObject joHomePage = new JSONObject(AntOceanRpcCall.queryHomePage());
            if (ResChecker.checkRes(TAG + "查询海洋主页失败:", joHomePage)) {
                if (collectOceanEnergy.getValue() && joHomePage.has("bubbleVOList")) {
                    collectEnergy(joHomePage.getJSONArray("bubbleVOList"));
                }
                JSONObject userInfoVO = joHomePage.getJSONObject("userInfoVO");
                int rubbishNumber = userInfoVO.optInt("rubbishNumber", 0);
                String userId = userInfoVO.getString("userId");
                cleanOcean(userId, rubbishNumber);
                JSONObject ipVO = userInfoVO.optJSONObject("ipVO");
                if (ipVO != null) {
                    int surprisePieceNum = ipVO.optInt("surprisePieceNum", 0);
                    if (surprisePieceNum > 0) {
                        ipOpenSurprise();
                    }
                }

                querySeaAreaDetailList();
                queryMiscInfo();
                queryReplicaHome();
                queryUserRanking();

            } else {
                Log.error(TAG, joHomePage.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryHomePage err:",t);
        }
    }

    private void queryMiscInfo() {
        try {
            String s = AntOceanRpcCall.queryMiscInfo();
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询海洋杂项信息失败:", jo)) {
                JSONObject miscHandlerVOMap = jo.getJSONObject("miscHandlerVOMap");
                JSONObject homeTipsRefresh = miscHandlerVOMap.getJSONObject("HOME_TIPS_REFRESH");
                if (homeTipsRefresh.optBoolean("fishCanBeCombined") || homeTipsRefresh.optBoolean("canBeRepaired")) {
                    querySeaAreaDetailList();
                }
                switchOceanChapter();
            } else {
                Log.error(TAG, "查询海洋杂项信息失败"+jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG,  "queryMiscInfo err:",t);
        }
    }

    private void collectEnergy(JSONArray bubbleVOList) {
        try {
            for (int i = 0; i < bubbleVOList.length(); i++) {
                JSONObject bubble = bubbleVOList.getJSONObject(i);
                if (OceanSelfCollectPolicy.shouldCollect(bubble, oceanSelfCollectEnergyThreshold.getValue())) {
                    long bubbleId = bubble.getLong("id");
                    String userId = bubble.getString("userId");
                    String s = AntForestRpcCall.collectEnergy("", userId, bubbleId);
                    JSONObject jo = new JSONObject(s);
                    if (ResChecker.checkRes(TAG + "收取海洋能量失败:", jo)) {
                        JSONArray retBubbles = jo.optJSONArray("bubbles");
                        if (retBubbles != null) {
                            for (int j = 0; j < retBubbles.length(); j++) {
                                JSONObject retBubble = retBubbles.optJSONObject(j);
                                if (retBubble != null) {
                                    int collectedEnergy = retBubble.getInt("collectedEnergy");
                                    Log.forest("神奇海洋🌊收取[" + UserMap.getMaskName(userId) + "]#" + collectedEnergy + "g");
                                    Toast.INSTANCE.show("海洋能量🌊收取[" + UserMap.getMaskName(userId) + "]#" + collectedEnergy + "g");
                                }
                            }
                        }
                    } else {
                        Log.error(TAG, jo.getString("resultDesc"));
                    }
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryHomePage err:", t);
        }
    }

    private static void cleanOcean(String userId, int rubbishNumber) {
        try {
            for (int i = 0; i < rubbishNumber; i++) {
                String s = AntOceanRpcCall.cleanOcean(userId);
                JSONObject jo = new JSONObject(s);
                if (ResChecker.checkRes(TAG + "清理海洋失败:", jo)) {
                    JSONArray cleanRewardVOS = jo.getJSONArray("cleanRewardVOS");
                    checkReward(cleanRewardVOS);
                    Log.forest("神奇海洋🌊[清理:" + UserMap.getMaskName(userId) + "海域]");
                } else {
                    Log.error(TAG, jo.getString("resultDesc"));
                }
            }
        } catch (Throwable t) {

            Log.printStackTrace(TAG, "cleanOcean err:", t);
        }
    }

    private static void ipOpenSurprise() {
        try {
            String s = AntOceanRpcCall.ipOpenSurprise();
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "开启海洋惊喜失败:", jo)) {
                JSONArray rewardVOS = jo.getJSONArray("surpriseRewardVOS");
                checkReward(rewardVOS);
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "ipOpenSurprise err:", t);
        }
    }

    private static void checkAndCreateExtraCollect() {
        try {
            String s = AntOceanRpcCall.querySeaAreaDetailList();
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "复查海洋区域详情:", jo)) {
                if (jo.optBoolean("awardSeaAreaCanCreateExtraCollect", false)) {
                    String availableCode = jo.optString("awardSeaAreaCode", "");
                    Log.record(TAG, "发现海域[" + availableCode + "]限时挑战已就绪！正在接取...");

                    String createRet = AntOceanRpcCall.createSeaAreaExtraCollect();
                    if (ResChecker.checkRes(TAG + "接取限时挑战:", new JSONObject(createRet))) {
                        Log.forest("限时挑战🌊接取成功");
                    }
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private static void combineFish(String fishId, String logType) {
        try {
            String s = AntOceanRpcCall.combineFish(fishId);
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "合成海洋鱼类失败:", jo)) {
                JSONObject fishDetailVO = jo.getJSONObject("fishDetailVO");
                String name = fishDetailVO.getString("name");

                if ("EXTRA_COLLECT".equals(logType)) {
                    Log.forest("限时挑战🌊[" + name + "]合成成功");
                } else {
                    Log.forest("神奇海洋🌊[" + name + "]合成成功");
                }
                checkAndCreateExtraCollect();
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG,"combineFish err:", t);
        }
    }

    private static void checkReward(JSONArray rewards) {
        try {
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.getJSONObject(i);
                String name = reward.getString("name");
                JSONArray attachReward = reward.getJSONArray("attachRewardBOList");
                if (attachReward.length() > 0) {
                    Log.forest("神奇海洋🌊[获得:" + name + "碎片]");
                    boolean canCombine = true;
                    for (int j = 0; j < attachReward.length(); j++) {
                        JSONObject detail = attachReward.getJSONObject(j);
                        if (detail.optInt("count", 0) == 0) {
                            canCombine = false;
                            break;
                        }
                    }
                    if (canCombine && reward.optBoolean("unlock", false)) {
                        String fishId = reward.getString("id");
                        combineFish(fishId, "");
                    }
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG,  "checkReward err:",t);
        }
    }

    private static void collectReplicaAsset(int canCollectAssetNum) {
        try {
            for (int i = 0; i < canCollectAssetNum; i++) {
                String s = AntOceanRpcCall.collectReplicaAsset();
                JSONObject jo = new JSONObject(s);
                if (ResChecker.checkRes(TAG + "收集海洋科普知识失败:", jo)) {
                    Log.forest("神奇海洋🌊[学习海洋科普知识]#潘多拉能量+1");
                } else {
                    Log.error(TAG, jo.getString("resultDesc"));
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "collectReplicaAsset err:", t);
        }
    }

    private static void unLockReplicaPhase(String replicaCode, String replicaPhaseCode) {
        try {
            String s = AntOceanRpcCall.unLockReplicaPhase(replicaCode, replicaPhaseCode);
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "解锁海洋副本阶段失败:", jo)) {
                String name = jo.getJSONObject("currentPhaseInfo").getJSONObject("extInfo").getString("name");
                Log.forest("神奇海洋🌊迎回[" + name + "]");
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "unLockReplicaPhase err:", t);
        }
    }

    private static void queryReplicaHome() {
        try {
            String s = AntOceanRpcCall.queryReplicaHome();
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询海洋副本主页失败:", jo)) {
                if (jo.has("userReplicaAssetVO")) {
                    JSONObject userReplicaAssetVO = jo.getJSONObject("userReplicaAssetVO");
                    int canCollectAssetNum = userReplicaAssetVO.getInt("canCollectAssetNum");
                    collectReplicaAsset(canCollectAssetNum);
                }
                if (jo.has("userCurrentPhaseVO")) {
                    JSONObject userCurrentPhaseVO = jo.getJSONObject("userCurrentPhaseVO");
                    String phaseCode = userCurrentPhaseVO.getString("phaseCode");
                    String code = jo.getJSONObject("userReplicaInfoVO").getString("code");
                    if ("COMPLETED".equals(userCurrentPhaseVO.getString("phaseStatus"))) {
                        unLockReplicaPhase(code, phaseCode);
                    }
                }
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryReplicaHome err:", t);
        }
    }

    private static void queryOceanPropList() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryOceanPropList());
            if (ResChecker.checkRes(TAG + "查询海洋道具列表失败:", jo)) {
                checkAndCreateExtraCollect();
                AntOceanRpcCall.repairSeaArea();
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryOceanPropList err:", t);
        }
    }

    private void switchOceanChapter() {
        String s = AntOceanRpcCall.queryOceanChapterList();
        try {
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询海洋章节列表失败:", jo)) {
                String currentChapterCode = jo.getString("currentChapterCode");
                JSONArray chapterVOs = jo.getJSONArray("userChapterDetailVOList");
                boolean isFinish = false;
                String dstChapterCode = "";
                String dstChapterName = "";
                for (int i = 0; i < chapterVOs.length(); i++) {
                    JSONObject chapterVO = chapterVOs.getJSONObject(i);
                    int repairedSeaAreaNum = chapterVO.getInt("repairedSeaAreaNum");
                    int seaAreaNum = chapterVO.getInt("seaAreaNum");
                    if (chapterVO.getString("chapterCode").equals(currentChapterCode)) {
                        isFinish = repairedSeaAreaNum >= seaAreaNum;
                    } else {
                        if (repairedSeaAreaNum >= seaAreaNum || !chapterVO.getBoolean("chapterOpen")) {
                            continue;
                        }
                        dstChapterName = chapterVO.getString("chapterName");
                        dstChapterCode = chapterVO.getString("chapterCode");
                    }
                }

                if (isFinish && !StringUtil.isEmpty(dstChapterCode)) {
                    Log.record(TAG, "当前海域已完成，等待切换...");
                    GlobalThreadPools.sleepCompat(5000);

                    // 切换动作
                    s = AntOceanRpcCall.switchOceanChapter(dstChapterCode);
                    jo = new JSONObject(s);
                    if (ResChecker.checkRes(TAG + "切换海洋章节失败:", jo)) {
                        Log.forest("神奇海洋🌊切换到[" + dstChapterName + "]系列");
                    } else {
                        Log.error(TAG, jo.getString("resultDesc"));
                    }
                }
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "switchOceanChapter err:", t);
        }
    }

    private void querySeaAreaDetailList() {
        try {
            String s = AntOceanRpcCall.querySeaAreaDetailList();
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询海洋区域详情失败:", jo)) {

                // 1. 检查接取
                if (jo.optBoolean("awardSeaAreaCanCreateExtraCollect", false)) {
                    String availableCode = jo.optString("awardSeaAreaCode", "");
                    Log.record(TAG, "发现海域[" + availableCode + "]限时挑战，正在自动接取...");
                    String createRet = AntOceanRpcCall.createSeaAreaExtraCollect();
                    if (ResChecker.checkRes(TAG + "接取限时挑战:", new JSONObject(createRet))) {
                        Log.forest("限时挑战🌊接取成功");
                        querySeaAreaDetailList();
                        return;
                    }
                }

                int seaAreaNum = jo.getInt("seaAreaNum");
                int fixSeaAreaNum = jo.getInt("fixSeaAreaNum");
                int currentSeaAreaIndex = jo.getInt("currentSeaAreaIndex");
                if (currentSeaAreaIndex < fixSeaAreaNum && seaAreaNum > fixSeaAreaNum) {
                    queryOceanPropList();
                }

                JSONArray seaAreaVOs = jo.getJSONArray("seaAreaVOs");
                for (int i = 0; i < seaAreaVOs.length(); i++) {
                    JSONObject seaAreaVO = seaAreaVOs.getJSONObject(i);
                    // 普通鱼
                    JSONArray fishVOs = seaAreaVO.optJSONArray("fishVO");
                    if (fishVOs != null) {
                        for (int j = 0; j < fishVOs.length(); j++) {
                            JSONObject fishVO = fishVOs.getJSONObject(j);
                            if (!fishVO.getBoolean("unlock") && "COMPLETED".equals(fishVO.getString("status"))) {
                                String fishId = fishVO.getString("id");
                                combineFish(fishId, "");
                            }
                        }
                    }
                    JSONObject seaAreaExtraCollectVO = seaAreaVO.optJSONObject("seaAreaExtraCollectVO");
                    if (seaAreaExtraCollectVO != null) {
                        JSONArray extraFishVOs = seaAreaExtraCollectVO.optJSONArray("fishVO");
                        if (extraFishVOs != null) {
                            for (int j = 0; j < extraFishVOs.length(); j++) {
                                JSONObject fishVO = extraFishVOs.getJSONObject(j);
                                if (!fishVO.getBoolean("unlock") && "COMPLETED".equals(fishVO.optString("status"))) {
                                    String fishId = fishVO.getString("id");
                                    String name = fishVO.optString("name", "未知鱼类");
                                    Log.record(TAG, "发现限时挑战鱼类可合成: " + name);
                                    combineFish(fishId, "EXTRA_COLLECT");
                                }
                            }
                        }
                    }
                }
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "querySeaAreaDetailList err:", t);
        }
    }


    private void cleanFriendOcean(JSONObject fillFlag) {
        if (!fillFlag.optBoolean("canClean")) {
            return;
        }
        try {
            String userId = fillFlag.getString("userId");
            boolean isOceanClean = cleanOceanList.getValue().contains(userId);
            if (cleanOceanType.getValue() == CleanOceanType.DONT_CLEAN) {
                isOceanClean = !isOceanClean;
            }
            if (!isOceanClean) {
                return;
            }
            String s = AntOceanRpcCall.queryFriendPage(userId);
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询好友海洋页面失败:", jo)) {
                s = AntOceanRpcCall.cleanFriendOcean(userId);
                jo = new JSONObject(s);
                Log.forest("神奇海洋🌊[帮助:" + UserMap.getMaskName(userId) + "清理海域]");
                if (ResChecker.checkRes(TAG + "清理好友海洋失败:", jo)) {
                    JSONArray cleanRewardVOS = jo.getJSONArray("cleanRewardVOS");
                    checkReward(cleanRewardVOS);
                } else {
                    Log.error(TAG, jo.getString("resultDesc"));
                }
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryMiscInfo err:", t);
        }
    }

    private void queryUserRanking() {
        try {
            String s = AntOceanRpcCall.queryUserRanking();
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询海洋用户排行榜失败:", jo)) {
                JSONArray fillFlagVOList = jo.getJSONArray("fillFlagVOList");
                for (int i = 0; i < fillFlagVOList.length(); i++) {
                    JSONObject fillFlag = fillFlagVOList.getJSONObject(i);
                    if (cleanOcean.getValue()) {
                        cleanFriendOcean(fillFlag);
                    }
                }
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryMiscInfo err:", t);
        }
    }


    private void receiveTaskAward() {
        try {
            Set<String> presetBad = new LinkedHashSet<>(List.of("DEMO", "DEMO1"));

            TypeReference<Set<String>> typeRef = new TypeReference<>() {
            };
            Set<String> badTaskSet = DataStore.INSTANCE.getOrCreate("badOceanTaskSet", typeRef);
            if (badTaskSet.isEmpty()) {
                badTaskSet.addAll(presetBad);
                DataStore.INSTANCE.put("badOceanTaskSet", badTaskSet);
            }
            while (true) {
                boolean done = false;
                String s = AntOceanRpcCall.queryTaskList();
                JSONObject jo = new JSONObject(s);
                if (!ResChecker.checkRes(TAG + "查询海洋任务列表失败:", jo)) {
                    Log.record(TAG, "查询任务列表失败：" + jo.getString("resultDesc"));
                }
                JSONArray jaTaskList = jo.getJSONArray("antOceanTaskVOList");
                for (int i = 0; i < jaTaskList.length(); i++) {
                    JSONObject task = jaTaskList.getJSONObject(i);
                    JSONObject bizInfo = new JSONObject(task.getString("bizInfo"));
                    String taskTitle = bizInfo.optString("taskTitle");
                    String awardCount = bizInfo.optString("awardCount", "0");
                    String sceneCode = task.getString("sceneCode");
                    String taskType = task.getString("taskType");
                    String taskStatus = task.getString("taskStatus");
                    OceanTaskState beforeState = new OceanTaskState(taskType, taskStatus);
                    if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                        JSONObject joAward = new JSONObject(AntOceanRpcCall.receiveTaskAward(sceneCode, taskType));
                        if (ResChecker.checkRes(TAG + "领取海洋任务奖励失败:", joAward)) {
                            if (confirmOceanTaskAdvanced(beforeState)) {
                                Log.forest("海洋奖励🌊[" + taskTitle + "]# " + awardCount + "拼图");
                                done = true;
                            } else {
                                Log.record(TAG, "海洋任务🌊[" + taskTitle + "]领奖已受理但状态未推进，保留后续重试");
                            }
                        } else {
                            Log.error(TAG, "海洋奖励🌊领取失败：" + joAward);
                        }
                        GlobalThreadPools.sleepCompat(500);
                    } else if (TaskStatus.TODO.name().equals(taskStatus)) {
                        if (badTaskSet.contains(taskTitle) || badTaskSet.contains(taskType)) {
                            Log.record(TAG, "海洋任务🌊[" + taskTitle + "]已在黑名单中，跳过处理");
                            continue;
                        }
                        String actionType = task.optString("actionType", bizInfo.optString("actionType", ""));
                        OceanTaskDecision decision = OceanTaskSafetyPolicy.classify(taskType, taskTitle, actionType);
                        if (decision == OceanTaskDecision.ANSWER) {
                            answerQuestion();
                            done = confirmOceanTaskAdvanced(beforeState);
                        } else if (decision == OceanTaskDecision.FINISH_RPC) {
                            String bizKey = sceneCode + "_" + taskType;
                            int count = oceanTaskTryCount
                                    .computeIfAbsent(bizKey, k -> new AtomicInteger(0))
                                    .incrementAndGet();

                            JSONObject joFinishTask = new JSONObject(AntOceanRpcCall.finishTask(sceneCode, taskType));
                            String errorCode = joFinishTask.optString("code", "");
                            String desc = joFinishTask.optString("desc", "");
                            if ("400000040".equals(errorCode) || desc.contains("不支持RPC完成") ) {
                                Log.error(TAG, "海洋任务🌊[" + taskTitle + "]不支持RPC完成，已加入黑名单");
                                badTaskSet.add(taskTitle);
                                DataStore.INSTANCE.put("badOceanTaskSet", badTaskSet);
                                continue;
                            }
                            if (count > 1) {
                                badTaskSet.add(taskType);
                                DataStore.INSTANCE.put("badOceanTaskSet", badTaskSet);
                            } else {
                                if (ResChecker.checkRes(TAG, joFinishTask)) {
                                    if (confirmOceanTaskAdvanced(beforeState)) {
                                        Log.forest("海洋任务🌊完成[" + taskTitle + "]");
                                        done = true;
                                    } else {
                                        Log.record(TAG, "海洋任务🌊[" + taskTitle + "]完成已受理但状态未推进，保留后续重试");
                                    }
                                } else {
                                    Log.error(TAG, "海洋任务🌊完成失败：" + joFinishTask);
                                }
                            }

                        } else {
                            Log.record(TAG, "海洋任务🌊[" + taskTitle + "]安全策略跳过[" + decision + "]");
                        }
                        GlobalThreadPools.sleepCompat(500);
                    }
                }
                if (!done) break;
            }
        } catch (JSONException e) {
            Log.printStackTrace(TAG,"JSON解析错误: " ,e);
        } catch (
                Throwable t) {
            Log.printStackTrace(TAG, "receiveTaskAward err:", t);
        }
    }

    private boolean confirmOceanTaskAdvanced(OceanTaskState beforeState) {
        try {
            JSONObject response = new JSONObject(AntOceanRpcCall.queryTaskList());
            if (!ResChecker.checkRes(TAG + "回查海洋任务失败:", response)) {
                return false;
            }
            OceanTaskState afterState = OceanTaskProtocol.statusOf(response, beforeState.getTaskType());
            return OceanTaskProtocol.isAdvanced(beforeState, afterState);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "confirmOceanTaskAdvanced err:", t);
            return false;
        }
    }

    private static void answerQuestion() {
        try {
            String questionResponse = AntOceanRpcCall.getQuestion();
            JSONObject questionJson = new JSONObject(questionResponse);
            if (questionJson.getBoolean("answered")) {
                Log.record(TAG, "问题已经被回答过，跳过答题流程");
                return;
            }
            if (questionJson.getInt("resultCode") == 200) {
                String questionId = questionJson.getString("questionId");
                JSONArray options = questionJson.getJSONArray("options");
                String answer = options.getString(0);
                String submitResponse = AntOceanRpcCall.submitAnswer(answer, questionId);
                JSONObject submitJson = new JSONObject(submitResponse);
                if (submitJson.getInt("resultCode") == 200) {
                    Log.forest(TAG, "🌊海洋答题成功");
                } else {
                    Log.error(TAG, "海洋答题失败：" + submitJson);
                }
            } else {
                Log.error(TAG, "海洋获取问题失败：" + questionJson);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "海洋答题错误", t);
        }
    }

    private static void doOceanPDLTask() {
        try {
            Log.record(TAG, "执行潘多拉海域任务");
            String homeResponse = AntOceanRpcCall.PDLqueryReplicaHome();
            JSONObject homeJson = new JSONObject(homeResponse);
            if (ResChecker.checkRes(TAG + "查询潘多拉海洋副本主页失败:", homeJson)) {
                String taskListResponse = AntOceanRpcCall.PDLqueryTaskList();
                JSONObject taskListJson = new JSONObject(taskListResponse);
                JSONArray antOceanTaskVOList = taskListJson.getJSONArray("antOceanTaskVOList");
                for (int i = 0; i < antOceanTaskVOList.length(); i++) {
                    JSONObject task = antOceanTaskVOList.getJSONObject(i);
                    String taskStatus = task.getString("taskStatus");
                    if ("FINISHED".equals(taskStatus)) {
                        String bizInfoString = task.getString("bizInfo");
                        JSONObject bizInfo = new JSONObject(bizInfoString);
                        String taskTitle = bizInfo.getString("taskTitle");
                        int awardCount = bizInfo.getInt("awardCount");
                        String taskType = task.getString("taskType");
                        String receiveTaskResponse = AntOceanRpcCall.PDLreceiveTaskAward(taskType);
                        JSONObject receiveTaskJson = new JSONObject(receiveTaskResponse);
                        int code = receiveTaskJson.getInt("code");
                        if (code == 100000000) {
                            Log.forest("海洋奖励🌊[领取:" + taskTitle + "]获得潘多拉能量x" + awardCount);
                        } else {
                            if (receiveTaskJson.has("message")) {
                                Log.record(TAG, "领取任务奖励失败: " + receiveTaskJson.getString("message"));
                            } else {
                                Log.record(TAG, "领取任务奖励失败，未返回错误信息");
                            }
                        }
                    }
                }
            } else {
                Log.record(TAG, "PDLqueryReplicaHome调用失败: " + homeJson.optString("message"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "doOceanPDLTask err:", t);
        }
    }

    private void protectOcean() {
        try {
            String s = AntOceanRpcCall.queryCultivationList();
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询海洋培育列表失败:", jo)) {
                JSONArray ja = jo.getJSONArray("cultivationItemVOList");
                for (int i = 0; i < ja.length(); i++) {
                    jo = ja.getJSONObject(i);
                    String templateSubType = jo.getString("templateSubType");
                    String applyAction = jo.getString("applyAction");
                    String cultivationName = jo.getString("cultivationName");
                    String templateCode = jo.getString("templateCode");
                    JSONObject projectConfig = jo.getJSONObject("projectConfigVO");
                    String projectCode = projectConfig.getString("code");
                    Map<String, Integer> map = protectOceanList.getValue();
                    for (Map.Entry<String, Integer> entry : map.entrySet()) {
                        if (Objects.equals(entry.getKey(), templateCode)) {
                            Integer count = entry.getValue();
                            if (count != null && count > 0) {
                                oceanExchangeTree(templateCode, projectCode, cultivationName, count);
                            }
                            break;
                        }
                    }
                }
            } else {
                Log.error(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "protectBeach err:", t);
        }
    }

    private static void oceanExchangeTree(String cultivationCode, String projectCode, String itemName, int count) {
        try {
            String s;
            JSONObject jo;
            int appliedTimes = queryCultivationDetail(cultivationCode, projectCode, count);
            if (appliedTimes < 0)
                return;
            for (int applyCount = 1; applyCount <= count; applyCount++) {
                s = AntOceanRpcCall.oceanExchangeTree(cultivationCode, projectCode);
                jo = new JSONObject(s);
                if (ResChecker.checkRes(TAG + "海洋兑换树木失败:", jo)) {
                    JSONArray awardInfos = jo.getJSONArray("rewardItemVOs");
                    StringBuilder award = new StringBuilder();
                    for (int i = 0; i < awardInfos.length(); i++) {
                        jo = awardInfos.getJSONObject(i);
                        award.append(jo.getString("name")).append("*").append(jo.getInt("num"));
                    }
                    String str = "保护海洋生态🏖️[" + itemName + "]#第" + appliedTimes + "次" + "-获得奖励" + award;
                    Log.forest(str);
                    GlobalThreadPools.sleepCompat(300);
                } else {
                    Log.error("保护海洋生态🏖️[" + itemName + "]#发生未知错误，停止申请");
                    break;
                }
                GlobalThreadPools.sleepCompat(300);
                appliedTimes = queryCultivationDetail(cultivationCode, projectCode, count);
                if (appliedTimes < 0) {
                    break;
                } else {
                    GlobalThreadPools.sleepCompat(300);
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "海洋保护错误:", t);
        }
    }

    private static int queryCultivationDetail(String cultivationCode, String projectCode, int count) {
        int appliedTimes = -1;
        try {
            String s = AntOceanRpcCall.queryCultivationDetail(cultivationCode, projectCode);
            JSONObject jo = new JSONObject(s);
            if (ResChecker.checkRes(TAG + "查询海洋培育详情失败:", jo)) {
                JSONObject userInfo = jo.getJSONObject("userInfoVO");
                int currentEnergy = userInfo.getInt("currentEnergy");
                jo = jo.getJSONObject("cultivationDetailVO");
                String applyAction = jo.getString("applyAction");
                int certNum = jo.getInt("certNum");
                if ("AVAILABLE".equals(applyAction)) {
                    if (currentEnergy >= jo.getInt("energy")) {
                        if (certNum < count) {
                            appliedTimes = certNum + 1;
                        }
                    } else {
                        Log.forest("保护海洋🏖️[" + jo.getString("cultivationName") + "]#能量不足停止申请");
                    }
                } else {
                    Log.forest("保护海洋🏖️[" + jo.getString("cultivationName") + "]#似乎没有了");
                }
            } else {
                Log.error(jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "queryCultivationDetail err:", t);
        }
        return appliedTimes;
    }

    private static void exchangeProp() {
        try {
            boolean shouldContinue = true;
            while (shouldContinue) {
                String propListJson = AntOceanRpcCall.exchangePropList();
                JSONObject propListObj = new JSONObject(propListJson);
                if (ResChecker.checkRes(TAG + "查询海洋道具兑换列表失败:", propListObj)) {
                    int duplicatePieceNum = propListObj.getInt("duplicatePieceNum");
                    if (duplicatePieceNum < 10) {
                        return;
                    }
                    String exchangeResultJson = AntOceanRpcCall.exchangeProp();
                    JSONObject exchangeResultObj = new JSONObject(exchangeResultJson);
                    String exchangedPieceNum = exchangeResultObj.getString("duplicatePieceNum");
                    String exchangeNum = exchangeResultObj.getString("exchangeNum");
                    if (ResChecker.checkRes(TAG + "海洋道具兑换失败:", exchangeResultObj)) {
                        Log.forest("神奇海洋🏖️[万能拼图]制作" + exchangeNum + "张,剩余" + exchangedPieceNum + "张碎片");
                        GlobalThreadPools.sleepCompat(1000);
                    }
                } else {
                    shouldContinue = false;
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "exchangeProp error:", t);
        }
    }

    private static void usePropByType() {
        try {
            String propListJson = AntOceanRpcCall.usePropByTypeList();
            JSONObject propListObj = new JSONObject(propListJson);
            if (ResChecker.checkRes(TAG + "查询海洋道具使用类型列表失败:", propListObj)) {
                JSONArray oceanPropVOByTypeList = propListObj.getJSONArray("oceanPropVOByTypeList");
                for (int i = 0; i < oceanPropVOByTypeList.length(); i++) {
                    JSONObject propInfo = oceanPropVOByTypeList.getJSONObject(i);
                    int holdsNum = propInfo.getInt("holdsNum");
                    int pageNum = 0;
                    th:
                    while (holdsNum > 0) {
                        pageNum++;
                        String fishListJson = AntOceanRpcCall.queryFishList(pageNum);
                        JSONObject fishListObj = new JSONObject(fishListJson);
                        if (!ResChecker.checkRes(TAG + "查询海洋鱼类列表失败:", fishListObj)) {
                            break;
                        }
                        JSONArray fishVOS = fishListObj.optJSONArray("fishVOS");
                        if (fishVOS == null) {
                            break;
                        }
                        for (int j = 0; j < fishVOS.length(); j++) {
                            JSONObject fish = fishVOS.getJSONObject(j);
                            JSONArray pieces = fish.optJSONArray("pieces");
                            if (pieces == null) {
                                continue;
                            }
                            int order = fish.getInt("order");
                            String name = fish.getString("name");
                            Set<Integer> idSet = new HashSet<>();
                            for (int k = 0; k < pieces.length(); k++) {
                                JSONObject piece = pieces.getJSONObject(k);
                                if (piece.optInt("num") == 0) {
                                    idSet.add(Integer.parseInt(piece.getString("id")));
                                    holdsNum--;
                                    if (holdsNum <= 0) {
                                        break;
                                    }
                                }
                            }
                            if (!idSet.isEmpty()) {
                                String usePropResult = AntOceanRpcCall.usePropByType(order, idSet);
                                JSONObject usePropResultObj = new JSONObject(usePropResult);
                                if (ResChecker.checkRes(TAG + "使用海洋万能拼图失败:", usePropResultObj)) {
                                    int userCount = idSet.size();
                                    Log.forest("神奇海洋🏖️[万能拼图]使用" + userCount + "张，获得[" + name + "]剩余" + holdsNum + "张");
                                    GlobalThreadPools.sleepCompat(1000);
                                    if (holdsNum <= 0) {
                                        break th;
                                    }
                                }
                            }
                        }
                        if (!fishListObj.optBoolean("hasMore")) {
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG,  "usePropByType error:",t);
        }
    }

    @SuppressWarnings("unused")
    public interface CleanOceanType {
        int CLEAN = 0;
        int DONT_CLEAN = 1;
        String[] nickNames = {"选中清理", "选中不清理"};
    }
}
