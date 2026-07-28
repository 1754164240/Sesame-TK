package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONArray
import org.json.JSONObject

enum class EnergyPvpDecision {
    CLAIM,
    RETRY_LATER,
    DONE
}

object EnergyPvpChallengePolicy {
    private val activeStatuses = setOf("MATCHING", "PROGRESSING", "SETTLING")
    private val terminalStatuses = setOf(
        "FINISHED",
        "SETTLED",
        "COMPLETED",
        "DONE",
        "RECEIVED"
    )
    private val claimableRewardStatuses = setOf(
        "UNRECEIVED",
        "WAIT_RECEIVE",
        "WAIT_TO_RECEIVE"
    )
    private val terminalRewardStatuses = setOf(
        "RECEIVED",
        "ISSUED",
        "CLAIMED",
        "DONE"
    )

    fun decide(entry: JSONObject?, home: JSONObject?): EnergyPvpDecision {
        val entryPayload = entry?.takeIf(::isResponseSuccess)?.let(::payload)
        val homePayload = home?.takeIf(::isResponseSuccess)?.let(::payload)
        val pvpInfo = entryPayload?.optJSONObject("combineHandlerVOMap")
            ?.optJSONObject("energyPvpInfo")
            ?: entryPayload?.optJSONObject("energyPvpInfo")
        val records = listOf(
            homePayload?.optJSONObject("currentEnergyPvpBattleRecord"),
            homePayload?.optJSONObject("previousEnergyPvpBattleRecord")
        )

        if (pvpInfo?.optBoolean("hasReward") == true) {
            return EnergyPvpDecision.CLAIM
        }
        if ((homePayload?.optInt("waitToReceiveRecordCount") ?: 0) > 0 ||
            (homePayload?.optInt("waitToReceiveRewardCount") ?: 0) > 0
        ) {
            return EnergyPvpDecision.CLAIM
        }
        if (records.any(::hasClaimableReward)) {
            return EnergyPvpDecision.CLAIM
        }

        val entryStatus = pvpInfo?.optString("battleStatus")?.uppercase().orEmpty()
        val recordStatuses = records.mapNotNull { record ->
            record?.optString("battleStatus")?.uppercase()
        }
        if (entryStatus in activeStatuses || recordStatuses.any { it in activeStatuses }) {
            return EnergyPvpDecision.RETRY_LATER
        }
        if (hasUnknownRewardStatus(records)) {
            return EnergyPvpDecision.RETRY_LATER
        }

        if (pvpInfo == null ||
            !pvpInfo.has("hasEntry") ||
            !pvpInfo.has("hasReward") ||
            homePayload == null ||
            !homePayload.has("waitToReceiveRecordCount") ||
            !homePayload.has("waitToReceiveRewardCount")
        ) {
            return EnergyPvpDecision.RETRY_LATER
        }
        val hasEntry = pvpInfo.optBoolean("hasEntry")
        if (hasEntry && entryStatus !in terminalStatuses) {
            return EnergyPvpDecision.RETRY_LATER
        }
        if (recordStatuses.any { it.isBlank() || it !in terminalStatuses }) {
            return EnergyPvpDecision.RETRY_LATER
        }
        return EnergyPvpDecision.DONE
    }

    fun isTerminalClaimResult(code: String, message: String): Boolean {
        val text = "$code $message"
        return listOf(
            "已领取",
            "已发放",
            "无可领取",
            "没有可领取",
            "重复领取"
        ).any(text::contains)
    }

    fun summarizeRewards(rewards: JSONArray?): String {
        if (rewards == null || rewards.length() == 0) {
            return "无"
        }
        val parts = buildList {
            for (index in 0 until rewards.length()) {
                val reward = rewards.optJSONObject(index) ?: continue
                val name = reward.optString("rewardName")
                    .ifBlank { reward.optString("rewardId") }
                    .ifBlank { "未知奖励" }
                val energy = reward.optInt("energy")
                val type = reward.optString("rewardType")
                add(
                    when {
                        energy > 0 -> "$name(${energy}g)"
                        type.isNotBlank() -> "$name($type)"
                        else -> name
                    }
                )
            }
        }
        return parts.ifEmpty { listOf("无") }.joinToString("、")
    }

    private fun hasClaimableReward(record: JSONObject?): Boolean {
        val rewards = record?.optJSONArray("rewardDetailList") ?: return false
        for (index in 0 until rewards.length()) {
            val status = rewards.optJSONObject(index)
                ?.optString("rewardStatus")
                ?.uppercase()
                ?: continue
            if (status in claimableRewardStatuses ||
                status.contains("WAIT") && status.contains("RECEIV")
            ) {
                return true
            }
        }
        return false
    }

    private fun hasUnknownRewardStatus(records: List<JSONObject?>): Boolean {
        for (record in records) {
            val rewards = record?.optJSONArray("rewardDetailList") ?: continue
            for (index in 0 until rewards.length()) {
                val reward = rewards.optJSONObject(index) ?: return true
                val status = reward.optString("rewardStatus").uppercase()
                if (status !in claimableRewardStatuses &&
                    status !in terminalRewardStatuses
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun isResponseSuccess(response: JSONObject): Boolean {
        return response.optBoolean("success") ||
            response.optBoolean("isSuccess") ||
            response.optString("resultCode").let {
                it == "100" || it.equals("SUCCESS", ignoreCase = true)
            }
    }

    private fun payload(response: JSONObject): JSONObject {
        return response.optJSONObject("data")
            ?: response.optJSONObject("result")
            ?: response
    }
}
