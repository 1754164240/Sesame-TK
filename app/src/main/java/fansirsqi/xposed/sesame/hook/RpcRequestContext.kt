package fansirsqi.xposed.sesame.hook

enum class RpcRequestPurpose {
    BUSINESS,
    VERIFICATION_PROBE
}

data class RpcRequestContext(
    val traceId: String,
    val source: String,
    val stage: String,
    val taskName: String? = null,
    val configId: String? = null,
    val taskId: String? = null,
    val targetBusiness: String? = null
) {
    fun toSafeLog(method: String?): String {
        return buildList {
            add("traceId=$traceId")
            add("来源=$source")
            add("阶段=$stage")
            taskName?.takeIf(String::isNotBlank)?.let { add("任务名称=$it") }
            configId?.takeIf(String::isNotBlank)?.let { add("configId=$it") }
            taskId?.takeIf(String::isNotBlank)?.let { add("taskId=$it") }
            targetBusiness?.takeIf(String::isNotBlank)?.let { add("targetBusiness=$it") }
            method?.takeIf(String::isNotBlank)?.let { add("RPC方法=$it") }
        }.joinToString(separator = " | ")
    }

    fun verificationBlockLog(method: String?): String {
        val prefix = if (source == "会员任务" && stage == "任务列表查询") {
            "任务列表查询触发验证 | "
        } else {
            ""
        }
        return prefix + toSafeLog(method)
    }
}
