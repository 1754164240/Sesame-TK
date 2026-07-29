package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONObject

data class OceanTaskState(
    val taskType: String,
    val status: String
)

object OceanTaskProtocol {
    private val rewardReady = setOf("FINISHED", "REWARD_READY", "CAN_RECEIVE")
    private val terminal = setOf("RECEIVED", "HAS_RECEIVED", "COMPLETED", "DONE", "SUCCESS")

    @JvmStatic
    fun statusOf(response: JSONObject, taskType: String): OceanTaskState? {
        val tasks = response.optJSONArray("antOceanTaskVOList") ?: return null
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val currentType = task.optString("taskType").trim()
            val status = task.optString("taskStatus").trim().uppercase()
            if (currentType == taskType && status.isNotEmpty()) {
                return OceanTaskState(currentType, status)
            }
        }
        return null
    }

    @JvmStatic
    fun isAdvanced(before: OceanTaskState, after: OceanTaskState?): Boolean {
        if (after == null) return before.status.uppercase() in rewardReady
        if (before.taskType != after.taskType) return false
        return rank(after.status) > rank(before.status)
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
