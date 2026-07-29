package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject

data class MemberTreasureBoxRunResult(
    val confirmed: Boolean,
    val retryable: Boolean,
    val awardNum: Int,
    val nextTask: MemberTreasureBoxTask?
)

class MemberTreasureBoxWorkflow(
    private val triggerTask: (MemberTreasureBoxTask) -> String,
    private val queryTask: () -> String
) {
    private val terminalStatuses = setOf(
        "SUCCESS",
        "AWARDED",
        "COMPLETE",
        "COMPLETED",
        "RECEIVED",
        "DONE",
        "FINISHED"
    )

    fun triggerAndVerify(task: MemberTreasureBoxTask): MemberTreasureBoxRunResult {
        val triggerResponse = runCatching { JSONObject(triggerTask(task)) }
            .getOrNull()
            ?: return retryableResult()
        if (!MemberTaskProtocol.isFinishSuccess(triggerResponse)) {
            return retryableResult()
        }

        val queryResponse = runCatching { JSONObject(queryTask()) }
            .getOrNull()
            ?: return retryableResult()
        if (!MemberTaskProtocol.isFinishSuccess(queryResponse)) {
            return retryableResult()
        }
        if (!isConfirmed(task, queryResponse)) {
            return retryableResult()
        }

        val awardNum = triggerResponse.optJSONObject("currentTaskInfo")
            ?.optInt("awardNum", task.awardNum)
            ?: task.awardNum
        return MemberTreasureBoxRunResult(
            confirmed = true,
            retryable = false,
            awardNum = awardNum,
            nextTask = MemberTaskProtocol.parseTreasureBoxTask(queryResponse)
        )
    }

    private fun isConfirmed(
        task: MemberTreasureBoxTask,
        response: JSONObject
    ): Boolean {
        if (response.optBoolean("allTaskCompleted")) {
            return true
        }
        val current = response.optJSONObject("currentTaskInfo") ?: return false
        val currentBizNo = current.optString("bizNo")
        if (currentBizNo.isNotBlank() && currentBizNo != task.bizNo) {
            return true
        }
        return currentBizNo == task.bizNo &&
            current.optString("taskStatus").uppercase() in terminalStatuses
    }

    private fun retryableResult(): MemberTreasureBoxRunResult {
        return MemberTreasureBoxRunResult(
            confirmed = false,
            retryable = true,
            awardNum = 0,
            nextTask = null
        )
    }
}
