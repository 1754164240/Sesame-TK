package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.hook.RequestManager.requestString
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.UUID

object AntFarmRpcCall {
    private const val VERSION = "1.8.2302070202.46"


    /**
     * 进入农场
     *
     * @param userId       自己的用户id
     * @param targetUserId 所在农场的用户id
     * @return 返回结果
     * @throws JSONException 异常内容
     */
    @Throws(JSONException::class)
    fun enterFarm(userId: String?, targetUserId: String?): String {
        val args = JSONObject()
        args.put("animalId", "")
        args.put("bizCode", "")
        args.put("gotoneScene", "")
        args.put("gotoneTemplateId", "")
        args.put("groupId", "")
        args.put("growthExtInfo", "")
        args.put("inviteUserId", "")
        args.put("masterFarmId", "")
        args.put("queryLastRecordNum", true)
        args.put("recall", false)
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("shareId", "")
        args.put("shareUniqueId", System.currentTimeMillis().toString() + "_" + targetUserId)
        args.put("source", "ANTFOREST")
        args.put("starFarmId", "")
        args.put("subBizCode", "")
        args.put("touchRecordId", "")
        args.put("userId", userId)
        args.put("userToken", "")
        args.put("version", VERSION)
        val pamras = "[" + args + "]"
        return requestString("com.alipay.antfarm.enterFarm", pamras)
    }


    // 一起拿小鸡饲料
    fun letsGetChickenFeedTogether(): String {
        val args1 =
            "[{\"needHasInviteUserByCycle\":\"true\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM_P2P\",\"source\":\"ANTFARM\",\"startIndex\":0," + "\"version\":\"" + VERSION + "\"}]"
        val args = "[{\"needHasInviteUserByCycle\":true,\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM_FAMILY_SHARE\",\"source\":\"ANTFARM\",\"startIndex\":0}]"
        return requestString("com.alipay.antiep.canInvitePersonListP2P", args1)
    }

    // 赠送饲料
    fun giftOfFeed(bizTraceId: String, userId: String?): String {
        val args1 = "[{\"beInvitedUserId\":\"" + userId +
                "\",\"bizTraceId\":\"" + bizTraceId +
                "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM_P2P\"," +
                "\"source\":\"ANTFARM\",\"version\":\"" + VERSION + "\"}]"
        return requestString("com.alipay.antiep.inviteP2P", args1)
    }

    @Throws(JSONException::class)
    fun syncAnimalStatus(farmId: String?, operTag: String?, operType: String?): String {
        val args = JSONObject()
        args.put("farmId", farmId)
        args.put("operTag", operTag)
        args.put("operType", operType)
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("version", VERSION)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.syncAnimalStatus", params)
    }


    fun sleep(): String {
        val args1 = "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"LOVECABIN\",\"version\":\"unknown\"}]"
        return requestString("com.alipay.antfarm.sleep", args1)
    }

    fun wakeUp(): String {
        val args1 = "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"LOVECABIN\",\"version\":\"unknown\"}]"
        return requestString("com.alipay.antfarm.wakeUp", args1)
    }

    fun queryLoveCabin(userId: String): String {
        val args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"ENTERFARM\",\"userId\":\"" +
                userId + "\",\"version\":\"" + VERSION + "\"}]"
        return requestString("com.alipay.antfarm.queryLoveCabin", args1)
    }

    fun rewardFriend(consistencyKey: String?, friendId: String?, productNum: String?, time: String?): String {
        val args1 = ("[{\"canMock\":true,\"consistencyKey\":\"" + consistencyKey
                + "\",\"friendId\":\"" + friendId + "\",\"operType\":\"1\",\"productNum\":" + productNum +
                ",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"time\":"
                + time + ",\"version\":\"" + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.rewardFriend", args1)
    }

    fun recallAnimal(animalId: String?, currentFarmId: String?, masterFarmId: String?): String {
        val args1 = ("[{\"animalId\":\"" + animalId + "\",\"currentFarmId\":\""
                + currentFarmId + "\",\"masterFarmId\":\"" + masterFarmId +
                "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.recallAnimal", args1)
    }

    fun orchardRecallAnimal(animalId: String?, userId: String?): String {
        val args1 = "[{\"animalId\":\"" + animalId + "\",\"orchardUserId\":\"" + userId +
                "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"zhuangyuan_zhaohuixiaoji\",\"version\":\"0.1.2403061630.6\"}]"
        return requestString("com.alipay.antorchard.recallAnimal", args1)
    }

    fun sendBackAnimal(sendType: String?, animalId: String?, currentFarmId: String?, masterFarmId: String?): String {
        val args1 = ("[{\"animalId\":\"" + animalId + "\",\"currentFarmId\":\""
                + currentFarmId + "\",\"masterFarmId\":\"" + masterFarmId +
                "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"sendType\":\""
                + sendType + "\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.sendBackAnimal", args1)
    }

    fun harvestProduce(farmId: String?): String {
        val args1 = ("[{\"canMock\":true,\"farmId\":\"" + farmId +
                "\",\"giftType\":\"\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.harvestProduce", args1)
    }

    fun listActivityInfo(): String {
        val args1 = ("[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.listActivityInfo", args1)
    }

    fun donation(activityId: String?, donationAmount: Int): String {
        val args1 = ("[{\"activityId\":\"" + activityId + "\",\"donationAmount\":" + donationAmount +
                ",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.donation", args1)
    }

    fun listFarmTask(): String {
        val args1 = ("[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.listFarmTask", args1)
    }

    val answerInfo: String
        get() {
            val args1 = ("[{\"answerSource\":\"foodTask\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                    + VERSION + "\"}]")
            return requestString("com.alipay.antfarm.getAnswerInfo", args1)
        }

    fun answerQuestion(quesId: String, answer: Int): String {
        val args1 = ("[{\"answers\":\"[{\\\"questionId\\\":\\\"" + quesId + "\\\",\\\"answers\\\":[" + answer +
                "]}]\",\"bizkey\":\"ANSWER\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.doFarmTask", args1)
    }

    fun receiveFarmTaskAward(taskId: String): String {
        val args1 = ("[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskId\":\""
                + taskId + "\",\"version\":\"" + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.receiveFarmTaskAward", args1)
    }

    fun listToolTaskDetails(): String {
        val args1 = ("[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.listToolTaskDetails", args1)
    }

    fun receiveToolTaskReward(rewardType: String?, rewardCount: Int, taskType: String?): String {
        val args1 = ("[{\"ignoreLimit\":false,\"requestType\":\"NORMAL\",\"rewardCount\":" + rewardCount
                + ",\"rewardType\":\"" + rewardType + "\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskType\":\""
                + taskType + "\",\"version\":\"" + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.receiveToolTaskReward", args1)
    }

    @Throws(JSONException::class)
    fun feedAnimal(farmId: String?): String {
        val args = JSONObject()
        args.put("animalType", "CHICK")
        args.put("canMock", true)
        args.put("farmId", farmId)
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "chInfo_ch_appcollect__chsub_my-recentlyUsed")
        args.put("version", VERSION)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.feedAnimal", params)
    }

    fun listFarmTool(): String {
        val args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]"
        return requestString("com.alipay.antfarm.listFarmTool", args1)
    }

    fun useFarmTool(targetFarmId: String?, toolId: String?, toolType: String?): String {
        val args1 = ("[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"targetFarmId\":\""
                + targetFarmId + "\",\"toolId\":\"" + toolId + "\",\"toolType\":\"" + toolType + "\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.useFarmTool", args1)
    }

    fun rankingList(pageStartSum: Int): String {
        val args1 = ("[{\"pageSize\":20,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"startNum\":"
                + pageStartSum + ",\"version\":\"" + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.rankingList", args1)
    }

    fun notifyFriend(animalId: String, notifiedFarmId: String?): String {
        val args1 = ("[{\"animalId\":\"" + animalId +
                "\",\"animalType\":\"CHICK\",\"canBeGuest\":true,\"notifiedFarmId\":\"" + notifiedFarmId +
                "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                + VERSION + "\"}]")
        return requestString("com.alipay.antfarm.notifyFriend", args1)
    }

    @Throws(JSONException::class)
    fun feedFriendAnimal(friendFarmId: String?): String {
        val args = JSONObject()
        args.put("friendFarmId", friendFarmId)
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "chInfo_ch_appcenter__chsub_9patch")
        args.put("version", VERSION)
        val params = "[$args]"

        return requestString("com.alipay.antfarm.feedFriendAnimal", params)
    }

    fun farmId2UserId(farmId: String?): String {
        if (farmId == null) {
            return ""
        }
        return farmId.takeLast(16)
    }

    /**
     * 收集肥料
     *
     * @param manurePotNO 肥料袋号
     * @return 返回结果
     */
    fun collectManurePot(manurePotNO: String?): String {
//        "isSkipTempLimit":true, 肥料满了也强行收取，解决 农场未开通 打扫鸡屎失败问题
        return requestString(
            "com.alipay.antfarm.collectManurePot", ("[{\"isSkipTempLimit\":true,\"manurePotNOs\":\"" + manurePotNO +
                    "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION
                    + "\"}]")
        )
    }

    fun sign(): String {
        return requestString("com.alipay.antfarm.sign", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]")
    }

    fun initFarmGame(gameType: String?): String {
        if ("flyGame" == gameType) {
            return requestString(
                "com.alipay.antfarm.initFarmGame",
                "[{\"gameType\":\"flyGame\",\"requestType\":\"RPC\",\"sceneCode\":\"FLAYGAME\"," +
                        "\"source\":\"FARM_game_yundongfly\",\"toolTypes\":\"ACCELERATETOOL,SHARETOOL,NONE\",\"version\":\"\"}]"
            )
        }
        return requestString(
            "com.alipay.antfarm.initFarmGame",
            ("[{\"gameType\":\"" + gameType
                    + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"toolTypes\":\"STEALTOOL,ACCELERATETOOL,SHARETOOL\"}]")
        )
    }

    fun RandomScore(str: String?): Int {
        if ("starGame" == str) {
            return RandomUtil.nextInt(300, 400)
        } else if ("jumpGame" == str) {
            return RandomUtil.nextInt(250, 270) * 10
        } else if ("flyGame" == str) {
            return RandomUtil.nextInt(4000, 8000)
        } else if ("hitGame" == str) {
            return RandomUtil.nextInt(80, 120)
        } else {
            return 210
        }
    }

    fun recordFarmGame(gameType: String?): String {
        val uuid: String = uuid
        val md5String = getMD5(uuid)
        val score = RandomScore(gameType)
        if ("flyGame" == gameType) {
            val foodCount = score / 50
            return requestString(
                "com.alipay.antfarm.recordFarmGame",
                ("[{\"foodCount\":" + foodCount + ",\"gameType\":\"flyGame\",\"md5\":\"" + md5String
                        + "\",\"requestType\":\"RPC\",\"sceneCode\":\"FLAYGAME\",\"score\":" + score
                        + ",\"source\":\"ANTFARM\",\"toolTypes\":\"ACCELERATETOOL,SHARETOOL,NONE\",\"uuid\":\"" + uuid
                        + "\",\"version\":\"\"}]")
            )
        }
        return requestString(
            "com.alipay.antfarm.recordFarmGame",
            ("[{\"gameType\":\"" + gameType + "\",\"md5\":\"" + md5String
                    + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"score\":" + score
                    + ",\"source\":\"H5\",\"toolTypes\":\"STEALTOOL,ACCELERATETOOL,SHARETOOL\",\"uuid\":\"" + uuid
                    + "\"}]")
        )
    }

    private val uuid: String
        get() {
            val sb = StringBuilder()
            for (str in UUID.randomUUID().toString().split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                sb.append(str.substring(str.length / 2))
            }
            return sb.toString()
        }

    fun getMD5(password: String): String {
        try {
            // 得到一个信息摘要器
            val digest = MessageDigest.getInstance("md5")
            val result = digest.digest(password.toByteArray())
            val buffer = StringBuilder()
            // 把没一个byte 做一个与运算 0xff;
            for (b in result) {
                // 与运算
                val number = b.toInt() and 0xff // 加盐
                val str = Integer.toHexString(number)
                if (str.length == 1) {
                    buffer.append("0")
                }
                buffer.append(str)
            }
            // 标准的md5加密后的结果
            return buffer.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return ""
        }
    }


    /**
     * 小鸡厨房 - 进厨房
     *
     * @param userId 用户id
     * @return 返回结果
     * @throws JSONException 异常
     */
    @Throws(JSONException::class)
    fun enterKitchen(userId: String?): String {
        val args = JSONObject()
        args.put("requestType", "RPC")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "VILLA")
        args.put("userId", userId)
        args.put("version", "unknown")
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.enterKitchen", params)
    }

    fun collectDailyFoodMaterial(dailyFoodMaterialAmount: Int): String {
        return requestString(
            "com.alipay.antfarm.collectDailyFoodMaterial",
            "[{\"collectDailyFoodMaterialAmount\":" + dailyFoodMaterialAmount + ",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"VILLA\",\"version\":\"unknown\"}]"
        )
    }

    fun queryFoodMaterialPack(): String {
        return requestString(
            "com.alipay.antfarm.queryFoodMaterialPack",
            "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"kitchen\",\"version\":\"unknown\"}]"
        )
    }

    fun collectDailyLimitedFoodMaterial(dailyLimitedFoodMaterialAmount: Int): String {
        return requestString(
            "com.alipay.antfarm.collectDailyLimitedFoodMaterial",
            ("[{\"collectDailyLimitedFoodMaterialAmount\":" + dailyLimitedFoodMaterialAmount
                    + ",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"kitchen\",\"version\":\"unknown\"}]")
        )
    }

    fun farmFoodMaterialCollect(): String {
        return requestString(
            "com.alipay.antorchard.farmFoodMaterialCollect",
            "[{\"collect\":true,\"requestType\":\"RPC\",\"sceneCode\":\"ORCHARD\",\"source\":\"VILLA\",\"version\":\"unknown\"}]"
        )
    }

    /**
     * 小鸡厨房 - 做菜
     *
     * @param userId
     * @param source
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun cook(userId: String?, source: String?): String {
//[{"requestType":"RPC","sceneCode":"ANTFARM","source":"VILLA","userId":"2088522730162798","version":"unknown"}]
        val args = JSONObject()
        args.put("requestType", "RPC")
        args.put("sceneCode", "ANTFARM")
        args.put("source", source)
        args.put("userId", userId)
        args.put("version", "unknown")
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.cook", params)
    }

    fun useFarmFood(cookbookId: String?, cuisineId: String?): String {
        return requestString(
            "com.alipay.antfarm.useFarmFood",
            ("[{\"cookbookId\":\"" + cookbookId + "\",\"cuisineId\":\"" + cuisineId
                    + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"useCuisine\":true,\"version\":\""
                    + VERSION + "\"}]")
        )
    }

    fun collectKitchenGarbage(): String {
        return requestString(
            "com.alipay.antfarm.collectKitchenGarbage",
            "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"VILLA\",\"version\":\"unknown\"}]"
        )
    }

    /* 日常任务 */
    fun doFarmTask(bizKey: String?): String {
        return requestString(
            "com.alipay.antfarm.doFarmTask",
            ("[{\"bizKey\":\"" + bizKey
                    + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                    + VERSION + "\"}]")
        )
    }

    fun queryTabVideoUrl(): String {
        return requestString(
            "com.alipay.antfarm.queryTabVideoUrl",
            ("[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION
                    + "\"}]")
        )
    }

    fun videoDeliverModule(bizId: String?): String {
        return requestString(
            "alipay.content.reading.life.deliver.module",
            ("[{\"bizId\":\"" + bizId
                    + "\",\"bizType\":\"CONTENT\",\"chInfo\":\"ch_antFarm\",\"refer\":\"antFarm\",\"timestamp\":\""
                    + System.currentTimeMillis() + "\"}]")
        )
    }

    fun videoTrigger(bizId: String?): String {
        return requestString(
            "alipay.content.reading.life.prize.trigger",
            ("[{\"bizId\":\"" + bizId
                    + "\",\"bizType\":\"CONTENT\",\"prizeFlowNum\":\"VIDEO_TASK\",\"prizeType\":\"farmFeed\"}]")
        )
    }

    /* 惊喜礼包 */
    fun drawLotteryPlus(): String {
        return requestString(
            "com.alipay.antfarm.drawLotteryPlus",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5 \",\"version\":\"\"}]"
        )
    }

    /* 小麦 */
    fun acceptGift(): String {
        return requestString(
            "com.alipay.antfarm.acceptGift",
            ("[{\"ignoreLimit\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                    + VERSION + "\"}]")
        )
    }

    fun visitFriend(friendFarmId: String?): String {
        return requestString(
            "com.alipay.antfarm.visitFriend",
            ("[{\"friendFarmId\":\"" + friendFarmId
                    + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""
                    + VERSION + "\"}]")
        )
    }

    /**
     * 小鸡日志当月日期查询
     *
     * @return
     */
    fun queryChickenDiaryList(): String {
        return requestString(
            "com.alipay.antfarm.queryChickenDiaryList",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"DIARY\",\"source\":\"antfarm_icon\"}]"
        )
    }

    /**
     * 小鸡日志指定月份日期查询
     *
     * @param yearMonth 日期格式：yyyy-MM
     * @return
     */
    fun queryChickenDiaryList(yearMonth: String?): String {
        return requestString(
            "com.alipay.antfarm.queryChickenDiaryList",
            "[{\"queryMonthStr\":\"" + yearMonth + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"DIARY\",\"source\":\"antfarm_icon\"}]"
        )
    }

    fun queryChickenDiary(queryDayStr: String?): String {
        return requestString(
            "com.alipay.antfarm.queryChickenDiary",
            ("[{\"queryDayStr\":\"" + queryDayStr
                    + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"DIARY\",\"source\":\"antfarm_icon\"}]")
        )
    }

    fun diaryTietie(diaryDate: String?, roleId: String?): String {
        return requestString(
            "com.alipay.antfarm.diaryTietie",
            ("[{\"diaryDate\":\"" + diaryDate + "\",\"requestType\":\"NORMAL\",\"roleId\":\"" + roleId
                    + "\",\"sceneCode\":\"DIARY\",\"source\":\"antfarm_icon\"}]")
        )
    }

    /**
     * 小鸡日记点赞
     *
     * @param DiaryId 日记id
     * @return
     */
    fun collectChickenDiary(DiaryId: String?): String {
        return requestString(
            "com.alipay.antfarm.collectChickenDiary",
            "[{\"collectStatus\":true,\"diaryId\":\"" + DiaryId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"MOOD\",\"source\":\"H5\"}]"
        )
    }

    fun visitAnimal(): String {
        return requestString(
            "com.alipay.antfarm.visitAnimal",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION +
                    "\"}]"
        )
    }

    fun feedFriendAnimalVisit(friendFarmId: String?): String {
        return requestString(
            "com.alipay.antfarm.feedFriendAnimal",
            "[{\"friendFarmId\":\"" + friendFarmId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," +
                    "\"source\":\"visitChicken\",\"version\":\"" + VERSION + "\"}]"
        )
    }

    fun visitAnimalSendPrize(token: String?): String {
        return requestString(
            "com.alipay.antfarm.visitAnimalSendPrize",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"token\":\"" + token +
                    "\",\"version\":\"" + VERSION + "\"}]"
        )
    }

    /* 抽抽乐 */
    fun enterDrawMachine(): String {
        return requestString(
            "com.alipay.antfarm.enterDrawMachine",
            "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"siliaorenwu\"}]"
        )
    }

    /**
     * 抽抽乐-抽奖类型选择器
     *
     * @param drawType 抽奖类型 ipDraw-对应IP抽奖
     * @return ""
     */
    private fun chouchouleSelector(drawType: String): String {
        if (drawType == "ipDraw") {
            return "ANTFARM_IP_DRAW_TASK"
        }
        return "ANTFARM_DRAW_TIMES_TASK"
    }

    /**
     * 查询抽抽乐任务列表
     *
     * @param drawType 抽奖类型
     * @return 返回结果
     * @throws JSONException 异常
     */
    @Throws(JSONException::class)
    fun chouchouleListFarmTask(drawType: String): String {
        val taskSceneCode = chouchouleSelector(drawType)
        val args = JSONObject()
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("taskSceneCode", taskSceneCode)
        args.put("topTask", "")
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.listFarmTask", params)
    }

    /**
     * 执行抽抽乐任务
     *
     * @param drawType 抽奖类型
     * @param bizKey   任务ID
     * @return 返回结果
     * @throws JSONException 异常
     */
    @Throws(JSONException::class)
    fun chouchouleDoFarmTask(drawType: String, bizKey: String?): String {
        val taskSceneCode = chouchouleSelector(drawType)
        val args = JSONObject()
        args.put("bizKey", bizKey)
        args.put("requestType", "RPC")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("taskSceneCode", taskSceneCode)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.doFarmTask", params)
    }


    /**
     * 领取抽抽乐任务奖励-抽奖次数
     *
     * @param drawType 抽奖类型
     * @param taskId   任务ID
     * @return 返回结果
     * @throws JSONException 异常
     */
    @Throws(JSONException::class)
    fun chouchouleReceiveFarmTaskAward(drawType: String, taskId: String?): String {
        val taskSceneCode = chouchouleSelector(drawType)
        val args = JSONObject()
        args.put("requestType", "RPC")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("taskId", taskId)
        args.put("taskSceneCode", taskSceneCode)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.receiveFarmTaskAward", params)
    }

    /**
     * IP抽抽乐查询活动与抽奖次数
     */
    fun queryDrawMachineActivity(): String {
        return requestString(
            "com.alipay.antfarm.queryDrawMachineActivity",
            "[{\"otherScenes\":[\"dailyDrawMachine\"],\"requestType\":\"RPC\",\"scene\":\"ipDrawMachine\",\"sceneCode\":\"ANTFARM\",\"source\":\"ip_ccl\"}]"
        )
    }

    /**
     * IP抽抽乐抽奖
     */
    fun drawMachine(): String {
        return requestString("com.alipay.antfarm.drawMachine", "[{\"requestType\":\"RPC\",\"scene\":\"ipDrawMachine\",\"sceneCode\":\"ANTFARM\",\"source\":\"ip_ccl\"}]")
    }

    fun hireAnimal(farmId: String?, animalId: String?): String {
        return requestString(
            "com.alipay.antfarm.hireAnimal",
            "[{\"friendFarmId\":\"" + farmId + "\",\"hireActionType\":\"HIRE_IN_FRIEND_FARM\",\"hireAnimalId\":\"" + animalId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"sendCardChat\":false,\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]"
        )
    }

    fun DrawPrize(): String {
        return requestString(
            "com.alipay.antfarm.DrawPrize",
            "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"chouchoule\"}]"
        )
    }

    fun DrawPrize(activityId: String?): String {
        return requestString(
            "com.alipay.antfarm.DrawPrize",
            "[{\"activityId\":\"" + activityId + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"icon\"}]"
        )
    }

    fun drawGameCenterAward(): String {
        return requestString(
            "com.alipay.antfarm.drawGameCenterAward",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]"
        )
    }

    fun queryGameList(): String {
        return requestString(
            "com.alipay.antfarm.queryGameList",
            "[{\"commonDegradeResult\":{\"deviceLevel\":\"high\",\"resultReason\":0,\"resultType\":0},\"platform\":\"Android\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]"
        )
    }

    // 小鸡换装
    fun listOrnaments(): String {
        return requestString(
            "com.alipay.antfarm.listOrnaments",
            "[{\"pageNo\":\"1\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"setsType\":\"ACHIEVEMENTSETS\",\"source\":\"H5\",\"subType\":\"sets\",\"type\":\"apparels\",\"version\":\"" + VERSION + "\"}]"
        )
    }

    fun saveOrnaments(animalId: String?, farmId: String?, ornaments: String?): String {
        return requestString(
            "com.alipay.antfarm.saveOrnaments",
            "[{\"animalId\":\"" + animalId + "\",\"farmId\":\"" + farmId + "\",\"ornaments\":\"" + ornaments + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]"
        )
    }

    // 亲密家庭
    fun enterFamily(): String {
        val args = "[{\"fromAnn\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"timeZoneId\":\"Asia/Shanghai\"}]"
        return requestString("com.alipay.antfarm.enterFamily", args)
    }

    fun familyReceiveFarmTaskAward(taskId: String): String {
        val args =
            "[{\"awardType\":\"FAMILY_INTIMACY\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskId\":\"" + taskId + "\",\"taskSceneCode\":\"ANTFARM_FAMILY_TASK\"}]"
        return requestString("com.alipay.antfarm.receiveFarmTaskAward", args)
    }

    fun familyAwardList(): String {
        val args = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.familyAwardList", args)
    }

    fun receiveFamilyAward(rightId: String): String {
        val args = "[{\"requestType\":\"NORMAL\",\"rightId\":\"" + rightId + "\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.receiveFamilyAward", args)
    }

    fun assignFamilyMember(assignAction: String?, beAssignUser: String?): String {
        return requestString(
            "com.alipay.antfarm.assignFamilyMember",
            "[{\"assignAction\":\"" + assignAction + "\",\"beAssignUser\":\"" + beAssignUser + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        )
    }

    fun sendChat(chatCardType: String?, receiverUserId: String?): String {
        return requestString(
            "com.alipay.antfarm.sendChat",
            "[{\"chatCardType\":\"" + chatCardType + "\",\"receiverUserId\":\"" + receiverUserId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        )
    }

    fun deliverSubjectRecommend(friendUserIdList: JSONArray?): String {
        val args = "[{\"friendUserIds\":" + friendUserIdList + ",\"requestType\":\"NORMAL\",\"sceneCode\":\"ChickFamily\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.deliverSubjectRecommend", args)
    }

    @Throws(JSONException::class)
    fun OpenAIPrivatePolicy(): String {
        val args = JSONObject()
        args.put("privatePolicyIdList", JSONArray().put("AI_CHICK_PRIVATE_POLICY"))
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("version", VERSION)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.OpenPrivatePolicy", params)
    }


    @Throws(JSONException::class)
    fun deliverContentExpand(
        ariverRpcTraceId: String?,
        eventId: String?,
        eventName: String?,
        memo: String?,
        resultCode: String?,
        sceneId: String?,
        sceneName: String?,
        success: Boolean,
        friendUserIdList: JSONArray?
    ): String {
        val args = JSONObject()
        args.put("ariverRpcTraceId", ariverRpcTraceId)
        args.put("eventId", eventId)
        args.put("eventName", eventName)
        args.put("friendUserIds", friendUserIdList)
        args.put("memo", memo)
        args.put("requestType", "NORMAL")
        args.put("resultCode", resultCode)
        args.put("sceneCode", "ANTFARM")
        args.put("sceneId", sceneId)
        args.put("sceneName", sceneName)
        args.put("source", "H5")
        args.put("success", success)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.DeliverContentExpand", params)
    }

    @Throws(JSONException::class)
    fun QueryExpandContent(deliverId: String?): String {
        val args = JSONObject()
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("deliverId", deliverId)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.QueryExpandContent", params)
    }

    @Throws(JSONException::class)
    fun deliverMsgSend(groupId: String?, friendUserIds: JSONArray?, content: String?, deliverId: String?): String {
        val args = JSONObject()
        args.put("content", content)
        args.put("deliverId", deliverId)
        args.put("friendUserIds", friendUserIds)
        args.put("groupId", groupId)
        args.put("mode", "AI")
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("spaceType", "ChickFamily")
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.DeliverMsgSend", params)
    }

    fun syncFamilyStatus(groupId: String, operType: String?, syncUserIds: String?): String {
        val args =
            "[{\"groupId\":\"" + groupId + "\",\"operType\":\"" + operType + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"syncUserIds\":[\"" + syncUserIds + "\"]}]"
        return requestString("com.alipay.antfarm.syncFamilyStatus", args)
    }

    fun inviteFriendVisitFamily(receiverUserId: JSONArray?): String {
        val args = "[{\"bizType\":\"FAMILY_SHARE\",\"receiverUserId\":" + receiverUserId + ",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.inviteFriendVisitFamily", args)
    }

    fun familyEatTogether(groupId: String?, friendUserIdList: JSONArray?, cuisines: JSONArray?): String {
        val args =
            "[{\"cuisines\":" + cuisines + ",\"friendUserIds\":" + friendUserIdList + ",\"groupId\":\"" + groupId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"spaceType\":\"ChickFamily\"}]"
        return requestString("com.alipay.antfarm.familyEatTogether", args)
    }

    fun queryRecentFarmFood(queryNum: Int): String {
        val args = "[{\"queryNum\": " + queryNum + ",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.queryRecentFarmFood", args)
    }

    fun feedFriendAnimal(friendFarmId: String, groupId: String?): String {
        val args =
            "[{\"friendFarmId\": \"" + friendFarmId + "\",\"groupId\": \"" + groupId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ChickFamily\",\"source\":\"H5\",\"spaceType\":\"ChickFamily\"}]"
        return requestString("com.alipay.antfarm.feedFriendAnimal", args)
    }

    fun queryFamilyDrawActivity(): String {
        val args = "[{\"bizType\":\"ANTFARM_GAME_CENTER\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.queryFamilyDrawActivity", args)
    }

    fun familyDraw(): String {
        val args = "[{\"bizType\":\"ANTFARM_GAME_CENTER\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.familyDraw", args)
    }

    fun familyBatchInviteP2P(inviteP2PVOList: JSONArray?, sceneCode: String?): String {
        val args = "[{\"inviteP2PVOList\":" + inviteP2PVOList + ",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"antfarm\"}]"
        return requestString("com.alipay.antiep.batchInviteP2P", args)
    }

    fun familyDrawSignReceiveFarmTaskAward(taskId: String): String {
        val args =
            "[{\"awardType\":\"FAMILY_DRAW_TIME\",\"bizType\":\"ANTFARM_GAME_CENTER\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskId\":\"" + taskId + "\",\"taskSceneCode\":\"ANTFARM_FAMILY_DRAW_TASK\"}]"
        return requestString("com.alipay.antfarm.receiveFarmTaskAward", args)
    }

    /**
     * 扭蛋任务查询好友列表
     */
    @Throws(JSONException::class)
    fun familyShareP2PPanelInfo(sceneCode: String?): String {
        val jo = JSONObject()
        jo.put("requestType", "RPC")
        jo.put("source", "antfarm")
        jo.put("sceneCode", sceneCode)
        return requestString("com.alipay.antiep.shareP2PPanelInfo", JSONArray().put(jo).toString())
    }

    /**
     * 扭蛋任务列表
     */
    fun familyDrawListFarmTask(): String {
        val args =
            "[{\"bizType\":\"ANTFARM_GAME_CENTER\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM_FAMILY_DRAW_TASK\",\"signSceneCode\":\"\",\"source\":\"H5\",\"taskSceneCode\":\"ANTFARM_FAMILY_DRAW_TASK\"}]"
        return requestString("com.alipay.antfarm.listFarmTask", args)
    }

    fun giftFamilyDrawFragment(giftUserId: String?, giftNum: Int): String {
        val args =
            "[{\"bizType\":\"ANTFARM_GAME_CENTER\",\"giftNum\":$giftNum,\"giftUserId\":\"$giftUserId\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.antfarm.giftFamilyDrawFragment", args)
    }

    val mallHome: String
        get() {
            val data =
                "[{\"bizType\":\"ANTFARM_GAME_CENTER\",\"pageSize\":10,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"startIndex\":0}]"
            return requestString("com.alipay.charitygamecenter.getMallHome", data)
        }

    fun getMallItemDetail(spuId: String): String {
        val data = "[{\"bizType\":\"ANTFARM_GAME_CENTER\",\"itemId\":\"$spuId\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]"
        return requestString("com.alipay.charitygamecenter.getMallItemDetail", data)
    }

    fun exchangeBenefit(spuId: String, skuId: String?): String {
        val data =
            "[{\"bizType\":\"ANTFARM_GAME_CENTER\",\"ignoreHoldLimit\":false,\"itemId\":\"$spuId\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"subItemId\":\"$skuId\"}]"
        return requestString("com.alipay.charitygamecenter.buyMallItem", data)
    }

    /**
     * 领取活动食物
     *
     * @param foodType
     * @param giftIndex
     * @return
     */
    fun clickForGiftV2(foodType: String, giftIndex: Int): String {
        val data =
            "[{\"foodType\":\"" + foodType + "\",\"giftIndex\":" + giftIndex + ",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"ANTFOREST\",\"version\":\"" + VERSION + "\"}]"
        return requestString("com.alipay.antfarm.clickForGiftV2", data)
    }
}
