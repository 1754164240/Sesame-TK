package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject

enum class ChouChouLeTaskRoute {
    BROWSE,
    FARM
}

object ChouChouLeTaskPolicy {
    private val browseTaskIds = setOf(
        "SHANGYEHUA_DAILY_DRAW_TIMES",
        "IP_SHANGYEHUA_TASK"
    )

    fun route(taskId: String): ChouChouLeTaskRoute {
        return if (taskId in browseTaskIds) {
            ChouChouLeTaskRoute.BROWSE
        } else {
            ChouChouLeTaskRoute.FARM
        }
    }
}

class ChouChouLeBrowseWorkflow(
    private val queryTask: () -> String,
    private val wait: (Long) -> Unit,
    private val finishTask: (String, String) -> String
) {
    fun execute(drawType: String, taskId: String): String {
        val root = runCatching {
            JSONObject(queryTask())
        }.getOrNull() ?: return ""
        if (!root.optBoolean("success", false)) {
            return ""
        }
        val durationSeconds = root.optJSONObject("resultData")
            ?.optDouble("duration", Double.NaN)
            ?: return ""
        if (!durationSeconds.isFinite() || durationSeconds <= 0.0) {
            return ""
        }
        wait((durationSeconds * 1_000).toLong())
        val sceneCode = if (drawType == "dailyDraw") {
            "ANTFARM_DAILY_DRAW_TASK"
        } else {
            "ANTFARM_IP_DRAW_TASK"
        }
        return finishTask(taskId, sceneCode)
    }
}

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
                executeTask = { drawType, task ->
                    executeTask(drawType, task)
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

    private fun executeTask(
        drawType: String,
        task: ChouChouLeRewardTask
    ): Boolean {
        return try {
            val response = when (
                ChouChouLeTaskPolicy.route(task.taskId)
            ) {
                ChouChouLeTaskRoute.BROWSE ->
                    executeBrowseTask(drawType, task.taskId)
                ChouChouLeTaskRoute.FARM ->
                    AntFarmRpcCall.chouchouleDoFarmTask(
                        drawType,
                        task.taskId
                    )
            }
            ChouChouLeRewardPolicy.isActionAccepted(response)
        } catch (t: Throwable) {
            Log.printStackTrace("执行抽抽乐任务 err:", t)
            false
        }
    }

    private fun executeBrowseTask(
        drawType: String,
        taskId: String
    ): String {
        val workflow = ChouChouLeBrowseWorkflow(
            queryTask = {
                AntFarmRpcCall.chouchouleQueryBrowseTask()
            },
            wait = GlobalThreadPools::sleepCompat,
            finishTask = { currentTaskId, sceneCode ->
                val outBizNo = currentTaskId + "_" +
                    System.currentTimeMillis() + "_" +
                    Integer.toHexString(
                        (Math.random() * 0xFFFFFF).toInt()
                    )
                AntFarmRpcCall.finishTask(
                    currentTaskId,
                    sceneCode,
                    outBizNo
                )
            }
        )
        return workflow.execute(drawType, taskId)
    }
}
