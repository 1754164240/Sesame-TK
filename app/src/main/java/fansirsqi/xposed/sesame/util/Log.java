package fansirsqi.xposed.sesame.util;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import fansirsqi.xposed.sesame.BuildConfig;
import fansirsqi.xposed.sesame.model.BaseModel;

/**
 * 日志工具类，负责初始化和管理各种类型的日志记录器，并提供日志输出方法。
 */
public class Log {
    private static final String TAG = "";
    private static final Logger RUNTIME_LOGGER;
    private static final Logger SYSTEM_LOGGER;
    private static final Logger RECORD_LOGGER;
    private static final Logger DEBUG_LOGGER;
    private static final Logger FOREST_LOGGER;
    private static final Logger FARM_LOGGER;
    private static final Logger OTHER_LOGGER;
    private static final Logger ERROR_LOGGER;
    private static final Logger CAPTURE_LOGGER;
    private static final Logger CAPTCHA_LOGGER;

    // 错误去重机制：记录错误特征和出现次数
    private static final Map<String, AtomicInteger> errorCountMap = new ConcurrentHashMap<>();
    private static final int MAX_DUPLICATE_ERRORS = 3; // 最多打印3次相同错误

    static {
        RUNTIME_LOGGER = LoggerFactory.getLogger("runtime");
        SYSTEM_LOGGER = LoggerFactory.getLogger("system");
        RECORD_LOGGER = LoggerFactory.getLogger("record");
        DEBUG_LOGGER = LoggerFactory.getLogger("debug");
        FOREST_LOGGER = LoggerFactory.getLogger("forest");
        FARM_LOGGER = LoggerFactory.getLogger("farm");
        OTHER_LOGGER = LoggerFactory.getLogger("other");
        ERROR_LOGGER = LoggerFactory.getLogger("error");
        CAPTURE_LOGGER = LoggerFactory.getLogger("capture");
        CAPTCHA_LOGGER = LoggerFactory.getLogger("captcha");
    }

    // 🔥 修改点 2：新增初始化方法
    public static void init(Context context) {
        try {
            // 在这里传入 context 进行配置
            Logback.configureLogbackDirectly(context);
        } catch (Exception e) {
            android.util.Log.e("SesameLog", "Logback init failed", e);
        }
    }

    private static String truncateLogmsg(String msg) {
        if (msg.length() > 16) {
            return msg.substring(0, 16) + "...";
        }
        return msg;
    }

    public static void system(String msg) {
        SYSTEM_LOGGER.info(TAG + "{}", msg);
    }

    public static void system(String TAG, String msg) {
        system("[" + TAG + "]: " + msg);
    }

    public static void runtime(String msg) {
        system(msg);
        if (BaseModel.Companion.getRuntimeLog().getValue() || BuildConfig.DEBUG) {
            RUNTIME_LOGGER.info(TAG + "{}", msg);
        }
    }

    public static void runtime(String TAG, String msg) {
        runtime("[" + TAG + "]: " + msg);
    }

    public static void record(String msg) {
        runtime(msg);
        if (BaseModel.Companion.getRecordLog().getValue()) {
            RECORD_LOGGER.info(TAG + "{}", msg);
        }
    }

    public static void record(String TAG, String msg) {
        record("[" + TAG + "]: " + msg);
    }

    public static void forest(String msg) {
        record(msg);
        FOREST_LOGGER.info("{}", msg);
    }

    public static void forest(String TAG, String msg) {
        forest("[" + TAG + "]: " + msg);
    }

    public static void farm(String msg) {
        record(msg);
        FARM_LOGGER.info("{}", msg);
    }

    public static void other(String msg) {
        record(msg);
        OTHER_LOGGER.info("{}", msg);
    }

    public static void other(String TAG, String msg) {
        other("[" + TAG + "]: " + msg);
    }

    public static void debug(String msg) {
        runtime(msg);
        DEBUG_LOGGER.info("{}", msg);
    }

    public static void debug(String TAG, String msg) {
        debug("[" + TAG + "]: " + msg);
    }

    public static void error(String msg) {
        runtime(msg);
        ERROR_LOGGER.error(TAG + "{}", msg);
    }

    public static void error(String TAG, String msg) {
        error("[" + TAG + "]: " + msg);
    }

    public static void capture(String msg) {
        CAPTURE_LOGGER.info(TAG + "{}", msg);
    }

    public static void capture(String TAG, String msg) {
        capture("[" + TAG + "]: " + msg);
    }

    public static void captcha(String msg) {
        runtime(msg);
        CAPTCHA_LOGGER.info("{}", msg);
    }

    public static void captcha(String TAG, String msg) {
        captcha("[" + TAG + "]: " + msg);
    }

    /**
     * 检查是否应该打印此错误（去重机制）
     *
     * @param th 异常对象
     * @return true=应该打印，false=已重复太多次
     */
    private static boolean shouldPrintError(Throwable th) {
        if (th == null) return false;

        // 提取错误特征（类名+消息的前50个字符）
        String errorSignature = th.getClass().getSimpleName() + ":" +
                (th.getMessage() != null ? th.getMessage().substring(0, Math.min(50, th.getMessage().length())) : "null");

        // 特殊处理：JSON解析空字符串错误
        if (th.getMessage() != null && th.getMessage().contains("End of input at character 0")) {
            errorSignature = "JSONException:EmptyResponse";
        }

        AtomicInteger count = errorCountMap.computeIfAbsent(errorSignature, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        // 如果是第3次，记录一个汇总信息
        if (currentCount == MAX_DUPLICATE_ERRORS) {
            runtime("⚠️ 错误【" + errorSignature + "】已出现" + currentCount + "次，后续将不再打印详细堆栈");
            return true;
        }

        // 超过最大次数后不再打印
        return currentCount > MAX_DUPLICATE_ERRORS;
    }

    public static void printStackTrace(Throwable th) {
        if (shouldPrintError(th)) return;
        String stackTrace = "error: " + android.util.Log.getStackTraceString(th);
        error(stackTrace);
    }

    public static void printStackTrace(String msg, Throwable th) {
        if (shouldPrintError(th)) return;
        String stackTrace = "Throwable error: " + android.util.Log.getStackTraceString(th);
        error(msg, stackTrace);
    }

    public static void printStackTrace(String TAG, String msg, Throwable th) {
        if (shouldPrintError(th)) return;
        String stackTrace = "[" + TAG + "] Throwable error: " + android.util.Log.getStackTraceString(th);
        error(msg, stackTrace);
    }

    public static void printStackTrace(Exception e) {
        if (shouldPrintError(e)) return;
        String stackTrace = "Exception error: " + android.util.Log.getStackTraceString(e);
        error(stackTrace);
    }

    public static void printStackTrace(String msg, Exception e) {
        if (shouldPrintError(e)) return;
        String stackTrace = "Throwable error: " + android.util.Log.getStackTraceString(e);
        error(msg, stackTrace);
    }

    public static void printStackTrace(String TAG, String msg, Exception e) {
        if (shouldPrintError(e)) return;
        String stackTrace = "[" + TAG + "] Throwable error: " + android.util.Log.getStackTraceString(e);
        error(msg, stackTrace);
    }

    public static void printStack(String TAG) {
        String stackTrace = "stack: " + android.util.Log.getStackTraceString(new Exception("获取当前堆栈" + TAG + ":"));
        system(stackTrace);
    }

}
