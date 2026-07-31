package fansirsqi.xposed.sesame.task.antOcean

import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.hook.RpcRequestContext
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONArray
import org.json.JSONObject

object AntAiFishRpcCall {

    @JvmStatic
    fun status(): String {
        return RequestManager.requestString(
            "alipay.antaifish.h5.status",
            buildStatusArgs(uniqueId())
        )
    }

    @JvmStatic
    fun homepage(): String {
        return RequestManager.requestString(
            "alipay.antaifish.h5.homepage",
            buildHomepageArgs(uniqueId())
        )
    }

    @JvmStatic
    fun listTasks(sceneCode: String): String {
        return RequestManager.requestString(
            "com.alipay.antieptask.listTaskopengreen",
            buildListTasksArgs(sceneCode, uniqueId())
        )
    }

    @JvmStatic
    fun finishTask(sceneCode: String, taskType: String): String {
        return RequestManager.requestString(
            "com.alipay.antiep.finishTask",
            buildFinishTaskArgs(
                sceneCode,
                taskType,
                "${taskType}_${RandomUtil.nextDouble()}",
                uniqueId()
            ),
            RpcRequestContext(
                traceId = "ai-fish-finish-${System.nanoTime()}",
                source = "AI摸鱼",
                stage = "任务完成",
                taskId = taskType,
                targetBusiness = sceneCode
            )
        )
    }

    @JvmStatic
    fun receiveTaskAward(sceneCode: String, taskType: String): String {
        return RequestManager.requestString(
            "com.alipay.antieptask.receiveTaskAwardopengreen",
            buildReceiveTaskAwardArgs(sceneCode, taskType, uniqueId())
        )
    }

    @JvmStatic
    fun rescueFish(): String {
        return RequestManager.requestString(
            "alipay.antaifish.h5.rescueFish",
            buildOceanActionArgs(uniqueId())
        )
    }

    @JvmStatic
    fun touchFish(): String {
        return RequestManager.requestString(
            "alipay.antaifish.h5.touchfish",
            buildOceanActionArgs(uniqueId())
        )
    }

    internal fun buildStatusArgs(uniqueId: String): String {
        return arguments(
            JSONObject()
                .put("source", "nengliangtixing")
                .put("uniqueId", uniqueId)
        )
    }

    internal fun buildHomepageArgs(uniqueId: String): String {
        return buildOceanActionArgs(uniqueId)
    }

    internal fun buildListTasksArgs(
        sceneCode: String,
        uniqueId: String
    ): String {
        return arguments(
            JSONObject()
                .put("extend", JSONObject().put("appMode", "normal"))
                .put("requestType", "RPC")
                .put("sceneCode", sceneCode)
                .put("source", "ANTAIFISH")
                .put("uniqueId", uniqueId)
        )
    }

    internal fun buildFinishTaskArgs(
        sceneCode: String,
        taskType: String,
        outBizNo: String,
        uniqueId: String
    ): String {
        return arguments(
            JSONObject()
                .put("outBizNo", outBizNo)
                .put("requestType", "RPC")
                .put("sceneCode", sceneCode)
                .put("source", "ANTAIFISH")
                .put("taskType", taskType)
                .put("uniqueId", uniqueId)
        )
    }

    internal fun buildReceiveTaskAwardArgs(
        sceneCode: String,
        taskType: String,
        uniqueId: String
    ): String {
        return arguments(
            JSONObject()
                .put("ignoreLimit", false)
                .put("requestType", "RPC")
                .put("sceneCode", sceneCode)
                .put("source", "ANTAIFISH")
                .put("taskType", taskType)
                .put("uniqueId", uniqueId)
        )
    }

    internal fun buildOceanActionArgs(uniqueId: String): String {
        return arguments(
            JSONObject()
                .put("source", "ANT_OCEAN")
                .put("uniqueId", uniqueId)
        )
    }

    private fun arguments(request: JSONObject): String {
        return JSONArray().put(request).toString()
    }

    private fun uniqueId(): String {
        return "${System.currentTimeMillis()}${RandomUtil.nextLong()}"
    }
}
