package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONArray
import org.json.JSONObject

enum class ForestTaskQuerySource {
    POPUP,
    HOME_LEAVES,
    TAKE_LOOK_END,
    HOME,
    OPEN_GREEN_HOME
}

object ForestTaskProtocol {

    @JvmStatic
    fun querySources(signConfirmed: Boolean): List<ForestTaskQuerySource> {
        return buildList {
            add(ForestTaskQuerySource.POPUP)
            add(ForestTaskQuerySource.HOME_LEAVES)
            add(ForestTaskQuerySource.TAKE_LOOK_END)
            if (!signConfirmed) {
                add(ForestTaskQuerySource.HOME)
            }
            add(ForestTaskQuerySource.OPEN_GREEN_HOME)
        }
    }

    @JvmStatic
    fun buildTaskListArgs(
        fromAct: String,
        source: String,
        version: String,
        extend: JSONObject
    ): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("extend", JSONObject(extend.toString()))
                .put("fromAct", fromAct)
                .put("source", source)
                .put("version", version)
        )
    }

    @JvmStatic
    fun buildPopupTaskArgs(
        source: String,
        nativeVersion: String,
        version: String
    ): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put(
                    "extend",
                    JSONObject()
                        .put("appMode", "normal")
                        .put("nativeVersion", nativeVersion)
                        .put("osType", "android")
                )
                .put("fromAct", "pop_task")
                .put("needInitSign", false)
                .put("needTeamPlantRewardInfo", false)
                .put("source", source)
                .put(
                    "statusList",
                    JSONArray()
                        .put("TODO")
                        .put("FINISHED")
                )
                .put("version", version)
        )
    }

    @JvmStatic
    fun buildOpenGreenTaskArgs(
        sceneCode: String,
        source: String,
        extend: JSONObject?
    ): JSONArray {
        val request = JSONObject()
        if (extend != null) {
            request.put("extend", JSONObject(extend.toString()))
        }
        request
            .put("requestType", "RPC")
            .put("sceneCode", sceneCode)
            .put("source", source)
        return JSONArray().put(request)
    }
}
