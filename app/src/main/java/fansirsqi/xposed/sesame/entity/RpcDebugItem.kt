package fansirsqi.xposed.sesame.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * RPC 调试项数据模型
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RpcDebugItem(
    var name: String = "",           // 功能名称
    var method: String = "",         // RPC 方法
    var requestData: Any? = null,    // 请求数据（可以是 List 或 Map）
    var id: String = ""              // 唯一ID（空时自动生成）
) {
    companion object {
        private val objectMapper = ObjectMapper()
    }
    
    init {
        // 如果 id 为空，自动生成
        if (id.isEmpty()) {
            id = System.currentTimeMillis().toString()
        }
    }
    
    /**
     * 获取显示名称（如果 name 为空则显示 method）
     * 不序列化到 JSON
     */
    @JsonIgnore
    fun getDisplayName(): String {
        return name.ifEmpty { method }
    }

    /**
     * 转换为 JSON 字符串格式（用于发送广播）
     * 不序列化到 JSON
     */
    @JsonIgnore
    fun getRequestDataString(): String {
        return when (requestData) {
            is String -> requestData as String
            is List<*> -> objectMapper.writeValueAsString(requestData)
            is Map<*, *> -> objectMapper.writeValueAsString(listOf(requestData))
            else -> "[{}]"
        }
    }
}

