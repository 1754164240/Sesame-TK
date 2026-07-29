package fansirsqi.xposed.sesame.task.antStall

import android.util.Base64
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.entity.AlipayUser
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.TimeCounter
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedList
import java.util.Queue

/**
 * @file AntStall.kt
 * @brief 蚂蚁新村任务模块
 * @author Constanline
 * @since 2023/08/22
 */
class AntStall : ModelTask() {

    /**
     * @brief 摊位数据类
     * @property userId 用户ID
     * @property hot 热度值
     */
    private data class Seat(val userId: String, val hot: Int)

    // 配置字段
    private lateinit var stallAutoOpen: BooleanModelField
    private lateinit var stallOpenType: ChoiceModelField
    private lateinit var stallOpenList: SelectModelField
    private lateinit var stallAutoClose: BooleanModelField
    private lateinit var stallAutoTicket: BooleanModelField
    private lateinit var stallTicketType: ChoiceModelField
    private lateinit var stallTicketList: SelectModelField
    private lateinit var stallAutoTask: BooleanModelField
    private lateinit var stallReceiveAward: BooleanModelField
    private lateinit var stallWhiteList: SelectModelField
    private lateinit var stallBlackList: SelectModelField
    private lateinit var stallAllowOpenReject: BooleanModelField
    private lateinit var stallAllowOpenTime: IntegerModelField
    private lateinit var stallSelfOpenTime: IntegerModelField
    private lateinit var stallDonate: BooleanModelField
    private lateinit var stallInviteRegister: BooleanModelField
    private lateinit var stallThrowManure: BooleanModelField
    private lateinit var stallThrowManureType: ChoiceModelField
    private lateinit var stallThrowManureList: SelectModelField
    private lateinit var stallInviteShop: BooleanModelField
    private lateinit var stallInviteShopType: ChoiceModelField
    private lateinit var stallInviteShopList: SelectModelField
    private lateinit var roadmap: BooleanModelField
    private lateinit var stallInviteRegisterList: SelectModelField
    private lateinit var assistFriendList: SelectModelField

    override fun getName(): String = "新村"

    override fun getGroup(): ModelGroup = ModelGroup.STALL

    override fun getIcon(): String = "AntStall.png"

    override fun getFields(): ModelFields {
        return ModelFields().apply {
            addField(BooleanModelField("stallAutoOpen", "摆摊 | 开启", false).also {
                stallAutoOpen = it
            })
            addField(
                ChoiceModelField(
                    "stallOpenType",
                    "摆摊 | 动作",
                    StallOpenType.OPEN,
                    StallOpenType.nickNames
                ).also { stallOpenType = it })
            addField(
                SelectModelField(
                    "stallOpenList",
                    "摆摊 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { stallOpenList = it })
            addField(
                BooleanModelField(
                    "stallAutoClose",
                    "收摊 | 开启",
                    false
                ).also { stallAutoClose = it })
            addField(
                IntegerModelField(
                    "stallSelfOpenTime",
                    "收摊 | 摆摊时长(分钟)",
                    120
                ).also { stallSelfOpenTime = it })
            addField(
                BooleanModelField(
                    "stallAutoTicket",
                    "贴罚单 | 开启",
                    false
                ).also { stallAutoTicket = it })
            addField(
                ChoiceModelField(
                    "stallTicketType",
                    "贴罚单 | 动作",
                    StallTicketType.DONT_TICKET,
                    StallTicketType.nickNames
                ).also { stallTicketType = it })
            addField(
                SelectModelField(
                    "stallTicketList",
                    "贴罚单 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { stallTicketList = it })
            addField(
                BooleanModelField(
                    "stallThrowManure",
                    "丢肥料 | 开启",
                    false
                ).also { stallThrowManure = it })
            addField(
                ChoiceModelField(
                    "stallThrowManureType",
                    "丢肥料 | 动作",
                    StallThrowManureType.DONT_THROW,
                    StallThrowManureType.nickNames
                ).also { stallThrowManureType = it })
            addField(
                SelectModelField(
                    "stallThrowManureList",
                    "丢肥料 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { stallThrowManureList = it })
            addField(
                BooleanModelField(
                    "stallInviteShop",
                    "邀请摆摊 | 开启",
                    false
                ).also { stallInviteShop = it })
            addField(
                ChoiceModelField(
                    "stallInviteShopType",
                    "邀请摆摊 | 动作",
                    StallInviteShopType.INVITE,
                    StallInviteShopType.nickNames
                ).also { stallInviteShopType = it })
            addField(
                SelectModelField(
                    "stallInviteShopList",
                    "邀请摆摊 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { stallInviteShopList = it })
            addField(
                BooleanModelField(
                    "stallAllowOpenReject",
                    "请走小摊 | 开启",
                    false
                ).also { stallAllowOpenReject = it })
            addField(
                IntegerModelField(
                    "stallAllowOpenTime",
                    "请走小摊 | 允许摆摊时长(分钟)",
                    121
                ).also { stallAllowOpenTime = it })
            addField(
                SelectModelField(
                    "stallWhiteList",
                    "请走小摊 | 白名单(超时也不赶)",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { stallWhiteList = it })
            addField(
                SelectModelField(
                    "stallBlackList",
                    "请走小摊 | 黑名单(不超时也赶)",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { stallBlackList = it })
            addField(BooleanModelField("stallAutoTask", "自动任务", false).also {
                stallAutoTask = it
            })
            addField(
                BooleanModelField(
                    "stallReceiveAward",
                    "自动领奖",
                    false
                ).also { stallReceiveAward = it })
            addField(BooleanModelField("stallDonate", "自动捐赠", false).also { stallDonate = it })
            addField(BooleanModelField("roadmap", "自动进入下一村", false).also { roadmap = it })
            addField(
                BooleanModelField(
                    "stallInviteRegister",
                    "邀请 | 邀请好友开通新村",
                    false
                ).also { stallInviteRegister = it })
            addField(
                SelectModelField(
                    "stallInviteRegisterList",
                    "邀请 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { stallInviteRegisterList = it })
            addField(
                SelectModelField(
                    "assistFriendList",
                    "助力好友列表",
                    LinkedHashSet(),
                    AlipayUser::getList
                ).also { assistFriendList = it })
        }
    }

    override fun runJava() {
        try {
            val tc = TimeCounter(TAG)
            Log.record(TAG, "执行开始-$name")

            val homeResponse = AntStallRpcCall.home()
            val homeJson = JSONObject(homeResponse)

            if (!ResChecker.checkRes(TAG, homeJson)) {
                Log.record(TAG, "home err: $homeResponse")
                return
            }

            // 检查是否已注册
            if (!homeJson.getBoolean("hasRegister") || homeJson.getBoolean("hasQuit")) {
                Log.farm("蚂蚁新村⛪请先开启蚂蚁新村")
                return
            }

            // 收取应收金币
            val astReceivableCoinVO = homeJson.getJSONObject("astReceivableCoinVO")
            if (astReceivableCoinVO.optBoolean("hasCoin")) {
                settleReceivable()
                tc.countDebug("收金币")
            }

            // 丢肥料
            if (stallThrowManure.value) {
                throwManure()
                tc.countDebug("丢肥料")
            }

            val seatsMap = homeJson.getJSONObject("seatsMap")

            // 收取金币
            settle(seatsMap)
            tc.countDebug("收取金币")

            // 收肥料
            collectManure()
            tc.countDebug("收肥料")

            // 请走操作
            sendBack(seatsMap)
            tc.countDebug("请走")

            // 收摊
            if (stallAutoClose.value) {
                closeShop()
                tc.countDebug("收摊")
            }

            // 摆摊
            if (stallAutoOpen.value) {
                openShop()
                tc.countDebug("摆摊")
            }

            // 自动任务
            if (stallAutoTask.value) {
                taskList()
                tc.countDebug("自动任务第一次")
                GlobalThreadPools.sleepCompat(500)
                taskList()
                tc.countDebug("自动任务第二次")
            }

            // 新村助力
            assistFriend()
            tc.countDebug("新村助力")

            // 自动捐赠
            if (stallDonate.value && Status.canStallDonateToday()) {
                donate()
                tc.countDebug("自动捐赠")
            }

            // 进入下一村
            if (roadmap.value) {
                roadmap()
                tc.countDebug("自动进入下一村")
            }

            // 贴罚单
            if (stallAutoTicket.value) {
                pasteTicket()
                tc.countDebug("贴罚单")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "home err:", t)
        } finally {
            Log.record(TAG, "执行结束-$name")
        }
    }

    /**
     * @brief 请走小摊
     */
    private fun sendBack(
        billNo: String,
        seatId: String,
        shopId: String,
        shopUserId: String,
        sentUserId: MutableSet<String>
    ) {
        try {
            val preResponse = AntStallRpcCall.shopSendBackPre(billNo, seatId, shopId, shopUserId)
            val preJson = JSONObject(preResponse)

            if (!ResChecker.checkRes(TAG, preJson)) {
                Log.error(TAG, "sendBackPre err: $preResponse")
                return
            }

            val income = preJson.getJSONObject("astPreviewShopSettleVO").getJSONObject("income")
            val amount = income.getDouble("amount").toInt()

            val sendBackResponse = AntStallRpcCall.shopSendBack(seatId)
            val sendBackJson = JSONObject(sendBackResponse)

            if (ResChecker.checkRes(TAG, sendBackJson)) {
                val amountText = if (amount > 0) "获得金币$amount" else ""
                Log.farm("蚂蚁新村⛪请走[${UserMap.getMaskName(shopUserId)}]的小摊$amountText")
            } else {
                Log.error(TAG, "sendBack err: $sendBackResponse")
            }

            if (stallInviteShop.value) {
                inviteOpen(seatId, sentUserId)
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "sendBack err:", t)
        }
    }

    /**
     * @brief 邀请开店
     */
    private fun inviteOpen(seatId: String, sentUserId: MutableSet<String>) {
        try {
            val response = AntStallRpcCall.rankInviteOpen()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "inviteOpen err: $response")
                return
            }

            val friendRankList = json.getJSONArray("friendRankList")
            for (i in 0 until friendRankList.length()) {
                val friend = friendRankList.getJSONObject(i)
                val friendUserId = friend.getString("userId")

                var isInviteShop = stallInviteShopList.value.contains(friendUserId)
                if (stallInviteShopType.value == StallInviteShopType.DONT_INVITE) {
                    isInviteShop = !isInviteShop
                }

                if (!isInviteShop || sentUserId.contains(friendUserId)) {
                    continue
                }

                if (friend.getBoolean("canInviteOpenShop")) {
                    val inviteResponse = AntStallRpcCall.oneKeyInviteOpenShop(friendUserId, seatId)
                    if (inviteResponse.isEmpty()) {
                        Log.record(TAG, "邀请[${UserMap.getMaskName(friendUserId)}]开店返回空,跳过")
                        continue
                    }

                    val inviteJson = JSONObject(inviteResponse)
                    if (ResChecker.checkRes(TAG, inviteJson)) {
                        Log.farm("蚂蚁新村⛪邀请[${UserMap.getMaskName(friendUserId)}]开店成功")
                        sentUserId.add(friendUserId)
                        return
                    } else {
                        Log.record(
                            TAG,
                            "邀请[${UserMap.getMaskName(friendUserId)}]开店失败: ${
                                inviteJson.optString("errorMessage")
                            }"
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "inviteOpen err:", t)
        }
    }

    /**
     * @brief 处理摊位请走逻辑
     */
    private fun sendBack(seatsMap: JSONObject) {
        try {
            val sentUserId = mutableSetOf<String>()

            // 记录已占用的用户
            for (i in 1..2) {
                val seat = seatsMap.getJSONObject("GUEST_0$i")
                if (seat.getString("status") == "BUSY") {
                    val rentLastUser = seat.optString("rentLastUser")
                    if (rentLastUser.isNotEmpty()) {
                        sentUserId.add(rentLastUser)
                    }
                }
            }

            // 处理每个摊位
            for (i in 1..2) {
                val seat = seatsMap.getJSONObject("GUEST_0$i")
                val seatId = seat.getString("seatId")

                // 摊位空闲时尝试邀请
                if (seat.getString("status") == "FREE") {
                    if (stallInviteShop.value) {
                        Log.record(TAG, "摊位[$i]空闲,尝试邀请好友...")
                        inviteOpen(seatId, sentUserId)
                    }
                    continue
                }

                if (!stallAllowOpenReject.value) {
                    continue
                }

                val rentLastUser = seat.optString("rentLastUser")
                if (rentLastUser.isEmpty()) {
                    continue
                }

                // 白名单跳过
                if (stallWhiteList.value.contains(rentLastUser)) {
                    Log.record(
                        TAG,
                        "好友[${UserMap.getMaskName(rentLastUser)}]在白名单中,跳过请走。"
                    )
                    continue
                }

                val rentLastBill = seat.getString("rentLastBill")
                val rentLastShop = seat.getString("rentLastShop")

                // 黑名单直接赶走
                if (stallBlackList.value.contains(rentLastUser)) {
                    Log.record(
                        TAG,
                        "好友[${UserMap.getMaskName(rentLastUser)}]在黑名单中,立即请走。"
                    )
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser, sentUserId)
                    continue
                }

                // 超时判断
                val bizStartTime = seat.getLong("bizStartTime")
                val endTime = bizStartTime + stallAllowOpenTime.value * 60 * 1000L

                if (System.currentTimeMillis() > endTime) {
                    Log.record(TAG, "好友[${UserMap.getMaskName(rentLastUser)}]摆摊超时,立即请走。")
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser, sentUserId)
                } else {
                    val taskId = "SB|$seatId"
                    if (!hasChildTask(taskId)) {
                        addChildTask(ChildModelTask(taskId, "SB", {
                            if (stallAllowOpenReject.value) {
                                sendBack(
                                    rentLastBill,
                                    seatId,
                                    rentLastShop,
                                    rentLastUser,
                                    sentUserId
                                )
                            }
                        }, endTime))
                        Log.record(TAG, "添加蹲点请走⛪在[${TimeUtil.getCommonDate(endTime)}]执行")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "sendBack err:", t)
        }
    }

    /**
     * @brief 结算金币
     */
    private fun settle(seatsMap: JSONObject) {
        try {
            val seat = seatsMap.getJSONObject("MASTER")
            if (!seat.has("coinsMap")) return

            val coinsMap = seat.getJSONObject("coinsMap")
            val master = coinsMap.getJSONObject("MASTER")
            val assetId = master.getString("assetId")
            val settleCoin = master.getJSONObject("money").getDouble("amount").toInt()
            val fullShow = master.getBoolean("fullShow")

            if (fullShow || settleCoin > 100) {
                val response = AntStallRpcCall.settle(assetId, settleCoin)
                val json = JSONObject(response)
                if (ResChecker.checkRes(TAG, json)) {
                    Log.farm("蚂蚁新村⛪[收取金币]#$settleCoin")
                } else {
                    Log.error(TAG, "settle err: $response")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "settle err:", t)
        }
    }

    /**
     * @brief 收摊
     */
    private fun closeShop() {
        try {
            val response = AntStallRpcCall.shopList()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "closeShop err: $response")
                return
            }

            val astUserShopList = json.getJSONArray("astUserShopList")
            if (astUserShopList.length() == 0) {
                Log.record(TAG, "没有正在摆摊的小摊可收。")
                return
            }

            Log.record(TAG, "检查 ${astUserShopList.length()} 个小摊的收摊时间...")

            for (i in 0 until astUserShopList.length()) {
                val shop = astUserShopList.getJSONObject(i)
                if (shop.getString("status") != "OPEN") continue

                val rentLastEnv = shop.getJSONObject("rentLastEnv")
                val gmtLastRent = rentLastEnv.getLong("gmtLastRent")
                val shopTime = gmtLastRent + stallSelfOpenTime.value * 60 * 1000L
                val shopId = shop.getString("shopId")
                val rentLastBill = shop.getString("rentLastBill")
                val rentLastUser = shop.getString("rentLastUser")

                if (System.currentTimeMillis() > shopTime) {
                    Log.record(TAG, "小摊[$shopId]摆摊时间已到,执行收摊。")
                    shopClose(shopId, rentLastBill, rentLastUser)
                } else {
                    val taskId = "SH|$shopId"
                    if (!hasChildTask(taskId)) {
                        addChildTask(ChildModelTask(taskId, "SH", {
                            if (stallAutoClose.value) {
                                shopClose(shopId, rentLastBill, rentLastUser)
                            }
                            GlobalThreadPools.sleepCompat(300L)
                            if (stallAutoOpen.value) {
                                openShop()
                            }
                        }, shopTime))
                        Log.record(TAG, "添加蹲点收摊⛪在[${TimeUtil.getCommonDate(shopTime)}]执行")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "closeShop err:", t)
        }
    }

    /**
     * @brief 摆摊
     */
    private fun openShop() {
        try {
            val response = AntStallRpcCall.shopList()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "openShop err: $response")
                return
            }

            val astUserShopList = json.getJSONArray("astUserShopList")
            val shopIds: Queue<String> = LinkedList()

            for (i in 0 until astUserShopList.length()) {
                val shop = astUserShopList.getJSONObject(i)
                if (shop.getString("status") == "FREE") {
                    shopIds.add(shop.getString("shopId"))
                }
            }

            if (shopIds.isEmpty()) {
                Log.record(TAG, "没有空闲的小摊可用于摆摊。")
                return
            }

            Log.record(TAG, "找到 ${shopIds.size} 个空闲小摊,开始寻找好友村庄...")
            rankCoinDonate(shopIds)

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "openShop err:", t)
        }
    }

    /**
     * @brief 获取好友排行榜
     */
    private fun rankCoinDonate(shopIds: Queue<String>) {
        try {
            val response = AntStallRpcCall.rankCoinDonate()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "rankCoinDonate err: $response")
                return
            }

            val friendRankList = json.getJSONArray("friendRankList")
            val seats = mutableListOf<Seat>()

            for (i in 0 until friendRankList.length()) {
                val friendRank = friendRankList.getJSONObject(i)
                if (!friendRank.getBoolean("canOpenShop")) continue

                val userId = friendRank.getString("userId")
                var isStallOpen = stallOpenList.value.contains(userId)
                if (stallOpenType.value == StallOpenType.CLOSE) {
                    isStallOpen = !isStallOpen
                }

                if (isStallOpen) {
                    val hot = friendRank.getInt("hot")
                    seats.add(Seat(userId, hot))
                }
            }

            friendHomeOpen(seats, shopIds)

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "rankCoinDonate err:", t)
        }
    }

    /**
     * @brief 在好友村庄开店
     */
    private fun openShop(seatId: String, userId: String, shopId: String) {
        try {
            val response = AntStallRpcCall.shopOpen(seatId, userId, shopId)
            val json = JSONObject(response)

            if (json.optString("resultCode") == "SUCCESS") {
                Log.farm("蚂蚁新村⛪在[${UserMap.getMaskName(userId)}]家摆摊")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "openShop err:", t)
        }
    }

    /**
     * @brief 访问好友主页并开店
     */
    private fun friendHomeOpen(seats: List<Seat>, shopIds: Queue<String>) {
        val sortedSeats = seats.sortedByDescending { it.hot }
        val currentUid = UserMap.currentUid

        for (seat in sortedSeats) {
            val shopId = shopIds.poll() ?: return
            val userId = seat.userId

            try {
                val response = AntStallRpcCall.friendHome(userId)
                val json = JSONObject(response)

                if (json.optString("resultCode") != "SUCCESS") {
                    Log.error(TAG, "新村摆摊失败: $response")
                    return
                }

                val seatsMap = json.getJSONObject("seatsMap")

                // 检查是否已占用摊位
                val guest1 = seatsMap.getJSONObject("GUEST_01")
                val rentUser1 = guest1.optString("rentLastUser")
                val guest2 = seatsMap.getJSONObject("GUEST_02")
                val rentUser2 = guest2.optString("rentLastUser")

                if (currentUid == rentUser1 || currentUid == rentUser2) {
                    Log.record(TAG, "已在[${UserMap.getMaskName(userId)}]家摆摊,跳过")
                    continue
                }

                // 尝试在第一个摊位开店
                if (guest1.getBoolean("canOpenShop")) {
                    openShop(guest1.getString("seatId"), userId, shopId)
                } else if (guest2.getBoolean("canOpenShop")) {
                    openShop(guest2.getString("seatId"), userId, shopId)
                }

            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
            }
        }
    }

    /**
     * @brief 关闭商店
     */
    private fun shopClose(shopId: String, billNo: String, userId: String) {
        try {
            val preResponse = AntStallRpcCall.preShopClose(shopId, billNo)
            val preJson = JSONObject(preResponse)

            if (!ResChecker.checkRes(TAG, preJson)) {
                Log.error(TAG, "shopClose err: $preResponse")
                return
            }

            val income = preJson.getJSONObject("astPreviewShopSettleVO").getJSONObject("income")
            val closeResponse = AntStallRpcCall.shopClose(shopId)
            val closeJson = JSONObject(closeResponse)

            if (ResChecker.checkRes(TAG, closeJson)) {
                Log.farm(
                    "蚂蚁新村⛪收取在[${UserMap.getMaskName(userId)}]的摊位获得${
                        income.getString(
                            "amount"
                        )
                    }"
                )
            } else {
                Log.error(TAG, "shopClose err: $closeResponse")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "shopClose err:", t)
        }
    }

    /**
     * @brief 处理任务列表
     */
    private fun taskList() {
        try {
            val response = AntStallRpcCall.taskList()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "taskList err: $response")
                return
            }

            // 签到
            val signListModel = json.getJSONObject("signListModel")
            if (!signListModel.getBoolean("currentKeySigned")) {
                Log.record(TAG, "开始执行每日签到...")
                signToday()
            }

            val taskModels = json.getJSONArray("taskModels")
            Log.record(TAG, "开始检查 ${taskModels.length()} 个新村任务...")

            for (i in 0 until taskModels.length()) {
                try {
                    val task = taskModels.getJSONObject(i)
                    val taskStatus = task.getString("taskStatus")
                    val taskType = task.getString("taskType")
                    val beforeState = StallTaskState(taskType, taskStatus.uppercase())

                    // 已完成的任务领取奖励
                    if (taskStatus == "FINISHED") {
                        Log.record(TAG, "任务[$taskType]已完成,尝试领取奖励...")
                        receiveTaskAward(taskType, beforeState)
                        continue
                    }

                    if (taskStatus != "TODO") continue

                    val bizInfo = JSONObject(task.getString("bizInfo"))
                    val title = bizInfo.optString("title", bizInfo.optString("taskTitle", taskType))
                    val actionType = bizInfo.optString("actionType", task.optString("actionType"))
                    when (val decision = StallTaskSafetyPolicy.classify(taskType, title, actionType)) {
                        StallTaskDecision.FINISH_RPC -> {
                            if (finishTask(taskType, beforeState)) {
                                Log.farm("蚂蚁新村💣任务[$title]完成")
                            }
                        }

                        StallTaskDecision.HANDLE_QA -> {
                            if (ReadingDada.answerQuestion(bizInfo)) {
                                val refreshed = refreshTaskState(taskType)
                                if (StallTaskProtocol.isAdvanced(beforeState, refreshed) && StallTaskProtocol.isRewardReady(refreshed)) {
                                    receiveTaskAward(taskType, refreshed!!)
                                } else {
                                    Log.record(TAG, "新村任务[$title]答题已提交但状态未推进，保留后续重试")
                                }
                            }
                        }

                        StallTaskDecision.HANDLE_INVITE -> {
                            if (inviteRegister()) {
                                val refreshed = refreshTaskState(taskType)
                                if (!StallTaskProtocol.isAdvanced(beforeState, refreshed)) {
                                    Log.record(TAG, "新村任务[$title]邀请已受理但状态未推进，保留后续重试")
                                }
                            }
                        }

                        StallTaskDecision.HANDLE_XLIGHT -> {
                            handleXlightTask(beforeState)
                        }

                        else -> {
                            Log.record(TAG, "新村任务[$title]安全策略跳过[$decision]")
                        }
                    }

                    GlobalThreadPools.sleepCompat(200L)

                } catch (t: Throwable) {
                    Log.printStackTrace(TAG, "taskList for err:", t)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "taskList err:", t)
        }
    }

    /**
     * @brief 处理X-light任务
     */
    private fun handleXlightTask(beforeState: StallTaskState) {
        try {
            val response = AntStallRpcCall.xlightPlugin()
            val json = JSONObject(response)

            if (StallTaskProtocol.isXlightTrafficLimited(json)) {
                Log.error(TAG, "XLight 命中流量风控[217/61002]，停止当前任务链路")
                return
            }

            if (!json.has("playingResult")) {
                Log.error(TAG, "taskList.xlightPlugin err: ${json.optString("resultDesc")}")
                return
            }

            val playingResult = json.getJSONObject("playingResult")
            val pid = playingResult.getString("playingBizId")
            val eventList = JsonUtil.getValueByPathObject(
                playingResult,
                "eventRewardDetail.eventRewardInfoList"
            ) as? JSONArray ?: return

            if (eventList.length() == 0) return

            for (j in 0 until eventList.length()) {
                try {
                    val eventInfo = eventList.getJSONObject(j)
                    val finishResponse = AntStallRpcCall.finish(pid, eventInfo)
                    Log.record("延时5S 木兰市集")
                    GlobalThreadPools.sleepCompat(5000)

                    val finishJson = JSONObject(finishResponse)
                    if (!finishJson.optBoolean("success")) {
                        Log.error(TAG, "taskList.finish err: ${finishJson.optString("resultDesc")}")
                        continue
                    }
                    val refreshed = refreshTaskState(beforeState.taskType)
                    if (StallTaskProtocol.isAdvanced(beforeState, refreshed)) {
                        Log.farm("蚂蚁新村⛪XLight 任务状态已推进")
                        return
                    } else {
                        Log.record(TAG, "XLight 事件已受理但任务状态未推进，继续保留重试")
                    }
                } catch (t: Throwable) {
                    Log.printStackTrace(TAG, "taskList for err:", t)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleXlightTask err:", t)
        }
    }

    /**
     * @brief 今日签到
     */
    private fun signToday() {
        try {
            val response = AntStallRpcCall.signToday()
            val json = JSONObject(response)

            if (ResChecker.checkRes(TAG, json)) {
                val confirmation = JSONObject(AntStallRpcCall.taskList())
                if (ResChecker.checkRes(TAG, confirmation) && StallTaskProtocol.isSignConfirmed(confirmation)) {
                    Log.farm("蚂蚁新村⛪[签到成功]")
                } else {
                    Log.record(TAG, "新村签到已受理但服务端状态未刷新，保留后续重试")
                }
            } else {
                Log.error(TAG, "signToday err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "signToday err:", t)
        }
    }

    /**
     * @brief 领取任务奖励
     */
    private fun receiveTaskAward(taskType: String, beforeState: StallTaskState): Boolean {
        if (!stallReceiveAward.value) return false

        try {
            val response = AntStallRpcCall.receiveTaskAward(taskType)
            val json = JSONObject(response)

            if (json.optBoolean("success")) {
                val refreshed = refreshTaskState(taskType)
                if (StallTaskProtocol.isAdvanced(beforeState, refreshed)) {
                    Log.farm("蚂蚁新村⛪[领取奖励]")
                    return true
                }
                Log.record(TAG, "新村任务[$taskType]领奖已受理但状态未推进，保留后续重试")
            } else {
                Log.error(TAG, "receiveTaskAward err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "receiveTaskAward err:", t)
        }
        return false
    }

    /**
     * @brief 完成任务
     */
    private fun finishTask(taskType: String, beforeState: StallTaskState): Boolean {
        try {
            val response = AntStallRpcCall.finishTask(
                "${taskType}_${System.currentTimeMillis()}",
                taskType
            )
            val json = JSONObject(response)

            if (json.optBoolean("success")) {
                val refreshed = refreshTaskState(taskType)
                return if (StallTaskProtocol.isAdvanced(beforeState, refreshed)) {
                    true
                } else {
                    Log.record(TAG, "新村任务[$taskType]完成已受理但状态未推进，保留后续重试")
                    false
                }
            } else {
                Log.error(TAG, "finishTask err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "finishTask err:", t)
        }
        return false
    }

    private fun refreshTaskState(taskType: String): StallTaskState? {
        return try {
            val response = JSONObject(AntStallRpcCall.taskList())
            if (!ResChecker.checkRes(TAG, response)) {
                null
            } else {
                StallTaskProtocol.statusOf(response, taskType)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "refreshTaskState err:", t)
            null
        }
    }

    /**
     * @brief 邀请好友注册
     */
    private fun inviteRegister(): Boolean {
        if (!stallInviteRegister.value) return false

        try {
            val response = AntStallRpcCall.rankInviteRegister()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "rankInviteRegister err: $response")
                return false
            }

            val friendRankList = json.optJSONArray("friendRankList") ?: return false
            if (friendRankList.length() == 0) return false

            for (i in 0 until friendRankList.length()) {
                val friend = friendRankList.getJSONObject(i)

                if (!friend.optBoolean("canInviteRegister", false) ||
                    friend.getString("userStatus") != "UNREGISTER"
                ) {
                    continue
                }

                val userId = friend.getString("userId")
                if (!stallInviteRegisterList.value.contains(userId)) {
                    continue
                }

                val inviteResponse = AntStallRpcCall.friendInviteRegister(userId)
                val inviteJson = JSONObject(inviteResponse)

                if (ResChecker.checkRes(TAG, inviteJson)) {
                    Log.farm("蚂蚁新村⛪邀请好友[${UserMap.getMaskName(userId)}]#开通新村")
                    return true
                } else {
                    Log.error(TAG, "friendInviteRegister err: $inviteJson")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "InviteRegister err:", t)
        }
        return false
    }

    /**
     * @brief 分享助力
     */
    private fun shareP2P(): String? {
        try {
            val response = AntStallRpcCall.shareP2P()
            val json = JSONObject(response)

            if (json.optBoolean("success")) {
                val shareId = json.getString("shareId")
                Log.record(TAG, "蚂蚁新村⛪[分享助力]")
                return shareId
            } else {
                Log.error(TAG, "shareP2P err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "shareP2P err:", t)
        }
        return null
    }

    /**
     * @brief 助力好友
     */
    private fun assistFriend() {
        try {
            if (!Status.canAntStallAssistFriendToday()) {
                Log.record(TAG, "今日新村助力次数已用完。")
                return
            }

            val friendSet = assistFriendList.value
            if (friendSet.isEmpty()) {
                Log.record(TAG, "未设置新村助力好友列表。")
                return
            }

            Log.record(TAG, "开始为 ${friendSet.size} 位好友进行新村助力...")

            for (uid in friendSet) {
                val shareId = Base64.encodeToString(
                    "$uid-${RandomUtil.getRandomInt(5)}ANUTSALTML_2PA_SHARE".toByteArray(),
                    Base64.NO_WRAP
                )

                val response = AntStallRpcCall.achieveBeShareP2P(shareId)
                val json = JSONObject(response)
                val name = UserMap.getMaskName(uid)

                if (!json.optBoolean("success")) {
                    when (json.getString("code")) {
                        "600000028" -> {
                            Log.record(TAG, "新村助力🮐被助力次数上限[$name]")
                            continue
                        }

                        "600000027" -> {
                            Log.record(TAG, "新村助力💪今日助力他人次数上限")
                            Status.antStallAssistFriendToday()
                            return
                        }

                        else -> {
                            Log.error(TAG, "新村助力😔失败[$name]${json.optString("desc")}")
                            continue
                        }
                    }
                }

                Log.farm("新村助力🎉成功[$name]")
                GlobalThreadPools.sleepCompat(5000)
            }

            Status.antStallAssistFriendToday()

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "assistFriend err:", t)
        }
    }

    /**
     * @brief 捐赠项目
     */
    private fun donate() {
        try {
            val response = AntStallRpcCall.projectList()
            val json = JSONObject(response)

            if (json.optString("resultCode", "") != "SUCCESS") return

            // 检查余额
            val userInfo = json.optJSONObject("astUserInfoVO")
            if (userInfo != null) {
                val currentCoinAmount = userInfo.optJSONObject("currentCoin")
                    ?.optDouble("amount", 0.0) ?: 0.0

                if (currentCoinAmount < 15000) {
                    return
                }
            }

            // 查找在线项目
            val projects = json.optJSONArray("astProjectVOS") ?: return

            for (i in 0 until projects.length()) {
                val project = projects.optJSONObject(i) ?: continue

                if (project.optString("status", "") == "ONLINE") {
                    val projectId = project.optString("projectId", "")

                    // 获取项目详情
                    val detailResponse = AntStallRpcCall.projectDetail(projectId)
                    val detailJson = JSONObject(detailResponse)

                    if (detailJson.optString("resultCode", "") == "SUCCESS") {
                        // 执行捐赠
                        val donateResponse = AntStallRpcCall.projectDonate(projectId)
                        val donateJson = JSONObject(donateResponse)

                        val astProjectVO = donateJson.optJSONObject("astProjectVO")
                        if (astProjectVO != null) {
                            val title = astProjectVO.optString("title", "未知项目")

                            if (donateJson.optString("resultCode", "") == "SUCCESS") {
                                Log.farm("蚂蚁新村⛪[捐赠:$title]")
                                Status.setStallDonateToday()
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "donate err:", t)
        }
    }

    /**
     * @brief 进入下一村
     */
    private fun roadmap() {
        try {
            val response = AntStallRpcCall.roadmap()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) return

            val roadList = json.getJSONArray("roadList")
            var hasNewVillage = false

            for (i in 0 until roadList.length()) {
                val road = roadList.getJSONObject(i)

                if (road.getString("status") != "NEW") continue

                hasNewVillage = true
                val villageName = road.getString("villageName")
                val flagKey = "stall::roadmap::$villageName"

                if (Status.hasFlagToday(flagKey)) {
                    Log.record(TAG, "今日已进入[$villageName],跳过重复打卡。")
                    continue
                }

                Log.farm("蚂蚁新村⛪[进入:$villageName]成功")
                Status.setFlagToday(flagKey)
                break
            }

            if (!hasNewVillage) {
                Log.record(TAG, "所有村庄都已解锁,无需进入下一村。")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "roadmap err:", t)
        }
    }

    /**
     * @brief 收集肥料
     */
    private fun collectManure() {
        try {
            val response = AntStallRpcCall.queryManureInfo()
            val json = JSONObject(response)

            if (!json.optBoolean("success")) {
                Log.error(TAG, "collectManure err: $response")
                return
            }

            val astManureInfoVO = json.getJSONObject("astManureInfoVO")
            if (astManureInfoVO.optBoolean("hasManure")) {
                val manure = astManureInfoVO.getInt("manure")
                val collectResponse = AntStallRpcCall.collectManure()
                val collectJson = JSONObject(collectResponse)

                if (ResChecker.checkRes(TAG, collectJson)) {
                    Log.farm("蚂蚁新村⛪获得肥料${manure}g")
                }
            } else {
                Log.record(TAG, "没有可收取的肥料。")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "collectManure err:", t)
        }
    }

    /**
     * @brief 丢肥料批量处理
     */
    private fun throwManure(dynamicList: JSONArray) {
        // 前置检查:如果今日已达上限,直接跳过
        if (Status.hasFlagToday(StatusFlags.FLAG_ANTSTALL_THROW_MANURE_LIMIT)) {
            return
        }

        try {
            val response = AntStallRpcCall.throwManure(dynamicList)
            val json = JSONObject(response)

            // 先于ResChecker判断特定业务错误码
            val resultCode = json.optString("resultCode")
            if (resultCode == "B_OVER_LIMIT_COUNT_OF_THROW_TO_FRIEND") {
                Log.record(TAG, "检测到今日丢肥料次数已达上限,停止后续尝试")
                Status.setFlagToday(StatusFlags.FLAG_ANTSTALL_THROW_MANURE_LIMIT)
                return
            }

            // 正常的响应检查
            if (ResChecker.checkRes(TAG, json)) {
                Log.farm("蚂蚁新村⛪打肥料成功")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "throwManure err:", t)
        } finally {
            GlobalThreadPools.sleepCompat(1000)
        }
    }

    /**
     * @brief 丢肥料主流程
     */
    private fun throwManure() {
        try {
            val response = AntStallRpcCall.dynamicLoss()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "throwManure err: $response")
                return
            }

            val astLossDynamicVOS = json.getJSONArray("astLossDynamicVOS")
            var dynamicList = JSONArray()

            for (i in 0 until astLossDynamicVOS.length()) {
                val lossDynamic = astLossDynamicVOS.getJSONObject(i)

                if (lossDynamic.has("specialEmojiVO")) continue

                val objectId = lossDynamic.getString("objectId")
                var isThrowManure = stallThrowManureList.value.contains(objectId)

                if (stallThrowManureType.value == StallThrowManureType.DONT_THROW) {
                    isThrowManure = !isThrowManure
                }

                if (!isThrowManure) continue

                val dynamic = JSONObject().apply {
                    put("bizId", lossDynamic.getString("bizId"))
                    put("bizType", lossDynamic.getString("bizType"))
                }
                dynamicList.put(dynamic)

                if (dynamicList.length() == 5) {
                    throwManure(dynamicList)
                    dynamicList = JSONArray()
                }
            }

            if (dynamicList.length() > 0) {
                throwManure(dynamicList)
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "throwManure err:", t)
        }
    }

    /**
     * @brief 结算应收金币
     */
    private fun settleReceivable() {
        try {
            val response = AntStallRpcCall.settleReceivable()
            val json = JSONObject(response)

            if (ResChecker.checkRes(TAG, json)) {
                Log.farm("蚂蚁新村⛪收取应收金币")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "settleReceivable err:", t)
        }
    }

    /**
     * @brief 贴罚单
     */
    private fun pasteTicket() {
        try {
            if (!Status.canPasteTicketTime()) {
                Log.record(TAG, "未到贴罚单时间或今日已贴完。")
                return
            }

            Log.record(TAG, "开始巡逻,寻找可贴罚单的好友...")

            while (true) {
                try {
                    val response = AntStallRpcCall.nextTicketFriend()
                    val json = JSONObject(response)

                    if (!json.optBoolean("success")) {
                        Log.error(
                            TAG,
                            "pasteTicket.nextTicketFriend err: ${json.optString("resultDesc")}"
                        )
                        return
                    }

                    if (json.getInt("canPasteTicketCount") == 0) {
                        Log.record(TAG, "蚂蚁新村👍[今日罚单已贴完]")
                        Status.pasteTicketTime()
                        return
                    }

                    val friendId = json.optString("friendUserId")
                    if (friendId.isEmpty()) {
                        Log.record(TAG, "没有更多可贴罚单的好友了。")
                        return
                    }

                    var isStallTicket = stallTicketList.value.contains(friendId)
                    if (stallTicketType.value == StallTicketType.DONT_TICKET) {
                        isStallTicket = !isStallTicket
                    }

                    if (!isStallTicket) continue

                    // 访问好友主页
                    val homeResponse = AntStallRpcCall.friendHome(friendId)
                    val homeJson = JSONObject(homeResponse)

                    if (!homeJson.optBoolean("success")) {
                        Log.error(
                            TAG,
                            "pasteTicket.friendHome err: ${homeJson.optString("resultDesc")}"
                        )
                        return
                    }

                    val seatsMap = homeJson.getJSONObject("seatsMap")
                    val keys = seatsMap.keys()

                    while (keys.hasNext()) {
                        try {
                            val key = keys.next()
                            val propertyValue = seatsMap.get(key)

                            if (propertyValue !is JSONObject || propertyValue.length() == 0) {
                                continue
                            }

                            if (propertyValue.getBoolean("canOpenShop") ||
                                propertyValue.getString("status") != "BUSY" ||
                                !propertyValue.getBoolean("overTicketProtection")
                            ) {
                                continue
                            }

                            val rentLastUser = propertyValue.getString("rentLastUser")
                            val ticketResponse = AntStallRpcCall.ticket(
                                propertyValue.getString("rentLastBill"),
                                propertyValue.getString("seatId"),
                                propertyValue.getString("rentLastShop"),
                                rentLastUser,
                                propertyValue.getString("userId")
                            )

                            val ticketJson = JSONObject(ticketResponse)
                            if (!ticketJson.optBoolean("success")) {
                                Log.error(
                                    TAG,
                                    "pasteTicket.ticket err: ${ticketJson.optString("resultDesc")}"
                                )
                                return
                            }

                            Log.farm("蚂蚁新村🚫在[${UserMap.getMaskName(friendId)}]贴罚单")

                        } finally {
                            GlobalThreadPools.sleepCompat(1000)
                        }
                    }

                } finally {
                    GlobalThreadPools.sleepCompat(1500)
                }
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "pasteTicket err:", t)
        }
    }

    /**
     * @brief 摆摊操作类型
     */
    interface StallOpenType {
        companion object {
            const val OPEN = 0
            const val CLOSE = 1
            val nickNames = arrayOf("选中摆摊", "选中不摆摊")
        }
    }

    /**
     * @brief 贴罚单操作类型
     */
    interface StallTicketType {
        companion object {
            const val TICKET = 0
            const val DONT_TICKET = 1
            val nickNames = arrayOf("选中贴罚单", "选中不贴罚单")
        }
    }

    /**
     * @brief 丢肥料操作类型
     */
    interface StallThrowManureType {
        companion object {
            const val THROW = 0
            const val DONT_THROW = 1
            val nickNames = arrayOf("选中丢肥料", "选中不丢肥料")
        }
    }

    /**
     * @brief 邀请摆摊操作类型
     */
    interface StallInviteShopType {
        companion object {
            const val INVITE = 0
            const val DONT_INVITE = 1
            val nickNames = arrayOf("选中邀请", "选中不邀请")
        }
    }

    companion object {
        private const val TAG = "AntStall"

    }
}
