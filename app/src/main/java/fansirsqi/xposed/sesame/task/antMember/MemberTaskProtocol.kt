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
    val extMap: JSONObject,
    val configId: String = ""
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

data class MemberTaskState(
    val stableKey: String,
    val processId: String,
    val configId: String,
    val adBizId: String,
    val title: String,
    val status: String,
    val current: Int?,
    val limit: Int?,
    val targetBusiness: String,
    val source: JSONObject = JSONObject()
) {
    fun toSafetyCandidate(): MemberTaskCandidate {
        return MemberTaskCandidate(
            configId = configId,
            title = title,
            targetBusiness = targetBusiness,
            adBizId = adBizId
        )
    }
}

data class MemberTaskSnapshot(
    val recognized: Boolean,
    val tasks: List<MemberTaskState>
)

enum class MemberTaskVerification {
    CONFIRMED,
    PARTIAL,
    UNCONFIRMED
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
    private val confirmedStatuses = setOf(
        "AWARDED",
        "COMPLETE",
        "COMPLETED",
        "RECEIVED",
        "SUCCESS",
        "DONE",
        "FINISHED"
    )

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
    fun buildAllStatusTaskListArgs(): JSONArray {
        return JSONArray().put(
            JSONObject().put("source", "signInAd")
        )
    }

    @JvmStatic
    fun buildSingleTaskDetailArgs(taskProcessId: String): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("sourcePassMap", buildSourcePassMap())
                .put("taskProcessId", taskProcessId)
        )
    }

    @JvmStatic
    fun buildSingleAdTaskDetailArgs(
        configId: String,
        adBizId: String
    ): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("adBizId", adBizId)
                .put("adTaskFlag", true)
                .put("alipayGrowthFlag", false)
                .put("configId", configId)
                .put("sourcePassMap", buildSourcePassMap())
                .put("taskProcessId", "")
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
                    extMap = copiedExtMap,
                    configId = config.optString("configId")
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
    fun parseTaskSnapshot(
        vararg responses: JSONObject
    ): MemberTaskSnapshot {
        var recognized = false
        val taskMap = LinkedHashMap<String, MemberTaskState>()
        for (response in responses) {
            val containers = taskResponseContainers(response)
            if (!hasSuccessfulResponseMarker(containers)) {
                continue
            }
            val collected = mutableListOf<JSONObject>()
            for (container in containers) {
                for (key in listOf(
                    "adTaskList",
                    "pureTaskList",
                    "taskProcessVOList",
                    "availableTaskProcessList"
                )) {
                    if (!container.has(key)) {
                        continue
                    }
                    recognized = true
                    appendObjects(container.optJSONArray(key), collected)
                }
                if (container.has("categoryTaskList")) {
                    recognized = true
                    val categories = container.optJSONArray("categoryTaskList")
                    if (categories != null) {
                        for (index in 0 until categories.length()) {
                            val category = categories.optJSONObject(index)
                                ?: continue
                            appendObjects(
                                category.optJSONArray("taskProcessVOList"),
                                collected
                            )
                        }
                    }
                }
            }
            for (taskObject in collected) {
                val state = parseTaskState(taskObject) ?: continue
                taskMap[state.stableKey] = state
            }
        }
        return MemberTaskSnapshot(
            recognized = recognized,
            tasks = taskMap.values.toList()
        )
    }

    @JvmStatic
    fun verifyTaskDetail(
        before: MemberTaskState,
        response: JSONObject
    ): MemberTaskVerification {
        val detail = parseTaskDetail(response)
            ?: return MemberTaskVerification.UNCONFIRMED
        val status = detail.status.uppercase()
        if (status in confirmedStatuses) {
            return MemberTaskVerification.CONFIRMED
        }

        val current = detail.current
        val limit = detail.limit
        val previousCurrent = before.current
        return if (
            current != null &&
            previousCurrent != null &&
            current > previousCurrent &&
            limit != null &&
            current < limit
        ) {
            MemberTaskVerification.PARTIAL
        } else {
            MemberTaskVerification.UNCONFIRMED
        }
    }

    @JvmStatic
    fun parseTaskDetail(response: JSONObject): MemberTaskState? {
        val containers = taskResponseContainers(response)
        if (!hasSuccessfulResponseMarker(containers)) {
            return null
        }
        val taskObject = containers.asSequence()
            .mapNotNull { it.optJSONObject("taskProcessVO") }
            .firstOrNull()
            ?: return null
        return parseTaskState(taskObject)
    }

    @JvmStatic
    fun isApplyConfirmed(
        before: MemberTaskState,
        after: MemberTaskState
    ): Boolean {
        val sameTask = when {
            before.processId.isNotBlank() && after.processId.isNotBlank() ->
                before.processId == after.processId
            before.configId.isNotBlank() && after.configId.isNotBlank() ->
                before.configId == after.configId
            before.adBizId.isNotBlank() && after.adBizId.isNotBlank() ->
                before.adBizId == after.adBizId
            else -> false
        }
        if (!sameTask) return false

        val initialStatuses = setOf("", "INIT", "TO_APPLY", "NOT_STARTED")
        val beforeStatus = before.status.uppercase()
        val afterStatus = after.status.uppercase()
        return afterStatus in confirmedStatuses ||
            beforeStatus in initialStatuses && afterStatus !in initialStatuses
    }

    @JvmStatic
    fun buildApplyAdTaskArgs(task: MemberAdTask): JSONArray {
        return buildApplyAdTaskArgs(
            adBizId = task.adBizId,
            adId = task.adId,
            awardNum = task.awardNum,
            extMap = task.extMap
        )
    }

    @JvmStatic
    fun buildApplyAdTaskArgs(task: MemberTaskState): JSONArray {
        val extMap = task.source.optJSONObject("lightsAdExtMap")
            ?: JSONObject()
        val config = task.source.optJSONObject("simpleTaskConfig")
            ?: task.source.optJSONObject("taskConfig")
            ?: JSONObject()
        val taskStage = config.optInt("taskStage")
        val stage = config.optJSONArray("stageVOList")
            ?.optJSONObject(taskStage)
            ?: JSONObject()
        val awardNum = stage.optJSONObject("awardParam")
            ?.optInt("awardParamPoint")
            ?: 0
        return buildApplyAdTaskArgs(
            adBizId = task.adBizId,
            adId = extMap.optString("adId"),
            awardNum = awardNum,
            extMap = extMap
        )
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
    fun buildApplyTaskArgs(task: MemberTaskState): JSONArray {
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
    fun isTerminalStatus(status: String): Boolean {
        return status.uppercase() in terminalStatuses + confirmedStatuses
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

    private fun parseTaskState(taskObject: JSONObject): MemberTaskState? {
        val task = taskObject.optJSONObject("taskProcessVO") ?: taskObject
        val config = task.optJSONObject("simpleTaskConfig")
            ?: task.optJSONObject("taskConfig")
            ?: JSONObject()
        val processId = firstNonBlank(
            task,
            "processId",
            "taskProcessId"
        )
        val configId = firstNonBlank(
            task,
            "taskConfigId"
        ).ifBlank {
            firstNonBlank(config, "configId", "taskConfigId", "id")
        }
        val adBizId = task.optJSONObject("lightsAdExtMap")
            ?.optString("bizId")
            .orEmpty()
            .ifBlank { task.optString("adBizId") }
        val stableKey = when {
            processId.isNotBlank() -> processId
            configId.isNotBlank() && adBizId.isNotBlank() ->
                "$configId#$adBizId"
            configId.isNotBlank() -> configId
            else -> return null
        }
        val extInfo = task.optJSONObject("extInfo")
        return MemberTaskState(
            stableKey = stableKey,
            processId = processId,
            configId = configId,
            adBizId = adBizId,
            title = firstNonBlank(config, "title", "name")
                .ifBlank { firstNonBlank(task, "title", "name") },
            status = firstNonBlank(
                task,
                "status",
                "subStatus",
                "taskStatus"
            ),
            current = firstInt(
                task,
                extInfo,
                "currentCount",
                "PERIOD_CURRENT_COUNT"
            ),
            limit = firstInt(
                task,
                extInfo,
                "targetCount",
                "PERIOD_TARGET_COUNT"
            ),
            targetBusiness = selectSupportedTargetBusiness(
                task.optJSONArray("targetBusiness")
                    ?: config.optJSONArray("targetBusiness")
            ),
            source = JSONObject(task.toString())
        )
    }

    @JvmStatic
    fun buildExecuteTaskArgs(task: MemberTaskState, outBizNo: Long): JSONArray {
        val businessParts = task.targetBusiness.split("#", limit = 3)
        val bizType = businessParts.getOrNull(0).orEmpty()
        val bizSubType = businessParts.getOrNull(1)
            .takeIf { it == "15S" }
            ?: "UNLIMITED"
        val bizParam = businessParts.getOrNull(2).orEmpty()
        return JSONArray().put(
            JSONObject()
                .put("bizType", bizType)
                .put("bizSubType", bizSubType)
                .put("bizParam", bizParam)
                .put("outBizNo", outBizNo.toString())
                .put("sourcePassMap", buildSourcePassMap())
        )
    }

    private fun selectSupportedTargetBusiness(
        targetBusiness: JSONArray?
    ): String {
        if (targetBusiness == null) {
            return ""
        }
        for (index in 0 until targetBusiness.length()) {
            val value = targetBusiness.optString(index)
            val parts = value.split("#")
            val type = parts.firstOrNull().orEmpty().uppercase()
            if (
                type == "CALL_APP" &&
                parts.getOrNull(1).orEmpty().isNotBlank()
            ) {
                return value
            }
            if (
                type == "BROWSE" &&
                parts.getOrNull(1).orEmpty().isNotBlank() &&
                parts.getOrNull(2).orEmpty().isNotBlank()
            ) {
                return value
            }
        }
        return ""
    }

    private fun taskResponseContainers(root: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val pending = ArrayDeque<JSONObject>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            result += current
            listOf("data", "result", "resultData").forEach { key ->
                current.optJSONObject(key)?.let(pending::addLast)
            }
        }
        return result
    }

    private fun hasSuccessfulResponseMarker(
        containers: List<JSONObject>
    ): Boolean {
        var markerFound = false
        for (container in containers) {
            if (container.has("success")) {
                markerFound = true
                if (!container.optBoolean("success", false)) {
                    return false
                }
            }
            for (key in listOf("resultCode", "code")) {
                if (!container.has(key)) {
                    continue
                }
                markerFound = true
                if (
                    container.optString(key).uppercase() !in
                    setOf("SUCCESS", "100", "200", "0")
                ) {
                    return false
                }
            }
        }
        return markerFound
    }

    private fun firstInt(
        primary: JSONObject,
        secondary: JSONObject?,
        primaryKey: String,
        secondaryKey: String
    ): Int? {
        if (primary.has(primaryKey)) {
            return primary.optString(primaryKey).toIntOrNull()
                ?: primary.optInt(primaryKey)
        }
        if (secondary?.has(secondaryKey) == true) {
            return secondary.optString(secondaryKey).toIntOrNull()
                ?: secondary.optInt(secondaryKey)
        }
        return null
    }

    private fun firstNonBlank(
        value: JSONObject,
        vararg keys: String
    ): String {
        return keys.asSequence()
            .map { value.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    private fun buildGameActivityArgs(context: MemberGameVisitContext): JSONObject {
        return JSONObject()
            .put("channelTaskPassThrough", context.channelTaskPassThrough)
            .put("deviceLevel", "high")
            .put("source", context.source)
            .put("unityDeviceLevel", "high")
    }

    private fun buildApplyAdTaskArgs(
        adBizId: String,
        adId: String,
        awardNum: Int,
        extMap: JSONObject
    ): JSONArray {
        val copiedExtMap = JSONObject(extMap.toString()).apply {
            if (!has("adId")) put("adId", adId)
            if (!has("bizId")) put("bizId", adBizId)
            if (!has("spaceCode")) put("spaceCode", TASK_SPACE_CODE)
        }
        val request = JSONObject()
            .put("adBizId", adBizId)
            .put("adId", adId)
            .put("awardNum", awardNum)
            .put("bizNo", adBizId)
            .put("extMap", copiedExtMap)
            .put("scene", "TASK_WALL")
            .put("sourcePassMap", buildSourcePassMap())
            .put("spaceCode", TASK_SPACE_CODE)
            .put("subScene", "")
            .put("userId", "")
        return JSONArray().put(request)
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
