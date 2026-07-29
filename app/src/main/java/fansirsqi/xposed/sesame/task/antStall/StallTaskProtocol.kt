package fansirsqi.xposed.sesame.task.antStall

import org.json.JSONObject

data class StallTaskState(
    val taskType: String,
    val status: String
)

object StallTaskProtocol {
    private const val XLIGHT_LIMIT_CODE = "217"
    private const val XLIGHT_LIMIT_SSP_CODE = "61002"
    private val rewardReady = setOf("FINISHED", "REWARD_READY", "CAN_RECEIVE")
    private val terminal = setOf("RECEIVED", "HAS_RECEIVED", "COMPLETED", "DONE", "SUCCESS")

    fun isXlightTrafficLimited(response: JSONObject): Boolean {
        return sequenceOf(response, response.optJSONObject("resData"))
            .filterNotNull()
            .any {
                it.optString("retCode") == XLIGHT_LIMIT_CODE &&
                    it.optString("sspErrorCode") == XLIGHT_LIMIT_SSP_CODE
            }
    }

    fun statusOf(response: JSONObject, taskType: String): StallTaskState? {
        val tasks = response.optJSONArray("taskModels") ?: return null
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val currentType = task.optString("taskType").trim()
            val status = task.optString("taskStatus").trim().uppercase()
            if (currentType == taskType && status.isNotEmpty()) {
                return StallTaskState(currentType, status)
            }
        }
        return null
    }

    fun isAdvanced(before: StallTaskState, after: StallTaskState?): Boolean {
        if (after == null) return before.status.uppercase() in rewardReady
        if (before.taskType != after.taskType) return false
        return rank(after.status) > rank(before.status)
    }

    fun isRewardReady(state: StallTaskState?): Boolean = state?.status?.uppercase() in rewardReady

    fun isSignConfirmed(response: JSONObject): Boolean {
        val signListModel = response.optJSONObject("signListModel") ?: return false
        return signListModel.has("currentKeySigned") && signListModel.optBoolean("currentKeySigned")
    }

    private fun rank(status: String): Int {
        val normalized = status.uppercase()
        return when {
            normalized in terminal -> 2
            normalized in rewardReady -> 1
            normalized == "TODO" -> 0
            else -> -1
        }
    }
}
