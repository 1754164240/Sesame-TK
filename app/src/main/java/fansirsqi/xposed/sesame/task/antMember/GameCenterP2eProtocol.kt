package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject

object GameCenterP2eProtocol {
    const val HOME_SOURCE = "ch_appcollect__chsub_my-recentlyUsed"
    const val TASK_SOURCE = "ch_appcenter__chsub_recentUSE"
    const val GIT_VERSION = "9e159d58cce04c13a"
    private const val PLATFORM_ACTIVITY_ID = "P2E_PLATFORM_TASK"

    @JvmStatic
    @JvmOverloads
    fun buildHomeArgs(source: String = HOME_SOURCE): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("canAddHome", true)
                .put("deviceLevel", "high")
                .put("screenType", 10)
                .put("source", source)
                .put("subscribePanelCheck", true)
                .put("__git", GIT_VERSION)
                .put("unityDeviceLevel", "high")
        )
    }

    @JvmStatic
    @JvmOverloads
    fun buildTaskListArgs(
        sessionId: String,
        source: String = TASK_SOURCE
    ): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("deviceLevel", "high")
                .put(
                    "panelLaunchableCheckMap",
                    JSONObject().put("SET_HEAD_TASK", true)
                )
                .put("sessionId", sessionId)
                .put("setHeadPanelCheck", true)
                .put("source", source)
                .put("subscribePanelCheck", true)
                .put("__git", GIT_VERSION)
                .put("unityDeviceLevel", "high")
        )
    }

    @JvmStatic
    @JvmOverloads
    fun buildPlatformTaskArgs(
        task: JSONObject,
        actionChannel: String = "taskList",
        source: String = TASK_SOURCE
    ): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("actionChannel", actionChannel)
                .put("activityId", PLATFORM_ACTIVITY_ID)
                .put("source", source)
                .put("taskId", task.optString("taskId"))
                .put("taskToken", task.optString("taskToken"))
        )
    }

    @JvmStatic
    @JvmOverloads
    fun buildReceiveTaskArgs(
        task: JSONObject,
        actionChannel: String = "taskList",
        source: String = TASK_SOURCE,
        oriChInfo: String = source
    ): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("actionChannel", actionChannel)
                .put(
                    "activityId",
                    task.optString("activityId").ifBlank { PLATFORM_ACTIVITY_ID }
                )
                .put("__git", GIT_VERSION)
                .put("oriChInfo", oriChInfo)
                .put("source", source)
                .put("taskId", task.optString("taskId"))
                .put("taskToken", task.optString("taskToken"))
                .put("taskType", task.optString("taskType"))
        )
    }

    @JvmStatic
    @JvmOverloads
    fun buildSignInArgs(
        date: String,
        index: Int,
        signSequenceId: String,
        source: String = HOME_SOURCE
    ): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("date", date)
                .put("index", index)
                .put("signSequenceId", signSequenceId)
                .put("source", source)
                .put("__git", GIT_VERSION)
        )
    }

    @JvmStatic
    fun buildSimpleArgs(): JSONArray {
        return JSONArray().put(JSONObject().put("__git", GIT_VERSION))
    }
}
