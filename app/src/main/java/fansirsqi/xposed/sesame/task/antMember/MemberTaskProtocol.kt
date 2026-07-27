package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

data class MemberAdTask(
    val adId: String,
    val adBizId: String,
    val title: String,
    val awardNum: Int,
    val browseSeconds: Int,
    val extMap: JSONObject
) {
    val waitMillis: Long
        get() = (max(browseSeconds, 15) + 1) * 1_000L
}

data class MemberBrowseTask(
    val configId: String,
    val processId: String,
    val title: String,
    val status: String,
    val browseSeconds: Int,
    val bizType: String,
    val bizSubType: String,
    val bizParam: String
) {
    val needsApply: Boolean
        get() = status.isEmpty() || status == "INIT"

    val waitMillis: Long
        get() = (max(browseSeconds, 15) + 1) * 1_000L
}

data class MemberTaskProgress(
    val currentCount: Int,
    val targetCount: Int,
    val totalAwardPoint: Int,
    val receivedAwardPoint: Int,
    val status: String
) {
    val completed: Boolean
        get() = status == "COMPLETE" || targetCount > 0 && currentCount >= targetCount

    val remainingCount: Int
        get() = if (targetCount > 0) max(0, targetCount - currentCount) else Int.MAX_VALUE
}

data class MemberTreasureBoxTask(
    val bizNo: String,
    val taskType: String,
    val endTime: Long,
    val awardNum: Int
)

data class MemberGameVisitContext(
    val source: String,
    val tab: String,
    val sceneId: String,
    val taskId: String
) {
    val channelTaskPassThrough: String
        get() = JSONObject()
            .put("sceneId", sceneId)
            .put("taskId", taskId)
            .toString()
}

object MemberTaskProtocol {
    const val TASK_SPACE_CODE = "ant_member_xlight_task"
    const val SOURCE = "ch_appcenter__chsub_9patch"

    private val terminalStatuses = setOf("AWARDED", "COMPLETE", "EXPIRED", "FAILED")

    @JvmStatic
    fun buildSignPageTaskListArgs(): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("source", "antmember")
                .put("spaceCode", TASK_SPACE_CODE)
                .put("taskTopConfigId", "")
                .put("switchNormal", true)
                .put("pageNo", 1)
                .put("pageSize", 8)
                .put("sourcePassMap", buildSourcePassMap())
        )
    }

    @JvmStatic
    fun parseAdTasks(response: JSONObject): List<MemberAdTask> {
        val resultData = response.optJSONObject("resultData") ?: return emptyList()
        val candidates = collectTaskObjects(resultData)
        val tasks = LinkedHashMap<String, MemberAdTask>()

        for (task in candidates) {
            if (!task.optBoolean("adTask") && !task.optBoolean("adVideoTask")) {
                continue
            }
            if (task.optString("status") in terminalStatuses) {
                continue
            }

            val extMap = task.optJSONObject("lightsAdExtMap") ?: continue
            val adId = extMap.optString("adId")
            val adBizId = extMap.optString("bizId")
            if (adId.isEmpty() || adBizId.isEmpty()) {
                continue
            }

            val config = task.optJSONObject("simpleTaskConfig") ?: JSONObject()
            val title = config.optString("title")
                .ifEmpty { extMap.optString("title", "会员广告任务") }
            if (title.replace(Regex("\\s+"), "").startsWith("玩游戏")) {
                continue
            }
            val taskStage = config.optInt("taskStage")
            val stage = config.optJSONArray("stageVOList")
                ?.optJSONObject(taskStage)
                ?: JSONObject()
            val awardNum = stage.optJSONObject("awardParam")
                ?.optInt("awardParamPoint")
                ?: 0
            val copiedExtMap = JSONObject(extMap.toString()).apply {
                if (!has("adId")) put("adId", adId)
                if (!has("bizId")) put("bizId", adBizId)
                if (!has("spaceCode")) put("spaceCode", TASK_SPACE_CODE)
            }

            tasks.putIfAbsent(
                adBizId,
                MemberAdTask(
                    adId = adId,
                    adBizId = adBizId,
                    title = title,
                    awardNum = awardNum,
                    browseSeconds = config.optInt("browseSeconds"),
                    extMap = copiedExtMap
                )
            )
        }
        return tasks.values.toList()
    }

    @JvmStatic
    fun parseBrowseTasks(response: JSONObject): List<MemberBrowseTask> {
        val resultData = response.optJSONObject("resultData") ?: return emptyList()
        val candidates = collectBrowseTaskObjects(resultData)
        val tasks = LinkedHashMap<String, MemberBrowseTask>()

        for (task in candidates) {
            if (task.optBoolean("adTask") || task.optBoolean("adVideoTask")) {
                continue
            }
            val status = task.optString("status")
            if (status in terminalStatuses) {
                continue
            }

            val targetBusiness = task.optJSONArray("targetBusiness")
                ?.optString(0)
                .orEmpty()
            val businessParts = targetBusiness.split("#", limit = 3)
            if (businessParts.size < 3 || businessParts[0] != "BROWSE") {
                continue
            }

            val config = task.optJSONObject("simpleTaskConfig") ?: continue
            val configId = config.optString("configId")
            if (configId.isEmpty()) {
                continue
            }
            val bizSubType = businessParts[1].takeIf { it == "15S" } ?: "UNLIMITED"

            tasks.putIfAbsent(
                configId,
                MemberBrowseTask(
                    configId = configId,
                    processId = task.optString("processId"),
                    title = config.optString("title", "会员浏览任务"),
                    status = status,
                    browseSeconds = config.optInt("browseSeconds"),
                    bizType = businessParts[0],
                    bizSubType = bizSubType,
                    bizParam = businessParts[2]
                )
            )
        }
        return tasks.values.toList()
    }

    @JvmStatic
    fun buildApplyAdTaskArgs(task: MemberAdTask): JSONArray {
        val request = JSONObject()
            .put("adBizId", task.adBizId)
            .put("adId", task.adId)
            .put("awardNum", task.awardNum)
            .put("bizNo", task.adBizId)
            .put("extMap", JSONObject(task.extMap.toString()))
            .put("scene", "TASK_WALL")
            .put("sourcePassMap", buildSourcePassMap())
            .put("spaceCode", TASK_SPACE_CODE)
            .put("subScene", "")
            .put("userId", "")
        return JSONArray().put(request)
    }

    @JvmStatic
    fun buildApplyTaskArgs(task: MemberBrowseTask): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("taskConfigId", task.configId)
                .put("sourcePassMap", buildSourcePassMap())
        )
    }

    @JvmStatic
    fun buildExecuteTaskArgs(task: MemberBrowseTask, outBizNo: Long): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("bizType", task.bizType)
                .put("bizSubType", task.bizSubType)
                .put("bizParam", task.bizParam)
                .put("outBizNo", outBizNo.toString())
                .put("sourcePassMap", buildSourcePassMap())
        )
    }

    @JvmStatic
    fun buildProgressQueryArgs(): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("relatedChannel", "MEMBERPOINT")
                .put("sourcePassMap", buildSourcePassMap())
        )
    }

    @JvmStatic
    fun buildTreasureBoxQueryArgs(): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("extMap", JSONObject())
                .put("sourcePassMap", buildSourcePassMap())
        )
    }

    @JvmStatic
    fun parseTreasureBoxTask(
        response: JSONObject,
        taskInfoKey: String = "currentTaskInfo"
    ): MemberTreasureBoxTask? {
        val taskInfo = response.optJSONObject(taskInfoKey) ?: return null
        if (taskInfo.optString("taskStatus") in terminalStatuses + "SUCCESS") return null
        val bizNo = taskInfo.optString("bizNo").ifEmpty { response.optString("bizNo") }
        val taskType = response.optString("taskType")
        if (bizNo.isEmpty() || taskType.isEmpty()) return null
        return MemberTreasureBoxTask(
            bizNo = bizNo,
            taskType = taskType,
            endTime = taskInfo.optLong("endDt"),
            awardNum = taskInfo.optInt("awardNum")
        )
    }

    @JvmStatic
    fun buildTriggerTreasureBoxArgs(task: MemberTreasureBoxTask): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("bizNo", task.bizNo)
                .put("extMap", JSONObject())
                .put("sourcePassMap", buildSourcePassMap())
                .put("taskType", task.taskType)
        )
    }

    @JvmStatic
    fun buildGameEntranceQueryArgs(): JSONArray {
        return JSONArray().put(
            JSONObject().put("sourcePassMap", buildSourcePassMap())
        )
    }

    @JvmStatic
    fun parseGameVisitContext(response: JSONObject): MemberGameVisitContext? {
        val outerParams = parseUrlQuery(response.optString("actionUrl"))
        val innerUrl = outerParams["url"] ?: return null
        val innerParams = parseUrlQuery(innerUrl)
        val source = innerParams["chInfo"].orEmpty()
            .ifEmpty { outerParams["chInfo"].orEmpty() }
        val tab = innerParams["tab"].orEmpty()
        var passThrough = innerParams["channelTaskPassThrough"].orEmpty()
        repeat(4) {
            val decoded = decodeUrlComponent(passThrough)
            if (decoded == passThrough) return@repeat
            passThrough = decoded
        }
        if (passThrough.startsWith("\"")) {
            passThrough = runCatching {
                JSONArray("[$passThrough]").getString(0)
            }.getOrDefault(passThrough)
        }
        val taskContext = runCatching { JSONObject(passThrough) }.getOrNull() ?: return null
        val sceneId = taskContext.optString("sceneId")
        val taskId = taskContext.optString("taskId")
        if (source.isEmpty() || tab.isEmpty() || sceneId.isEmpty() || taskId.isEmpty()) return null
        return MemberGameVisitContext(source, tab, sceneId, taskId)
    }

    @JvmStatic
    fun buildGameHomeArgs(context: MemberGameVisitContext): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("deviceLevel", "high")
                .put("mytabChInfo", "")
                .put("source", context.source)
                .put("sourceTab", context.tab)
                .put("unityDeviceLevel", "high")
                .put("userFatigueInfo", JSONObject().put("FEEDS_GUIDE_STEP", 0))
        )
    }

    @JvmStatic
    fun buildGameModuleArgs(context: MemberGameVisitContext): JSONArray {
        return JSONArray().put(buildGameActivityArgs(context))
    }

    @JvmStatic
    fun buildWalkMainArgs(context: MemberGameVisitContext): JSONArray {
        return JSONArray().put(
            buildGameActivityArgs(context)
                .put("cumulativeRechargePopupShown", false)
                .put("fallbackTaskPopupShownToday", false)
                .put("firstPayPopupShown", false)
        )
    }

    @JvmStatic
    fun buildPointRecordQueryArgs(): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("flowinSinceId", 0)
                .put("init", true)
                .put("pageSize", 20)
                .put("sourcePassMap", buildSourcePassMap())
                .put("type", 1)
        )
    }

    @JvmStatic
    fun hasPointRecord(
        response: JSONObject,
        date: String,
        memo: String,
        point: String
    ): Boolean {
        val summaries = response.optJSONArray("summaries") ?: return false
        for (summaryIndex in 0 until summaries.length()) {
            val details = summaries.optJSONObject(summaryIndex)
                ?.optJSONArray("details")
                ?: continue
            for (detailIndex in 0 until details.length()) {
                val detail = details.optJSONObject(detailIndex) ?: continue
                if (detail.optString("date") == date &&
                    detail.optString("memo") == memo &&
                    detail.optString("point") == point
                ) {
                    return true
                }
            }
        }
        return false
    }

    @JvmStatic
    fun parseProgress(response: JSONObject): MemberTaskProgress {
        val processList = response.optJSONArray("availableTaskProcessList") ?: JSONArray()
        var selected: JSONObject? = null
        for (index in 0 until processList.length()) {
            val process = processList.optJSONObject(index) ?: continue
            if (selected == null) {
                selected = process
            }
            if (process.optJSONObject("taskConfig")?.optString("taskStyle") == "WELFARE_TASK") {
                selected = process
                break
            }
        }

        val process = selected ?: return MemberTaskProgress(0, 0, 0, 0, "")
        var totalAwardPoint = 0
        var receivedAwardPoint = 0
        val stages = process.optJSONArray("stageProcessList") ?: JSONArray()
        for (index in 0 until stages.length()) {
            val stage = stages.optJSONObject(index) ?: continue
            val awardPoint = stage.optInt("awardPoint")
            totalAwardPoint += awardPoint
            if (stage.optString("stageStatus") == "COMPLETE") {
                receivedAwardPoint += awardPoint
            }
        }

        return MemberTaskProgress(
            currentCount = process.optInt("currentCount"),
            targetCount = process.optInt("targetCount"),
            totalAwardPoint = totalAwardPoint,
            receivedAwardPoint = receivedAwardPoint,
            status = process.optString("status")
        )
    }

    @JvmStatic
    fun isFinishSuccess(response: JSONObject): Boolean {
        return response.optBoolean("success") ||
            response.optBoolean("isSuccess") ||
            response.optString("resultCode").equals("SUCCESS", ignoreCase = true) ||
            response.optString("errCode") == "0"
    }

    @JvmStatic
    fun isExpectedTaskRejection(response: JSONObject): Boolean {
        val message = response.optString(
            "errMsg",
            response.optString("resultDesc", response.optString("resultMsg"))
        )
        return message.contains("任务") &&
            message.contains("校验失败") &&
            (message.contains("全性") || message.contains("安全性"))
    }

    private fun collectTaskObjects(resultData: JSONObject): List<JSONObject> {
        val tasks = ArrayList<JSONObject>()
        appendObjects(resultData.optJSONArray("adTaskList"), tasks)
        tasks.addAll(collectBrowseTaskObjects(resultData))
        return tasks
    }

    private fun collectBrowseTaskObjects(resultData: JSONObject): List<JSONObject> {
        val tasks = ArrayList<JSONObject>()
        val categories = resultData.optJSONArray("categoryTaskList") ?: JSONArray()
        for (index in 0 until categories.length()) {
            val category = categories.optJSONObject(index) ?: continue
            appendObjects(category.optJSONArray("taskProcessVOList"), tasks)
        }
        appendObjects(resultData.optJSONArray("pureTaskList"), tasks)
        return tasks
    }

    private fun appendObjects(source: JSONArray?, destination: MutableList<JSONObject>) {
        if (source == null) return
        for (index in 0 until source.length()) {
            source.optJSONObject(index)?.let(destination::add)
        }
    }

    private fun buildGameActivityArgs(context: MemberGameVisitContext): JSONObject {
        return JSONObject()
            .put("channelTaskPassThrough", context.channelTaskPassThrough)
            .put("deviceLevel", "high")
            .put("source", context.source)
            .put("unityDeviceLevel", "high")
    }

    private fun parseUrlQuery(url: String): Map<String, String> {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator < 0) return@mapNotNull null
                decodeUrlComponent(part.substring(0, separator)) to
                    decodeUrlComponent(part.substring(separator + 1))
            }
            .toMap()
    }

    private fun decodeUrlComponent(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun buildSourcePassMap(): JSONObject {
        return JSONObject()
            .put("innerSource", "")
            .put("source", SOURCE)
            .put("unid", "")
    }
}
