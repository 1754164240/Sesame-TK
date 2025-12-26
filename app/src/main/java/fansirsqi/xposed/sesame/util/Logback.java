package fansirsqi.xposed.sesame.util;

import android.content.Context;

import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;

public class Logback {
    private static String LOG_DIR;


    public static List<String> logNames = List.of(
            "runtime", "system", "record", "debug", "forest",
            "farm", "other", "error", "capture");

    public static void configureLogbackDirectly(Context context) {
        if (LOG_DIR == null) {
            LOG_DIR = Files.LOG_DIR.getPath() + File.separator;
        }
        File testDir = new File(LOG_DIR);
        if (!testDir.exists() || !testDir.canWrite()) {
            // 如果 Files 类定义的路径不可用（例如没权限），回退到 Android 内部私有目录
            // getExternalFilesDir 不需要权限即可读写
            File fallbackDir = context.getExternalFilesDir("logs");
            if (fallbackDir != null) {
                LOG_DIR = fallbackDir.getAbsolutePath() + File.separator;
            } else {
                LOG_DIR = context.getFilesDir().getAbsolutePath() + File.separator + "logs" + File.separator;
            }
        }
        // 确保目录存在（先有路径再创建）
        new File(LOG_DIR + "bak").mkdirs();


        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.stop();
        for (String logName : logNames) {
            setupAppender(lc, logName);
        }

        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setContext(lc);
        ple.setPattern("[%thread] %logger{80} %msg%n");
        ple.start();

        LogcatAppender la = new LogcatAppender();
        la.setContext(lc);
        la.setEncoder(ple);
        la.start();

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ROOT");
        root.addAppender(la);
    }

    static void setupAppender(LoggerContext loggerContext, String logName) {
        RollingFileAppender<ILoggingEvent> rfa = new RollingFileAppender<>();
        rfa.setContext(loggerContext);
        rfa.setName(logName);
        rfa.setFile(LOG_DIR + logName + ".log");

        SizeAndTimeBasedRollingPolicy<ILoggingEvent> satbrp = new SizeAndTimeBasedRollingPolicy<>();
        satbrp.setContext(loggerContext);
        satbrp.setFileNamePattern(LOG_DIR + "bak/" + logName + "-%d{yyyy-MM-dd}.%i.log");
        satbrp.setMaxFileSize(FileSize.valueOf("50MB"));
        satbrp.setTotalSizeCap(FileSize.valueOf("100MB"));
        satbrp.setMaxHistory(7);
        satbrp.setCleanHistoryOnStart(true);
        satbrp.setParent(rfa);
        satbrp.start();

        rfa.setRollingPolicy(satbrp);

        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setContext(loggerContext);
        ple.setPattern("%d{dd日 HH:mm:ss.SS} %msg%n");
        ple.start();

        rfa.setEncoder(ple);
        rfa.start();

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(logName);
        root.addAppender(rfa);
    }
}