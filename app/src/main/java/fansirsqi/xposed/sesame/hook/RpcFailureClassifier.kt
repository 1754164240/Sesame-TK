package fansirsqi.xposed.sesame.hook

import org.json.JSONObject

enum class RpcFailureKind {
    SUCCESS,
    OFFLINE,
    VERIFICATION_REQUIRED,
    FREQUENCY_LIMITED,
    RETRYABLE,
    UNKNOWN
}

data class RpcFailure(
    val code: String,
    val message: String,
    val kind: RpcFailureKind
)

object RpcFailureClassifier {
    private val codeKeys = listOf(
        "resultCode", "errorCode", "error", "errorTip", "code", "retCode", "sspErrorCode", "errCode"
    )
    private val messageKeys = listOf(
        "resultDesc", "resultView", "memo", "desc", "errorMessage", "errorMsg", "sspErrorMsg", "message"
    )
    private val successCodes = setOf("SUCCESS", "200", "100000000")
    private val verificationKeywords = listOf(
        "需要验证", "需要驗證", "进行验证", "進行驗證", "请进行验证", "安全验证", "人工验证",
        "保障您的正常访问", "保障您的操作安全"
    )
    private val frequencyKeywords = listOf(
        "操作频繁", "请求频繁", "访问频繁", "稍后再试", "频率限制", "流量限制", "次数已达上限", "cheating traffic"
    )
    private val retryableKeywords = listOf(
        "network timeout", "timeout", "网络超时", "网络异常", "网络不可用", "bridge不可用", "返回为空", "rpc返回为空"
    )

    fun classify(raw: String?): RpcFailure {
        if (raw.isNullOrBlank()) {
            return RpcFailure("EMPTY_RPC_RESPONSE", "RPC返回为空", RpcFailureKind.RETRYABLE)
        }
        val root = try {
            JSONObject(raw)
        } catch (_: Throwable) {
            return RpcFailure("MALFORMED_RPC_RESPONSE", "RPC响应无法解析", RpcFailureKind.RETRYABLE)
        }
        val objects = responseObjects(root)
        val code = firstValue(objects, codeKeys)
        val message = allValues(objects, messageKeys).joinToString(" | ")

        val kind = when {
            isVerification(code, message) -> RpcFailureKind.VERIFICATION_REQUIRED
            isOffline(code, message) -> RpcFailureKind.OFFLINE
            isFrequencyLimited(objects, code, message) -> RpcFailureKind.FREQUENCY_LIMITED
            isSuccess(objects, code) -> RpcFailureKind.SUCCESS
            code == "EMPTY_RPC_RESPONSE" || containsAny(message, retryableKeywords) -> RpcFailureKind.RETRYABLE
            else -> RpcFailureKind.UNKNOWN
        }
        return RpcFailure(code, message, kind)
    }

    private fun responseObjects(root: JSONObject): List<JSONObject> {
        return buildList {
            add(root)
            listOf("data", "result", "resData").forEach { key ->
                root.optJSONObject(key)?.let(::add)
            }
        }
    }

    private fun isSuccess(objects: List<JSONObject>, code: String): Boolean {
        return objects.any { it.has("success") && it.optBoolean("success") } || code.uppercase() in successCodes
    }

    private fun isVerification(code: String, message: String): Boolean {
        return code == "1009" || containsAny(message, verificationKeywords)
    }

    private fun isOffline(code: String, message: String): Boolean {
        return code.equals("I07", ignoreCase = true) || message.contains("离线模式")
    }

    private fun isFrequencyLimited(objects: List<JSONObject>, code: String, message: String): Boolean {
        val xlightLimited = objects.any {
            it.optString("retCode") == "217" && it.optString("sspErrorCode") == "61002"
        }
        return xlightLimited || code.contains("LIMIT", ignoreCase = true) || containsAny(message, frequencyKeywords)
    }

    private fun firstValue(objects: List<JSONObject>, keys: List<String>): String {
        for (objectValue in objects) {
            for (key in keys) {
                val value = objectValue.opt(key)?.toString()?.trim().orEmpty()
                if (value.isNotEmpty() && value != "null") return value
            }
        }
        return ""
    }

    private fun allValues(objects: List<JSONObject>, keys: List<String>): List<String> {
        return buildList {
            for (objectValue in objects) {
                for (key in keys) {
                    val value = objectValue.optString(key).trim()
                    if (value.isNotEmpty() && value !in this) add(value)
                }
            }
        }
    }

    private fun containsAny(value: String, keywords: List<String>): Boolean {
        return keywords.any { value.contains(it, ignoreCase = true) }
    }
}
