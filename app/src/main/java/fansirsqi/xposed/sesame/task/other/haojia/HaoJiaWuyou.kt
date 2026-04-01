package fansirsqi.xposed.sesame.task.other.haojia

import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.TaskBlacklist
import fansirsqi.xposed.sesame.util.TimeUtil
import org.json.JSONObject

object HaoJiaWuyou {
    private const val TAG = "好家无忧卡"

    fun start() {
        Log.record(TAG, "开始执行")
        try {
            doSignIn()
            doTasks()
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
        Log.record(TAG, "执行结束")
    }

    /**
     * 处理签到
     * 修正：增加对 SIG_DUPLICATED_SIGN_IN 的判断，避免误报失败
     */
    private fun doSignIn() {
        try {
            val resp = HaoJiaRpcCall.querySignIn()
            if (!ResChecker.checkRes(TAG, resp)) return

            val jo = JSONObject(resp)
            // 获取签到组件
            val component = jo.optJSONObject("components")
                ?.optJSONObject("independent_component_sign_in_00966139_independent_component_sign_in_recall")

            if (component == null) {
                Log.record(TAG, "签到查询失败: 未找到组件")
                return
            }

            // 检查组件状态，如果失败，判断是否是因为重复签到
            if (!component.optBoolean("isSuccess")) {
                val errorCode = component.optString("errorCode")
                val errorMsg = component.optString("errorMsg")
                if (errorCode == "SIG_DUPLICATED_SIGN_IN") {
                    Log.record(TAG, "今日已签到 (重复签到)")
                    return
                }
                Log.record(TAG, "签到组件异常: $errorMsg")
                return
            }

            val content = component.optJSONObject("content") ?: return
            val orderInfoList = content.optJSONArray("playSignInOrderInfoList") ?: return

            if (orderInfoList.length() > 0) {
                val orderInfo = orderInfoList.getJSONObject(0)
                val templateInfo = orderInfo.optJSONObject("playSignInTemplateInfo") ?: return
                val signCode = templateInfo.optString("code")

                // 双重检查：通过记录列表检查今日是否已签到
                val todayStr = TimeUtil.getDateStrNoSplite() // 格式 yyyyMMdd
                val recordList = orderInfo.optJSONArray("signInRecordInfoList")
                var signed = false
                if (recordList != null) {
                    for (i in 0 until recordList.length()) {
                        val record = recordList.getJSONObject(i)
                        if (record.optString("date") == todayStr) {
                            signed = true
                            break
                        }
                    }
                }

                if (signed) {
                    Log.record(TAG, "今日已签到")
                } else if (signCode.isNotEmpty()) {
                    Log.record(TAG, "开始签到...")
                    val signResp = HaoJiaRpcCall.doSignIn(signCode)

                    // 深度解析签到结果
                    val signJo = JSONObject(signResp)
                    val signComp = signJo.optJSONObject("components")
                        ?.optJSONObject("independent_component_sign_in_00966139_independent_component_sign_in")

                    val isRpcSuccess = ResChecker.checkRes(TAG, signResp) && signResp.contains("\"success\":true")
                    val isDuplicate = signComp?.optString("errorCode") == "SIG_DUPLICATED_SIGN_IN"

                    if (isRpcSuccess) {
                        // 即使RPC成功，也要看组件内部是否真的成功
                        if (signComp != null && signComp.optBoolean("isSuccess")) {
                            Log.record(TAG, "签到成功")
                        } else if (isDuplicate) {
                            Log.record(TAG, "签到成功 (重复签到)")
                        } else {
                            val msg = signComp?.optString("errorMsg") ?: "未知错误"
                            Log.record(TAG, "签到异常: $msg")
                        }
                    } else if (isDuplicate) {
                        Log.record(TAG, "签到成功 (重复签到)")
                    } else {
                        Log.record(TAG, "签到失败: $signResp")
                    }
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 处理任务
     * 修正：过滤 eventPush 类型任务，校验 rewardStatus 确保真实发奖
     */
    private fun doTasks() {
        try {
            val resp = HaoJiaRpcCall.queryTaskList()
            if (!ResChecker.checkRes(TAG, resp)) return

            val jo = JSONObject(resp)
            val component = jo.optJSONObject("components")
                ?.optJSONObject("independent_component_task_reward_00793835_independent_component_task_reward_query")

            val content = component?.optJSONObject("content") ?: return
            val taskList = content.optJSONArray("playTaskOrderInfoList") ?: return

            if (taskList.length() == 0) {
                Log.record(TAG, "暂无任务")
                return
            }

            for (i in 0 until taskList.length()) {
                val task = taskList.getJSONObject(i)
                val displayInfo = task.optJSONObject("displayInfo")
                val taskName = displayInfo?.optString("activityName") ?: "未知任务"
                val taskCode = task.optString("code")
                val taskStatus = task.optString("taskStatus") // init:未完成, finish:已完成
                val browseTime = displayInfo?.optInt("browseTime", 0) ?: 0
                val advanceType = task.optString("advanceType") // userPush:可做, eventPush:需后台事件

                // 1. 黑名单检查
                if (TaskBlacklist.isTaskInBlacklist(taskName)) {
                    Log.record(TAG, "跳过黑名单任务: $taskName")
                    continue
                }

                if (taskStatus == "init" && taskCode.isNotEmpty()) {
                    // 2. 关键词与类型过滤
                    // eventPush 通常需要开卡、买金、充值等真实操作，脚本无法模拟，跳过以净化日志
                    if (advanceType == "eventPush" ||
                        taskName.contains("开通") || taskName.contains("办理") ||
                        taskName.contains("咨询") || taskName.contains("黄金") ||
                        taskName.contains("流量") || taskName.contains("话费") ||
                        taskName.contains("理财") || taskName.contains("保险") ||
                        taskName.contains("购车")) {
                        // Log.record(TAG, "跳过任务(无法自动完成): $taskName")
                        continue
                    }

                    Log.record(TAG, "开始任务: $taskName")

                    // 模拟浏览
                    if (browseTime > 0) {
                        Log.record(TAG, "浏览任务: $taskName, 等待 ${browseTime}秒")
                        GlobalThreadPools.sleepCompat((browseTime * 1000).toLong())
                    } else {
                        GlobalThreadPools.sleepCompat(1000)
                    }

                    // 提交任务/领取奖励
                    val applyResp = HaoJiaRpcCall.applyTask(taskCode)
                    // 解析结果
                    val resJo = JSONObject(applyResp)

                    if (ResChecker.checkRes(TAG, applyResp)) {
                        val applyComp = resJo.optJSONObject("components")
                            ?.optJSONObject("independent_component_task_reward_00793835_independent_component_task_reward_apply")

                        if (applyComp != null && applyComp.optBoolean("isSuccess")) {
                            // 修正：深度检查 rewardStatus，防止虚假完成
                            val applyContent = applyComp.optJSONObject("content")
                            val claimedTask = applyContent?.optJSONObject("claimedTask")

                            // 不同的返回结构中，rewardStatus 可能在不同位置
                            val rewardStatus = claimedTask?.optString("rewardStatus")
                                ?: applyContent?.optString("rewardStatus")
                                ?: "unknown"

                            if (rewardStatus == "success" || rewardStatus == "REWARD_SUCCESS") {
                                Log.record(TAG, "任务完成: $taskName")
                            } else {
                                Log.record(TAG, "任务未发奖: $taskName, 状态: $rewardStatus")
                            }
                        } else {
                            // 获取具体的错误信息
                            val errorMsg = applyComp?.optString("resultView")
                                ?: applyComp?.optString("errorMsg")
                                ?: resJo.optString("resultView", "未知错误")

                            Log.record(TAG, "任务失败: $taskName, 原因: $errorMsg")

                            // 3. 失败自动加入黑名单
                            TaskBlacklist.autoAddToBlacklist(taskName, taskName, errorMsg)
                        }
                    } else {
                        // RPC 层面失败
                        val errorMsg = resJo.optString("resultView", applyResp)
                        Log.record(TAG, "任务RPC失败: $taskName, 原因: $errorMsg")
                        TaskBlacklist.autoAddToBlacklist(taskName, taskName, errorMsg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }
}