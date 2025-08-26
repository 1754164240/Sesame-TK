package fansirsqi.xposed.sesame.newutil

import android.content.Context
import android.os.Process
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.mmkv.MMKV
import fansirsqi.xposed.sesame.util.Files

object MMKVUtil {

    private val mmkvMap = mutableMapOf<String, MMKV>()
    private val objectMapper = jacksonObjectMapper()
    private var initialized = false
    private lateinit var rootDir: String
    private const val DEFAULT_KEY = "ET3vB^#td87sQqKaY*eMUJXP"

    /** 初始化一次 */
    @Suppress("DEPRECATION")
    fun init(context: Context) {
        val dir = Files.CONFIG_DIR
        if (!dir.exists()) dir.mkdirs()
        // 确保目录全局可读写（仅适用于调试或已 root 设备）
//        dir.setReadable(true, false)
//        dir.setWritable(true, false)
        rootDir = dir.absolutePath
        MMKV.initialize(rootDir)
        initialized = true
    }

    private fun ensureInit() {
        check(initialized) { "MMKVSettingsManager must be initialized before use" }
    }

    /** 获取指定 ID 的 MMKV 实例（直接返回对象） */
    fun getMMKV(id: String = "sesame-tk"): MMKV {
        ensureInit()
        return mmkvMap.getOrPut(id) {
            MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE, DEFAULT_KEY)
        }
    }

    /** 获取带 JSON 编/解码能力的封装对象（可选） */
    fun <T> getObjectMapper(): com.fasterxml.jackson.databind.ObjectMapper = objectMapper

    // ========= 泛型对象 =========
    private inline fun <reified T> getObject(id: String, key: String, def: T): T {
        val json = getMMKV(id).decodeString(key) ?: return def
        return runCatching {
            objectMapper.readValue(json, object : TypeReference<T>() {})
        }.getOrElse { def }
    }


    fun getConfig(shared: Boolean = true, id: String = "default"): MMKV {
        return if (shared) {
            getMMKV("shared-$id")
        } else {
            val userId = Process.myUid() / 100000  // 获取当前用户ID
            getMMKV("private-$userId-$id")
        }
    }

}

