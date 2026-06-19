package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

object AntFarmParadiseLimitedActivity {
    const val SCENE_CODE: String = "ANTFARM_LEYUAN_DAILY_TASK"
    const val SIGN_TASK_TYPE: String = "2026cc_lyqd"
    const val TREASURE_BOX_TASK_TYPE: String = "2026cc_GAME_ljkbx"

    val rewardTaskTypes: Set<String> = setOf(SIGN_TASK_TYPE, TREASURE_BOX_TASK_TYPE)

    private val activityEndMillis: Long = LocalDateTime.of(2026, 12, 31, 23, 0)
        .atZone(ZoneId.of("Asia/Shanghai"))
        .toInstant()
        .toEpochMilli()

    data class ClaimableTask(
        val taskType: String,
        val title: String,
        val awardCount: Int
    )

    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis <= activityEndMillis
    }

    fun claimableTasks(
        response: JSONObject,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ClaimableTask> {
        if (!isActive(nowMillis)) {
            return emptyList()
        }

        val taskList = response.optJSONObject("taskTriggerPlayInfo")
            ?.optJSONArray("taskList")
            ?: return emptyList()

        val result = mutableListOf<ClaimableTask>()
        for (i in 0 until taskList.length()) {
            val task = taskList.optJSONObject(i) ?: continue
            val sceneCode = task.optString("sceneCode")
            val taskStatus = task.optString("taskStatus")
            val taskType = task.optString("taskType")
            val awardCount = task.optInt("awardCount", 0)
            if (sceneCode != SCENE_CODE ||
                taskStatus != "FINISHED" ||
                !rewardTaskTypes.contains(taskType) ||
                awardCount <= 0
            ) {
                continue
            }

            val title = task.optJSONObject("bizInfo")
                ?.optString("title")
                ?.takeIf { it.isNotBlank() }
                ?: taskType
            result.add(ClaimableTask(taskType, title, awardCount))
        }
        return result
    }

    fun shouldOpenTreasureBoxesBeforeClaim(
        response: JSONObject,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isActive(nowMillis)) {
            return false
        }

        val taskList = response.optJSONObject("taskTriggerPlayInfo")
            ?.optJSONArray("taskList")
            ?: return false

        for (i in 0 until taskList.length()) {
            val task = taskList.optJSONObject(i) ?: continue
            if (task.optString("sceneCode") == SCENE_CODE &&
                task.optString("taskType") == TREASURE_BOX_TASK_TYPE &&
                task.optString("taskStatus") == "TODO"
            ) {
                return true
            }
        }
        return false
    }
}
