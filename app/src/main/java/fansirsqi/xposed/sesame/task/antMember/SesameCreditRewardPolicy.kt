package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject

data class SesameAlchemyNextDayAward(
    val awardId: String,
    val available: Boolean,
    val pointValue: Int
)

data class SesameAlchemyNextDaySnapshot(
    val recognized: Boolean,
    val award: SesameAlchemyNextDayAward?
)

data class SesameAlchemyTimeLimitedTask(
    val templateId: String,
    val title: String,
    val state: Int,
    val tomorrow: Boolean,
    val rewardAmount: Int
) {
    val claimable: Boolean
        get() = templateId.isNotBlank() && state == 1 && !tomorrow
}

data class SesameAlchemyTimeLimitedSnapshot(
    val recognized: Boolean,
    val task: SesameAlchemyTimeLimitedTask?
)

data class SesameCreditFeedback(
    val id: String,
    val categoryId: String,
    val title: String,
    val status: String,
    val potentialSize: Int
)

data class SesameCreditFeedbackSnapshot(
    val recognized: Boolean,
    val items: List<SesameCreditFeedback>
) {
    val potentialTotal: Int
        get() = items
            .filter { it.status.equals("UNCLAIMED", true) }
            .sumOf(SesameCreditFeedback::potentialSize)
}

data class SesameCreditTaskState(
    val templateId: String,
    val recordId: String,
    val title: String,
    val finishFlag: Boolean,
    val actionText: String,
    val bizType: String,
    val actionUrl: String,
    val todayFinish: Boolean = false
)

data class SesameCreditTaskSnapshot(
    val recognized: Boolean,
    val tasks: List<SesameCreditTaskState>
)

enum class SesameCreditTaskDecision {
    EXECUTE_FREE,
    TERMINAL,
    SKIP_AD,
    SKIP_APP,
    SKIP_FINANCIAL,
    SKIP_UNSUPPORTED
}

enum class SesameAlchemyRedPacketState {
    AVAILABLE,
    UNAVAILABLE,
    RETRY
}

object SesameCreditRewardPolicy {
    const val ZHIMA_PIGEON_TEMPLATE_ID = "hjwf_myzy_gyxj_erfang"

    private val financialSignals = listOf(
        "RECHARGE",
        "TOP_UP",
        "ORDER",
        "PURCHASE",
        "PAY",
        "WITHDRAW",
        "LOAN",
        "充值",
        "下单",
        "购买",
        "支付",
        "提现",
        "借款",
        "贷款"
    )
    private val unsupportedSignals = listOf(
        "GAME",
        "MULTI_STAGE",
        "游戏",
        "邀请",
        "助力",
        "上传",
        "授权",
        "办理"
    )
    private val adSignals = listOf(
        "AD_TASK",
        "LIGHT_AD",
        "ADVERT",
        "广告"
    )

    fun parseNextDayAward(response: String): SesameAlchemyNextDaySnapshot {
        val root = parseSuccessfulRoot(response)
            ?: return SesameAlchemyNextDaySnapshot(false, null)
        for (container in responseContainers(root)) {
            if (!container.has("entryList")) continue
            val entries = container.optJSONArray("entryList")
                ?: return SesameAlchemyNextDaySnapshot(false, null)
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                if (entry.optString("entryCode") != "ALCHEMY_STAGE_REWARD") {
                    continue
                }
                val award = entry.optJSONObject("nextDayAwardDTO")
                    ?: return SesameAlchemyNextDaySnapshot(true, null)
                return SesameAlchemyNextDaySnapshot(
                    recognized = true,
                    award = SesameAlchemyNextDayAward(
                        awardId = award.optString("awardId"),
                        available = award.optBoolean("awardAvailable", false),
                        pointValue = award.optInt("pointValue", 0)
                    )
                )
            }
            return SesameAlchemyNextDaySnapshot(true, null)
        }
        return SesameAlchemyNextDaySnapshot(false, null)
    }

    fun isNextDayAwardConfirmed(response: String, awardId: String): Boolean {
        if (awardId.isBlank()) return false
        val snapshot = parseNextDayAward(response)
        if (!snapshot.recognized) return false
        val award = snapshot.award ?: return true
        return award.awardId != awardId || !award.available
    }

    fun parseTimeLimitedTask(
        response: String
    ): SesameAlchemyTimeLimitedSnapshot {
        val root = parseSuccessfulRoot(response)
            ?: return SesameAlchemyTimeLimitedSnapshot(false, null)
        for (container in responseContainers(root)) {
            if (!container.has("timeLimitedTaskVO")) continue
            val task = container.optJSONObject("timeLimitedTaskVO")
                ?: return SesameAlchemyTimeLimitedSnapshot(true, null)
            return SesameAlchemyTimeLimitedSnapshot(
                recognized = true,
                task = SesameAlchemyTimeLimitedTask(
                    templateId = task.optString("templateId"),
                    title = task.optString("longTitle")
                        .ifBlank { task.optString("title") },
                    state = task.optInt("state", 0),
                    tomorrow = task.optBoolean("tomorrow", false),
                    rewardAmount = task.optInt("rewardAmount", 0)
                )
            )
        }
        return SesameAlchemyTimeLimitedSnapshot(false, null)
    }

    fun isTimeLimitedRewardConfirmed(
        response: String,
        templateId: String
    ): Boolean {
        if (templateId.isBlank()) return false
        val snapshot = parseTimeLimitedTask(response)
        if (!snapshot.recognized) return false
        val task = snapshot.task ?: return true
        return task.templateId != templateId || !task.claimable
    }

    fun parseFeedback(response: String): SesameCreditFeedbackSnapshot {
        val root = parseSuccessfulRoot(response)
            ?: return SesameCreditFeedbackSnapshot(false, emptyList())
        for (container in responseContainers(root)) {
            if (!container.has("creditFeedbackVOS")) continue
            val array = container.optJSONArray("creditFeedbackVOS")
                ?: return SesameCreditFeedbackSnapshot(false, emptyList())
            val items = mutableListOf<SesameCreditFeedback>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                items += SesameCreditFeedback(
                    id = item.optString("creditFeedbackId"),
                    categoryId = item.optString("cateId"),
                    title = item.optString("title"),
                    status = item.optString("status"),
                    potentialSize = parsePotentialSize(item)
                )
            }
            return SesameCreditFeedbackSnapshot(true, items)
        }
        return SesameCreditFeedbackSnapshot(false, emptyList())
    }

    fun isFeedbackCollectionConfirmed(
        response: String,
        targetIds: Set<String>
    ): Boolean {
        if (targetIds.isEmpty() || targetIds.any(String::isBlank)) return false
        val snapshot = parseFeedback(response)
        if (!snapshot.recognized) return false
        return snapshot.items.none {
            it.id in targetIds && it.status.equals("UNCLAIMED", true)
        }
    }

    fun parseTaskSnapshot(response: String): SesameCreditTaskSnapshot {
        val root = parseSuccessfulRoot(response)
            ?: return SesameCreditTaskSnapshot(false, emptyList())
        val tasks = mutableListOf<SesameCreditTaskState>()
        var recognized = false
        for (container in responseContainers(root)) {
            for (key in listOf("toCompleteVOS", "waitJoinTaskVOS", "waitCompleteTaskVOS")) {
                if (!container.has(key)) continue
                recognized = true
                addTasks(container.optJSONArray(key), tasks)
            }
            val daily = container.optJSONObject("dailyTaskListVO") ?: continue
            for (key in listOf("waitJoinTaskVOS", "waitCompleteTaskVOS")) {
                if (!daily.has(key)) continue
                recognized = true
                addTasks(daily.optJSONArray(key), tasks)
            }
        }
        return SesameCreditTaskSnapshot(
            recognized,
            tasks.distinctBy { it.templateId.ifBlank { it.recordId } }
        )
    }

    fun decideTask(task: SesameCreditTaskState): SesameCreditTaskDecision {
        if (
            task.finishFlag ||
            task.todayFinish ||
            task.actionText in setOf("已完成", "已领取")
        ) {
            return SesameCreditTaskDecision.TERMINAL
        }
        if (task.templateId.isBlank()) {
            return SesameCreditTaskDecision.SKIP_UNSUPPORTED
        }
        val riskText = listOf(
            task.templateId,
            task.title,
            task.bizType,
            task.actionUrl
        ).joinToString(" ")
        if (financialSignals.any { riskText.contains(it, true) }) {
            return SesameCreditTaskDecision.SKIP_FINANCIAL
        }
        if (adSignals.any { riskText.contains(it, true) }) {
            return SesameCreditTaskDecision.SKIP_AD
        }
        if (
            task.actionUrl.startsWith("alipays://", true) &&
            !task.actionUrl.contains("chInfo", true)
        ) {
            return SesameCreditTaskDecision.SKIP_APP
        }
        if (unsupportedSignals.any { riskText.contains(it, true) }) {
            return SesameCreditTaskDecision.SKIP_UNSUPPORTED
        }
        return SesameCreditTaskDecision.EXECUTE_FREE
    }

    fun isTaskCompletionConfirmed(response: String, templateId: String): Boolean {
        if (templateId.isBlank()) return false
        val snapshot = parseTaskSnapshot(response)
        if (!snapshot.recognized) return false
        val task = snapshot.tasks.firstOrNull { it.templateId == templateId }
            ?: return false
        return decideTask(task) == SesameCreditTaskDecision.TERMINAL
    }

    fun isZhimaPigeonCompletionConfirmed(
        taskResponse: String,
        templateId: String,
        beforePotentialTotal: Int,
        afterPotentialTotal: Int
    ): Boolean {
        return isTaskCompletionConfirmed(taskResponse, templateId) ||
            afterPotentialTotal > beforePotentialTotal
    }

    fun parseRedPacketState(response: String): SesameAlchemyRedPacketState {
        val root = parseSuccessfulRoot(response)
            ?: return SesameAlchemyRedPacketState.RETRY
        val data = root.optJSONObject("data")
            ?: return SesameAlchemyRedPacketState.RETRY
        return if (data.optBoolean("withdrawable", false)) {
            SesameAlchemyRedPacketState.AVAILABLE
        } else {
            SesameAlchemyRedPacketState.UNAVAILABLE
        }
    }

    fun isActionAccepted(response: String): Boolean {
        return runCatching { JSONObject(response) }
            .getOrNull()
            ?.let(::isSuccess)
            ?: false
    }

    private fun addTasks(
        array: JSONArray?,
        target: MutableList<SesameCreditTaskState>
    ) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val task = array.optJSONObject(index) ?: continue
            target += SesameCreditTaskState(
                templateId = task.optString("templateId"),
                recordId = task.optString("recordId"),
                title = task.optString("title"),
                finishFlag = task.optBoolean("finishFlag", false),
                actionText = task.optString("actionText"),
                bizType = task.optString("bizType"),
                actionUrl = task.optString("actionUrl"),
                todayFinish = task.optBoolean("todayFinish", false)
            )
        }
    }

    private fun parsePotentialSize(item: JSONObject): Int {
        return item.optString("potentialSize")
            .toDoubleOrNull()
            ?.toInt()
            ?: item.optInt("potentialSize", 0)
    }

    private fun parseSuccessfulRoot(response: String): JSONObject? {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return null
        return root.takeIf(::isSuccess)
    }

    private fun responseContainers(root: JSONObject): List<JSONObject> {
        return listOfNotNull(
            root,
            root.optJSONObject("data"),
            root.optJSONObject("resData"),
            root.optJSONObject("result")
        )
    }

    private fun isSuccess(root: JSONObject): Boolean {
        if (root.optBoolean("success", false)) return true
        return sequenceOf(
            root.optString("resultCode"),
            root.optString("code"),
            root.optString("resultStatus")
        ).any {
            it.equals("SUCCESS", true) || it == "100" || it == "0"
        }
    }
}
