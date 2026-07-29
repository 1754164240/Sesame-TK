package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.task.antSports.AntSportsRpcCall
import fansirsqi.xposed.sesame.task.antSports.AntSportsStepSync
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject

object AntFarmWalkDonateTask {
    private const val MIN_DONATE_STEP_COUNT = 1000
    private const val DEFAULT_SYNC_STEP_COUNT = 22000
    private const val DEFAULT_ACTIVITY_ID = "20160524001110000000000000001002"

    fun isWalkDonateTask(bizKey: String, title: String): Boolean {
        val normalizedBizKey = bizKey.uppercase()
        val hasDonateWord = normalizedBizKey.contains("DONATE") || normalizedBizKey.contains("DONATION")
        return title.contains("捐步") ||
            (normalizedBizKey.contains("WALK") && hasDonateWord) ||
            (normalizedBizKey.contains("STEP") && hasDonateWord)
    }

    fun isEligibleStepCount(stepCount: Int): Boolean {
        return stepCount > MIN_DONATE_STEP_COUNT
    }

    fun shouldSyncStepBeforeDonate(stepCount: Int): Boolean {
        return stepCount <= MIN_DONATE_STEP_COUNT
    }

    fun extractStepCount(response: JSONObject): Int {
        return response.optInt("stepCount", -1)
    }

    fun extractDonateToken(response: JSONObject): String? {
        return response.optJSONObject("walkDonateHomeModel")
            ?.optString("donateToken")
            ?.takeIf { it.isNotBlank() }
    }

    fun extractActivityId(response: JSONObject): String {
        return response.optJSONObject("walkDonateHomeModel")
            ?.optJSONObject("walkCharityActivityModel")
            ?.optString("activityId")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_ACTIVITY_ID
    }

    fun isDonateSuccess(response: JSONObject): Boolean {
        return response.optBoolean("isSuccess") ||
            response.optString("resultDesc").contains("已捐步") ||
            response.optString("resultView").contains("已捐步") ||
            response.optString("memo").contains("已捐步")
    }

    fun isGeneralSuccess(response: JSONObject): Boolean {
        if (response.optBoolean("success") || response.optBoolean("isSuccess")) {
            return true
        }
        val resultCode = response.optString("resultCode")
        return resultCode.equals("SUCCESS", ignoreCase = true) ||
            resultCode == "100" ||
            resultCode == "200"
    }

    fun donateIfEligible(logTag: String): Boolean {
        val currentUid = UserMap.currentUid
        if (currentUid != null && !Status.canExchangeToday(currentUid)) {
            Log.record(logTag, "今日已完成捐步，跳过庄园捐步任务")
            return true
        }

        var stepResponse = JSONObject(AntSportsRpcCall.queryDonationSteps())
        if (!ResChecker.checkRes(logTag, stepResponse)) {
            Log.record(logTag, "查询捐步步数失败: " + stepResponse.optString("resultDesc", stepResponse.toString()))
            return false
        }

        var stepCount = extractStepCount(stepResponse)
        if (shouldSyncStepBeforeDonate(stepCount)) {
            Log.record(logTag, "今日步数不足${MIN_DONATE_STEP_COUNT + 1}步，先尝试同步步数: $stepCount")
            if (AntSportsStepSync.syncStep(DEFAULT_SYNC_STEP_COUNT, logTag)) {
                Thread.sleep(1500)
                stepResponse = JSONObject(AntSportsRpcCall.queryDonationSteps())
                if (!ResChecker.checkRes(logTag, stepResponse)) {
                    Log.record(logTag, "同步后查询捐步步数失败: " + stepResponse.optString("resultDesc", stepResponse.toString()))
                    return false
                }
                stepCount = extractStepCount(stepResponse)
            }
        }

        if (!isEligibleStepCount(stepCount)) {
            Log.record(logTag, "今日步数不足${MIN_DONATE_STEP_COUNT + 1}步，暂不执行庄园捐步任务: $stepCount")
            return false
        }

        AntSportsRpcCall.walkDonateSignInfo(stepCount)
        val homeResponse = JSONObject(AntSportsRpcCall.donateWalkHome(stepCount))
        if (!ResChecker.checkRes(logTag, homeResponse)) {
            Log.record(logTag, "查询捐步首页失败: " + homeResponse.optString("resultDesc", homeResponse.toString()))
            return false
        }

        if (isAlreadyDonated(homeResponse)) {
            currentUid?.let { Status.exchangeToday(it) }
            Log.record(logTag, "今日已捐步，庄园捐步任务等待刷新领取")
            return true
        }

        if (!canExchange(homeResponse)) {
            Log.record(logTag, "当前捐步状态不可兑换: " + exchangeFlag(homeResponse))
            return false
        }

        val donateToken = extractDonateToken(homeResponse)
        if (donateToken.isNullOrBlank()) {
            Log.record(logTag, "捐步失败: donateToken 为空")
            return false
        }

        val activityId = extractActivityId(homeResponse)
        val exchangeResponse = JSONObject(AntSportsRpcCall.exchange(activityId, stepCount, donateToken))
        if (!isDonateSuccess(exchangeResponse)) {
            Log.record(logTag, "捐步兑换失败: " + exchangeResponse.optString("resultDesc", exchangeResponse.toString()))
            return false
        }

        val confirmedResponse =
            JSONObject(AntSportsRpcCall.donateWalkHome(stepCount))
        if (
            !ResChecker.checkRes(logTag, confirmedResponse) ||
            !isAlreadyDonated(confirmedResponse)
        ) {
            Log.record(logTag, "捐步动作已提交，服务端状态尚未确认")
            return false
        }

        currentUid?.let { Status.exchangeToday(it) }
        val resultModel = exchangeResponse.optJSONObject("donateExchangeResultModel")
        val donatedSteps = resultModel?.optInt("userCount", stepCount) ?: stepCount
        val amount = resultModel?.optJSONObject("userAmount")?.optDouble("amount", 0.0) ?: 0.0
        Log.other("庄园捐步❤️[$donatedSteps 步]#兑换$amount 元公益金")
        return true
    }

    private fun canExchange(response: JSONObject): Boolean {
        val flag = exchangeFlag(response)
        return flag.isBlank() || flag == "1"
    }

    fun isAlreadyDonated(response: JSONObject): Boolean {
        val model = response.optJSONObject("walkDonateHomeModel")
            ?.optJSONObject("walkUserInfoModel")
            ?: return false
        return model.optString("exchangeFlag") == "2" || model.has("userExchangedSteps")
    }

    private fun exchangeFlag(response: JSONObject): String {
        return response.optJSONObject("walkDonateHomeModel")
            ?.optJSONObject("walkUserInfoModel")
            ?.optString("exchangeFlag")
            ?: ""
    }
}
