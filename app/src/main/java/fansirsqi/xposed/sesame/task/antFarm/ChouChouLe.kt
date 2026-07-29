package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject

class ChouChouLe {

    companion object {
        private val TAG = ChouChouLe::class.java.simpleName
    }

    fun chouchoule(): Boolean {
        return try {
            val response = AntFarmRpcCall.queryLoveCabin(
                UserMap.currentUid
            )
            val root = JSONObject(response)
            if (!ResChecker.checkRes(TAG, root)) {
                return false
            }
            val drawMachineInfo = root.optJSONObject(
                "drawMachineInfo"
            ) ?: return false
            val drawTypes = buildList {
                if (
                    drawMachineInfo.has(
                        "ipDrawMachineActivityId"
                    )
                ) {
                    add("ipDraw")
                }
                if (
                    drawMachineInfo.has(
                        "dailyDrawMachineActivityId"
                    )
                ) {
                    add("dailyDraw")
                }
            }
            val workflow = ChouChouLeRewardWorkflow(
                queryTasks = { drawType ->
                    AntFarmRpcCall.chouchouleListFarmTask(drawType)
                },
                receiveReward = { drawType, taskId ->
                    AntFarmRpcCall.chouchouleReceiveFarmTaskAward(
                        drawType,
                        taskId
                    )
                }
            )
            var allFinished = true
            for (drawType in drawTypes) {
                val result = workflow.process(drawType)
                if (!result.recognized) {
                    Log.record(
                        TAG,
                        "抽抽乐任务结构未知[$drawType]，等待后续重试"
                    )
                }
                if (result.unsupportedPendingCount > 0) {
                    Log.record(
                        TAG,
                        "抽抽乐跳过广告、游戏及其他待执行任务" +
                            "[$drawType]#${result.unsupportedPendingCount}"
                    )
                }
                for (execution in result.executions) {
                    when (execution.outcome) {
                        ChouChouLeRewardOutcome.CONFIRMED ->
                            Log.farm(
                                "抽抽乐🎁[任务奖励已确认: " +
                                    "${execution.title}]"
                            )
                        ChouChouLeRewardOutcome.RETRY ->
                            Log.record(
                                TAG,
                                "抽抽乐奖励状态未推进" +
                                    "[${execution.title}]"
                            )
                    }
                }
                allFinished = allFinished && result.finished
            }
            allFinished
        } catch (t: Throwable) {
            Log.printStackTrace("chouchoule err:", t)
            false
        }
    }
}
