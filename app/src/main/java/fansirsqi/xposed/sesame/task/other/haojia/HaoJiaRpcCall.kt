package fansirsqi.xposed.sesame.task.other.haojia

import fansirsqi.xposed.sesame.hook.RequestManager
import org.json.JSONObject

object HaoJiaRpcCall {

    private const val OPERATION_PARAM_ID = "independent_component_program2023082800847098"
    private const val CHANNEL = "jiaofei_card_promo"

    /**
     * 通用请求构建器
     */
    private fun request(componentId: String, content: JSONObject = JSONObject()): String {
        // 构造 components 结构
        val componentPayload = JSONObject()
        if (CHANNEL.isNotEmpty()) {
            componentPayload.put("channel", CHANNEL)
        }
        // 合并额外参数
        val keys = content.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            componentPayload.put(key, content.get(key))
        }

        val components = JSONObject()
        components.put(componentId, componentPayload)

        val requestData = JSONObject()
        requestData.put("channel", CHANNEL)
        requestData.put("components", components)
        requestData.put("operationParamIdentify", OPERATION_PARAM_ID)
        requestData.put("source", "jiaofei")

        // 包装成数组
        val args = "[$requestData]"

        return RequestManager.requestString(
            "alipay.imasp.program.programInvoke",
            args
        )
    }

    /**
     * 查询签到信息 (Recall)
     */
    fun querySignIn(): String {
        return request("independent_component_sign_in_00966139_independent_component_sign_in_recall")
    }

    /**
     * 执行签到
     * @param code 签到周期编码，如 SIG2025122904098128
     */
    fun doSignIn(code: String): String {
        val content = JSONObject()
        content.put("code", code)
        return request("independent_component_sign_in_00966139_independent_component_sign_in", content)
    }

    /**
     * 查询任务列表
     */
    fun queryTaskList(): String {
        return request("independent_component_task_reward_00793835_independent_component_task_reward_query")
    }

    /**
     * 申请/领取/完成任务
     * @param taskCode 任务编码，如 TT2026012002107380
     */
    fun applyTask(taskCode: String): String {
        val content = JSONObject()
        content.put("code", taskCode)
        return request("independent_component_task_reward_00793835_independent_component_task_reward_apply", content)
    }
}