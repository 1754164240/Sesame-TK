package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONArray
import org.json.JSONObject

data class AiFishHomeSnapshot(
    val recognized: Boolean,
    val fishStatus: String?,
    val remainTouchChance: Int?,
    val touchTotal: Int?
)

data class AiFishTask(
    val sceneCode: String,
    val taskType: String,
    val title: String,
    val status: String,
    val waitSeconds: Int,
    val playType: String,
    val directReceiveAward: Boolean,
    val awardCount: Int
)

data class AiFishTaskSnapshot(
    val recognized: Boolean,
    val tasks: List<AiFishTask>
)

object AiFishProtocol {
    const val MAIN_SCENE = "ANTAIFISH"
    const val RESCUE_SCENE = "ANTAIFISH_RESCUE_AND_RESTORE"

    @JvmStatic
    fun parseHome(response: String): AiFishHomeSnapshot {
        val root = responseRoot(response)
            ?: return unknownHome()
        val interact = root.optJSONObject("myFish")
            ?.optJSONObject("interactVO")
            ?: return unknownHome()
        if (
            !interact.has("fishInteractStatus") ||
            !interact.has("remainTouchChance") ||
            !interact.has("touchTotal")
        ) {
            return unknownHome()
        }
        return AiFishHomeSnapshot(
            recognized = true,
            fishStatus = interact.optString("fishInteractStatus"),
            remainTouchChance = interact.optInt("remainTouchChance"),
            touchTotal = interact.optInt("touchTotal")
        )
    }

    @JvmStatic
    fun parseTasks(response: String): AiFishTaskSnapshot {
        val root = responseRoot(response)
            ?: return AiFishTaskSnapshot(false, emptyList())
        val taskArray = root.optJSONArray("taskInfoList")
            ?: return AiFishTaskSnapshot(false, emptyList())
        val tasks = buildList {
            for (index in 0 until taskArray.length()) {
                parseTask(taskArray.optJSONObject(index))?.let(::add)
            }
        }
        return AiFishTaskSnapshot(true, tasks)
    }

    @JvmStatic
    fun isActionAccepted(response: String): Boolean {
        val root = responseRoot(response) ?: return false
        return root.optBoolean("success", false) ||
            root.optString("resultCode").equals("SUCCESS", true) ||
            root.optString("code") == "100000000"
    }

    @JvmStatic
    fun selectRescueTask(snapshot: AiFishTaskSnapshot): AiFishTask? {
        if (!snapshot.recognized) {
            return null
        }
        return snapshot.tasks
            .asSequence()
            .filter { it.sceneCode == RESCUE_SCENE }
            .filter { it.status.equals("TODO", true) }
            .filter { it.playType.equals("VISIT_FLOAT_BALL", true) }
            .filter { it.waitSeconds > 0 }
            .sortedBy(AiFishTask::taskType)
            .firstOrNull()
    }

    @JvmStatic
    fun findTask(
        snapshot: AiFishTaskSnapshot,
        taskType: String
    ): AiFishTask? {
        return snapshot.tasks.firstOrNull { it.taskType == taskType }
    }

    private fun parseTask(task: JSONObject?): AiFishTask? {
        val baseInfo = task?.optJSONObject("taskBaseInfo") ?: return null
        val taskType = baseInfo.optString("taskType").trim()
        val sceneCode = baseInfo.optString("sceneCode").trim()
        val status = baseInfo.optString("taskStatus").trim()
        if (taskType.isEmpty() || sceneCode.isEmpty() || status.isEmpty()) {
            return null
        }
        val bizInfo = objectValue(baseInfo, "bizInfo")
        val prodPlayParam = objectValue(baseInfo, "prodPlayParam")
        val rights = task.optJSONObject("taskRights")
        return AiFishTask(
            sceneCode = sceneCode,
            taskType = taskType,
            title = bizInfo?.optString("taskTitle").orEmpty(),
            status = status,
            waitSeconds = prodPlayParam
                ?.optInt("timeCount", 0)
                ?.coerceIn(0, 60)
                ?: 0,
            playType = baseInfo.optString("taskProdPlayType"),
            directReceiveAward = rights
                ?.optBoolean("directReceiveAward", false)
                ?: false,
            awardCount = rights?.optInt("awardCount", 0) ?: 0
        )
    }

    private fun objectValue(parent: JSONObject, key: String): JSONObject? {
        return when (val value = parent.opt(key)) {
            is JSONObject -> value
            is String -> runCatching { JSONObject(value) }.getOrNull()
            else -> null
        }
    }

    private fun responseRoot(response: String): JSONObject? {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return null
        return root.optJSONObject("resData") ?: root
    }

    private fun unknownHome(): AiFishHomeSnapshot {
        return AiFishHomeSnapshot(
            recognized = false,
            fishStatus = null,
            remainTouchChance = null,
            touchTotal = null
        )
    }
}
