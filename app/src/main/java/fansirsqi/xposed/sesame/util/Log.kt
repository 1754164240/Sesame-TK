package fansirsqi.xposed.sesame.util

import android.content.Context
import fansirsqi.xposed.sesame.model.BaseModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 日志工具类，负责初始化和管理各种类型的日志记录器，并提供日志输出方法。
 */
object Log {
    private const val DEFAULT_TAG = ""
    private const val MAX_DUPLICATE_ERRORS = 3 // 最多打印3次相同错误

    // 错误去重机制
    private val errorCountMap = ConcurrentHashMap<String, AtomicInteger>()

    // Logger 实例
    private val RECORD_LOGGER: Logger
    private val DEBUG_LOGGER: Logger
    private val FOREST_LOGGER: Logger
    private val FARM_LOGGER: Logger
    private val OTHER_LOGGER: Logger
    private val ERROR_LOGGER: Logger
    private val CAPTURE_LOGGER: Logger

    init {
        // 🔥 1. 立即初始化 Logcat，确保在任何 Context 到来之前控制台可用
        Logback.initLogcatOnly()

        // 2. 初始化 Logger 实例 (此时它们已经有了 Logcat 能力)
        RECORD_LOGGER = LoggerFactory.getLogger("record")
        DEBUG_LOGGER = LoggerFactory.getLogger("debug")
        FOREST_LOGGER = LoggerFactory.getLogger("forest")
        FARM_LOGGER = LoggerFactory.getLogger("farm")
        OTHER_LOGGER = LoggerFactory.getLogger("other")
        ERROR_LOGGER = LoggerFactory.getLogger("error")
        CAPTURE_LOGGER = LoggerFactory.getLogger("capture")
    }

    /**
     * 🔥 新增初始化方法
     * 在这里传入 Context，追加文件日志功能
     */
    @JvmStatic
    fun init(context: Context) {
        try {
            Logback.initFileLogging(context)
        } catch (e: Exception) {
            android.util.Log.e("SesameLog", "Log init failed", e)
        }
    }

    // --- 日志方法 ---


    @JvmStatic
    fun record(msg: String) {
        if (BaseModel.recordLog.value == true) {
            Logback.refreshIfCrossDay()
            RECORD_LOGGER.info("$DEFAULT_TAG{}", msg)
        }
    }

    @JvmStatic
    fun record(tag: String, msg: String) {
        record("[$tag]: $msg")
    }

    @JvmStatic
    fun forest(msg: String) {
        record(msg)
        Logback.refreshIfCrossDay()
        FOREST_LOGGER.debug("{}", msg)
    }

    @JvmStatic
    fun forest(tag: String, msg: String) {
        forest("[$tag]: $msg")
    }

    @JvmStatic
    fun farm(msg: String) {
        record(msg)
        Logback.refreshIfCrossDay()
        FARM_LOGGER.debug("{}", msg)
    }

    @JvmStatic
    fun other(msg: String) {
        Logback.refreshIfCrossDay()
        OTHER_LOGGER.debug("{}", msg)
    }

    @JvmStatic
    fun other(tag: String, msg: String) {
        other("[$tag]: $msg")
    }

    @JvmStatic
    fun debug(msg: String) {
        Logback.refreshIfCrossDay()
        DEBUG_LOGGER.debug("{}", msg)
    }

    @JvmStatic
    fun debug(tag: String, msg: String) {
        debug("[$tag]: $msg")
    }

    @JvmStatic
    fun error(msg: String) {
        Logback.refreshIfCrossDay()
        ERROR_LOGGER.error("$DEFAULT_TAG{}", msg)
    }

    @JvmStatic
    fun error(tag: String, msg: String) {
        error("[$tag]: $msg")
    }

    @JvmStatic
    fun capture(msg: String) {
        Logback.refreshIfCrossDay()
        CAPTURE_LOGGER.info("$DEFAULT_TAG{}", msg)
    }

    @JvmStatic
    fun capture(tag: String, msg: String) {
        capture("[$tag]: $msg")
    }

    fun d(tag: String, msg: String) {
        DEBUG_LOGGER.debug("[$tag]: $msg")
    }

    fun i(tag: String, msg: String) {
        RECORD_LOGGER.info("[$tag]: $msg")
    }

    fun w(tag: String, msg: String) {
        RECORD_LOGGER.warn("[$tag]: $msg")
    }

    fun e(tag: String, msg: String, th: Throwable?=null) {
        ERROR_LOGGER.error("[$tag]: $msg ${android.util.Log.getStackTraceString(th)}")
    }


    /**
     * 检查是否应该打印此错误（去重机制）
     */
    private fun shouldPrintError(th: Throwable?): Boolean {
        if (th == null) return false

        // 提取错误特征
        var errorSignature = th.javaClass.simpleName + ":" +
                (th.message?.take(50) ?: "null")

        // 特殊处理：JSON解析空字符串错误
        if (th.message?.contains("End of input at character 0") == true) {
            errorSignature = "JSONException:EmptyResponse"
        }

        val count = errorCountMap.computeIfAbsent(errorSignature) { AtomicInteger(0) }
        val currentCount = count.incrementAndGet()

        // 如果是第3次，记录一个汇总信息
        if (currentCount == MAX_DUPLICATE_ERRORS) {
            record("⚠️ 错误【$errorSignature】已出现${currentCount}次，后续将不再打印详细堆栈")
            return true
        }

        // 超过最大次数后不再打印
        return currentCount <= MAX_DUPLICATE_ERRORS
    }

    @JvmStatic

    fun printStackTrace(th: Throwable) {
        if (shouldPrintError(th)) return
        val stackTrace = "error: " + android.util.Log.getStackTraceString(th)
        error(stackTrace)
    }

    @JvmStatic

    fun printStackTrace(msg: String, th: Throwable) {
        if (shouldPrintError(th)) return
        val stackTrace = "Throwable error: " + android.util.Log.getStackTraceString(th)
        error(msg, stackTrace)
    }

    @JvmStatic
    fun printStackTrace(tag: String, msg: String, th: Throwable) {
        if (shouldPrintError(th)) return
        val stackTrace = "[$tag] Throwable error: " + android.util.Log.getStackTraceString(th)
        error(msg, stackTrace)
    }

    // 兼容 Exception 参数的重载 (Kotlin 中 Exception 是 Throwable 的子类，其实可以直接用上面的)
    // 但为了保持原有 Java API 的签名习惯，这里保留
    @JvmStatic
    fun printStackTrace(e: Exception) {
        printStackTrace(e as Throwable)
    }

    @JvmStatic
    fun printStackTrace(msg: String, e: Exception) {
        printStackTrace(msg, e as Throwable)
    }

    @JvmStatic
    fun printStackTrace(tag: String, msg: String, e: Exception) {
        printStackTrace(tag, msg, e as Throwable)
    }

    @JvmStatic
    fun printStack(tag: String) {
        val stackTrace = "stack: " + android.util.Log.getStackTraceString(Exception("获取当前堆栈$tag:"))
        record(stackTrace)
    }
}
