package fansirsqi.xposed.sesame.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import fansirsqi.xposed.sesame.ui.SettingActivity
import fansirsqi.xposed.sesame.ui.WebSettingsActivity
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log

class UIConfig private constructor() {

    // 对应 Lombok 的 @Setter 和 @Data 中的 getter/setter
    // 使用 var 定义可变属性
    // 将 init 命名为 isInit 以匹配 Lombok 对 boolean 类型生成的 isInit() getter
    @JsonIgnore
    var isInit: Boolean = false

    @JsonProperty("uiOption")
    var uiOption: String = UI_OPTION_WEB

    // 利用 Kotlin 的属性语法替代 getTargetActivityClass() 方法
    @get:JsonIgnore
    val targetActivityClass: Class<*>
        get() = when (uiOption) {
            UI_OPTION_WEB -> WebSettingsActivity::class.java
            UI_OPTION_NEW -> SettingActivity::class.java
            else -> {
                Log.record(TAG, "未知的 UI 选项: $uiOption")
                WebSettingsActivity::class.java
            }
        }

    companion object {
        private val TAG = UIConfig::class.java.simpleName

        @JvmField // 保持 INSTANCE 字段可以直接访问，类似 Java 的 public static final
        val INSTANCE = UIConfig()

        const val UI_OPTION_WEB = "web"
        const val UI_OPTION_NEW = "new"

        @JvmStatic
        fun save(): Boolean {
            Log.record(TAG, "保存UI配置")
            return Files.setTargetFileofDir(JsonUtil.formatJson(INSTANCE), Files.getappConfigFile())
        }

        @JvmStatic
        @Synchronized
        fun load(): UIConfig {
            val targetFile = Files.getappConfigFile()
            try {
                if (targetFile.exists()) {
                    val json = Files.readFromFile(targetFile)
                    if (json.isNotBlank()) {
                        // Jackson 反序列化更新对象
                        JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue<UIConfig>(json)
                        val formatted = JsonUtil.formatJson(INSTANCE)
                        // Kotlin 中字符串使用 != 比较的是内容 (equals)
                        if (formatted != null && formatted != json) {
                            Log.record(TAG, "格式化${TAG}配置")
                            Files.write2File(formatted, targetFile)
                        }
                    } else {
                        resetToDefault()
                    }
                } else {
                    resetToDefault()
                    Files.write2File(JsonUtil.formatJson(INSTANCE), targetFile)
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, e)
                Log.record(TAG, "重置${TAG}配置")
                resetToDefault()
                try {
                    Files.write2File(JsonUtil.formatJson(INSTANCE), targetFile)
                } catch (e2: Exception) {
                    Log.printStackTrace(TAG, e2)
                }
            }
            INSTANCE.isInit = true
            return INSTANCE
        }

        @JvmStatic
        @Synchronized
        private fun resetToDefault() {
            Log.record(TAG, "重置UI配置")
            INSTANCE.uiOption = UI_OPTION_WEB
            INSTANCE.isInit = false
        }
    }
}