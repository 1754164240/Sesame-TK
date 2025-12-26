package fansirsqi.xposed.sesame.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.*;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import fansirsqi.xposed.sesame.hook.keepalive.SmartSchedulerManager;
import fansirsqi.xposed.sesame.hook.server.ModuleHttpServerManager;
import fansirsqi.xposed.sesame.hook.simple.SimplePageManager;
import kotlin.Unit;
import lombok.Setter;

import org.json.JSONObject;
import org.luckypray.dexkit.DexKitBridge;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.InvocationTargetException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fansirsqi.xposed.sesame.BuildConfig;
import fansirsqi.xposed.sesame.data.Config;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.data.RunType;
import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.data.ViewAppInfo;
import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.hook.rpc.bridge.NewRpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.OldRpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion;
import fansirsqi.xposed.sesame.hook.rpc.intervallimit.RpcIntervalLimit;
import fansirsqi.xposed.sesame.hook.server.ModuleHttpServer;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.Model;
import fansirsqi.xposed.sesame.newutil.DataStore;
import fansirsqi.xposed.sesame.task.MainTask;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskRunnerAdapter;
import fansirsqi.xposed.sesame.util.AssetUtil;
import fansirsqi.xposed.sesame.util.Detector;
import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.NetworkUtils;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.PermissionUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.util.WakeLockManager;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.GlobalThreadPools;
import fansirsqi.xposed.sesame.hook.rpc.debug.DebugRpc;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import kotlin.jvm.JvmStatic;
import lombok.Getter;
import fansirsqi.xposed.sesame.util.maps.IdMapManager;
import fansirsqi.xposed.sesame.util.maps.VipDataIdMap;
public class ApplicationHook {
    static final String TAG = ApplicationHook.class.getSimpleName();
    public XposedInterface xposedInterface = null;
    @Getter
    @Setter
    private ModuleHttpServer httpServer;
    private static final String modelVersion = BuildConfig.VERSION_NAME;

    static String finalProcessName = "";


    /**
     * 调度器管理器
     * 用于确保调度器在使用前已正确初始化。
     */
    private static volatile boolean smartSchedulerInitialized = false;
    private static final Object schedulerInitLock = new Object();

    /**
     * 广播动作常量
     */
    private static class BroadcastActions {
        static final String RESTART = "com.eg.android.AlipayGphone.sesame.restart";
        static final String EXECUTE = "com.eg.android.AlipayGphone.sesame.execute";
        static final String PRE_WAKEUP = "com.eg.android.AlipayGphone.sesame.prewakeup";
        static final String RE_LOGIN = "com.eg.android.AlipayGphone.sesame.reLogin";
        static final String STATUS = "com.eg.android.AlipayGphone.sesame.status";
        static final String RPC_TEST = "com.eg.android.AlipayGphone.sesame.rpctest";
    }

    /**
     * 支付宝类名常量
     */
    private static class AlipayClasses {
        static final String APPLICATION = "com.alipay.mobile.framework.AlipayApplication";
        static final String SOCIAL_SDK = "com.alipay.mobile.personalbase.service.SocialSdkContactService";
    }

    /**
     * 反射缓存 - 避免重复反射查找，提升性能
     * 优化策略：只缓存 Class，不缓存 Method（避免方法签名变化导致的问题）
     */
    private static class ReflectionCache {
        private static Class<?> alipayApplicationClass;
        private static Class<?> socialSdkContactServiceClass;
        private static volatile boolean initialized = false;

        /**
         * 初始化反射缓存（安全版本：只缓存类）
         */
        static void initialize(ClassLoader loader) {
            if (initialized) return;

            try {
                // 缓存支付宝应用类
                alipayApplicationClass = XposedHelpers.findClass(AlipayClasses.APPLICATION, loader);

                // 缓存社交SDK类
                socialSdkContactServiceClass = XposedHelpers.findClass(AlipayClasses.SOCIAL_SDK, loader);

                initialized = true;
                Log.runtime(TAG, "✅ 反射缓存初始化成功");
            } catch (Throwable t) {
                Log.runtime(TAG, "⚠️ 反射缓存初始化部分失败，将使用传统反射");
                Log.printStackTrace(TAG, t);
                // 部分失败不影响使用，后续会回退到传统反射
            }
        }

        /**
         * 获取支付宝应用类（带异常处理）
         */
        static Class<?> getAlipayApplicationClass(ClassLoader loader) {
            if (!initialized) initialize(loader);

            try {
                if (alipayApplicationClass != null) {
                    return alipayApplicationClass;
                }
                // 缓存未命中，使用传统反射
                return XposedHelpers.findClass(AlipayClasses.APPLICATION, loader);
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
                return null;
            }
        }

        /**
         * 获取社交SDK类（带异常处理）
         */
        static Class<?> getSocialSdkClass(ClassLoader loader) {
            if (!initialized) initialize(loader);

            try {
                if (socialSdkContactServiceClass != null) {
                    return socialSdkContactServiceClass;
                }
                // 缓存未命中，使用传统反射
                return XposedHelpers.findClass(AlipayClasses.SOCIAL_SDK, loader);
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
                return null;
            }
        }
    }

    @Getter
    private static ClassLoader classLoader = null;
    private static Object microApplicationContextObject = null;

    @SuppressLint("StaticFieldLeak")
    static volatile Context appContext = null;


    @JvmStatic
    public static Context getAppContext() {
        return appContext;
    }

    /**
     * 确保智能调度器已初始化（双重检查锁优化）
     * 优化点：
     * 1. 快速路径完全无锁（已初始化的情况）
     * 2. 慢路径使用双重检查防止重复初始化
     * 3. volatile 保证可见性
     */
    private static void ensureScheduler() {
        // 第一次检查（无锁，快速路径）
        if (smartSchedulerInitialized) {
            return; // 最常见情况：已初始化，直接返回
        }

        // 慢路径：需要初始化
        synchronized (schedulerInitLock) {
            // 双重检查：防止多线程重复初始化
            if (smartSchedulerInitialized) {
                return;
            }

            if (appContext == null) {
                Log.debug(TAG, "⚠️ 无法初始化调度器: appContext 为 null");
                return;
            }

            try {
                Log.debug(TAG, "🔧 开始初始化智能调度器...");
                // 初始化智能调度器（纯协程，无唤醒锁）
                SmartSchedulerManager.INSTANCE.initialize(appContext);
                smartSchedulerInitialized = true; // volatile 写，保证其他线程可见
                Log.debug(TAG, "✅ 智能调度器初始化成功");
            } catch (Exception e) {
                Log.error(TAG, "❌ 智能调度器初始化失败: " + e.getMessage());
                Log.printStackTrace(TAG, e);
                // 重要：初始化失败时不设置 smartSchedulerInitialized = true，允许下次重试
            }
        }
    }

    /**
     * 调度器适配器 - 使用智能管理器
     */
    private static class SchedulerAdapter {
        static void scheduleExactExecution(long delayMillis, long nextExecutionTime) {
            SmartSchedulerManager.INSTANCE.scheduleExactExecution(delayMillis, nextExecutionTime);
        }

        static void scheduleDelayedExecution(long delayMillis) {
            SmartSchedulerManager.INSTANCE.scheduleDelayedExecution(delayMillis);
        }

        static boolean scheduleWakeupAlarm(long triggerAtMillis, int requestCode, boolean isMainAlarm) {
            return SmartSchedulerManager.INSTANCE.scheduleWakeupAlarm(triggerAtMillis, requestCode, isMainAlarm);
        }

        static void cancelAllWakeupAlarms() {
            SmartSchedulerManager.INSTANCE.cancelAllWakeupAlarms();
        }
    }

    /**
     * 任务锁管理器 - 实现 AutoCloseable 自动释放锁
     * 优势：使用 try-with-resources 自动管理锁生命周期，防止遗漏释放
     */
    private static class TaskLock implements AutoCloseable {
        private final boolean acquired;

        /**
         * 构造函数：尝试获取任务锁
         *
         * @throws IllegalStateException 如果任务已在运行中
         */
        TaskLock() {
            synchronized (taskLock) {
                if (isTaskRunning) {
                    acquired = false;
                    throw new IllegalStateException("任务已在运行中");
                }
                isTaskRunning = true;
                acquired = true;
            }
        }

        /**
         * 释放任务锁
         */
        @Override
        public void close() {
            if (acquired) {
                synchronized (taskLock) {
                    isTaskRunning = false;
                }
            }
        }
    }

    @Getter
    static AlipayVersion alipayVersion = new AlipayVersion("");

    private static volatile boolean hooked = false;

    @JvmStatic
    public static boolean isHooked() {
        return hooked;
    }

    private static volatile boolean init = false;
    static volatile Calendar dayCalendar;
    @Getter
    static volatile boolean offline = false;
    private static volatile boolean alarmTriggeredFlag = false;
    @Getter
    static final AtomicInteger reLoginCount = new AtomicInteger(0);

    private static volatile boolean batteryPermissionChecked = false;


    @SuppressLint("StaticFieldLeak")
    static Service service;
    @Getter
    static Handler mainHandler;
    /**
     * -- GETTER --
     * 获取主任务实例 - 供任务调度使用
     */
    @Getter
    static MainTask mainTask;

    static volatile RpcBridge rpcBridge;
    private static final Object rpcBridgeLock = new Object();
    @Getter
    private static RpcVersion rpcVersion;

    // 任务执行互斥锁（防止任务重叠执行）
    private static volatile boolean isTaskRunning = false;
    private static final Object taskLock = new Object();

    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }


    private static volatile long lastExecTime = 0; // 添加为类成员变量
    public static volatile long nextExecutionTime = 0;
    private static final long MAX_INACTIVE_TIME = 3600000; // 最大不活动时间：1小时

    static {
        dayCalendar = Calendar.getInstance();
        dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
        dayCalendar.set(Calendar.MINUTE, 0);
        dayCalendar.set(Calendar.SECOND, 0);
        Method m = null;
        try {
            //noinspection JavaReflectionMemberAccess
            m = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
        } catch (Throwable t) {
            XposedBridge.log("E/" + TAG + " " + android.util.Log.getStackTraceString(t));
        }
        deoptimizeMethod = m;
    }

    private final static Method deoptimizeMethod;

    static void deoptimizeMethod(Class<?> c) throws InvocationTargetException, IllegalAccessException {
        for (Method m : c.getDeclaredMethods()) {
            if (deoptimizeMethod != null && m.getName().equals("makeApplicationInner")) {
                deoptimizeMethod.invoke(null, m);
                if (BuildConfig.DEBUG)
                    XposedBridge.log("D/" + TAG + " Deoptimized " + m.getName());
            }
        }
    }

    /**
     * 调度下次执行（公共静态方法）
     */
    @JvmStatic
    public static void scheduleNextExecution() {
        scheduleNextExecutionInternal(lastExecTime);
    }

    /**
     * 调度定时执行（内部静态方法）
     *
     * @param lastExecTime 上次执行时间
     */
    private static void scheduleNextExecutionInternal(long lastExecTime) {
        try {
            // 检查长时间未执行的情况
            checkInactiveTime();
            int checkInterval = BaseModel.Companion.getCheckInterval().getValue();
            List<String> execAtTimeList = BaseModel.Companion.getExecAtTimeList().getValue();
            if (execAtTimeList != null && execAtTimeList.contains("-1")) {
                Log.record(TAG, "定时执行未开启");
                return;
            }

            long delayMillis = checkInterval; // 默认使用配置的检查间隔
            long targetTime = 0;

            try {
                if (execAtTimeList != null) {
                    Calendar lastExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime);
                    Calendar nextExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime + checkInterval);
                    for (String execAtTime : execAtTimeList) {
                        Calendar execAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(execAtTime);
                        if (execAtTimeCalendar != null && lastExecTimeCalendar.compareTo(execAtTimeCalendar) < 0 && nextExecTimeCalendar.compareTo(execAtTimeCalendar) > 0) {
                            Log.record(TAG, "设置定时执行:" + execAtTime);
                            targetTime = execAtTimeCalendar.getTimeInMillis();
                            delayMillis = targetTime - lastExecTime;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.printStackTrace(TAG, "execAtTime err:：", e);
            }

            // 使用调度器（协程或 WorkManager）
            nextExecutionTime = targetTime > 0 ? targetTime : (lastExecTime + delayMillis);

            if (appContext == null) {
                Log.error(TAG, "❌ 无法调度任务：appContext 为 null");
                return;
            }

            ensureScheduler();
            SchedulerAdapter.scheduleExactExecution(delayMillis, nextExecutionTime);
        } catch (Exception e) {
            Log.printStackTrace(TAG, "scheduleNextExecution：", e);
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private void loadNativeLibs(Context context, File soFile) {
        try {
            File finalSoFile = AssetUtil.INSTANCE.copyStorageSoFileToPrivateDir(context, soFile);
            if (finalSoFile != null) {
                System.load(finalSoFile.getAbsolutePath());
                Log.runtime(TAG, "Loading " + soFile.getName() + " from :" + finalSoFile.getAbsolutePath());
            } else {
                Detector.INSTANCE.loadLibrary(soFile.getName().replace(".so", "").replace("lib", ""));
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, "载入so库失败！！", e);
        }
    }

    /**
     * ✅ 原有新版入口：LibXposed / LSPosed ≥ 1.9 使用
     */
    public void loadPackage(XposedModuleInterface.PackageLoadedParam lpparam) {
        Log.runtime(TAG, "xposed start loadPackage: " + lpparam.getPackageName());
        if (!General.PACKAGE_NAME.equals(lpparam.getPackageName())) return;
        classLoader = lpparam.getClassLoader();
        handleHookLogic(classLoader, lpparam.getPackageName(), lpparam.getApplicationInfo().sourceDir, lpparam);
    }

    /**
     * ✅ 新增旧版兼容入口：传统 Xposed / EdXposed / LSPosed < 1.9 使用
     */
    public void loadPackageCompat(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.runtime(TAG, "xp82 start loadPackageCompat: " + lpparam.packageName);
        XposedBridge.log(TAG + "|Hook in  " + lpparam.packageName + " in process ${lpparam.processName}");
        if (!General.PACKAGE_NAME.equals(lpparam.packageName)) return;
        classLoader = lpparam.classLoader;
        // 注意：旧版没有 ApplicationInfo.sourceDir，需要自己从 appInfo 获取
        String apkPath = lpparam.appInfo != null ? lpparam.appInfo.sourceDir : null;
        handleHookLogic(classLoader, lpparam.packageName, apkPath, lpparam);
    }


    @SuppressLint("PrivateApi")
    private void handleHookLogic(ClassLoader classLoader, String packageName, String apkPath, Object rawParam) {
        DataStore.INSTANCE.init(Files.CONFIG_DIR);
        XposedBridge.log(TAG + "|handleHookLogic " + packageName + " scuess!");
        if (hooked) return;
        hooked = true;

        String processName = null;
        if (rawParam instanceof XC_LoadPackage.LoadPackageParam) {
            processName = ((XC_LoadPackage.LoadPackageParam) rawParam).processName;
        } else if (rawParam instanceof XposedModuleInterface.PackageLoadedParam) {
            processName = XposedEnv.INSTANCE.getProcessName();
        }
        finalProcessName = processName;

        Log.runtime(TAG, "🔀 当前进程: " + finalProcessName);

        // ✅ 第一步: 尽早安装版本号 Hook (在所有其他 Hook 之前)
        VersionHook.installHook(classLoader);

        // 初始化反射缓存
        ReflectionCache.initialize(classLoader);

        // Hook验证码关闭功能
        try {
            CaptchaHook.INSTANCE.setupHook(classLoader);
            Log.runtime(TAG, "验证码Hook系统已初始化");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "验证码Hook初始化失败", t);
        }


        try {
            // 在Hook Application.attach 之前，先 deoptimize LoadedApk.makeApplicationInner
            try {
                Class<?> loadedApkClass = classLoader.loadClass("android.app.LoadedApk");
                deoptimizeMethod(loadedApkClass);
            } catch (Throwable t) {
                Log.printStackTrace(TAG, "deoptimize makeApplicationInner err:", t);
            }
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mainHandler = new Handler(Looper.getMainLooper());
                    appContext = (Context) param.args[0];

                    // 在主进程和小组件进程中注册广播接收器
                    if (General.PACKAGE_NAME.equals(finalProcessName) || (finalProcessName != null && finalProcessName.endsWith(":widgetProvider"))) {
                        registerBroadcastReceiver(appContext);
                    }

                    // SecurityBodyHelper初始化
                    SecurityBodyHelper.INSTANCE.init(classLoader);

                    //LOG日志的初始化
                    Log.init(appContext);


                    // ✅ 优先使用 Hook 捕获的版本号
                    if (VersionHook.hasVersion()) {
                        alipayVersion = VersionHook.getCapturedVersion();
                        Log.runtime(TAG, "📦 支付宝版本(Hook): " + alipayVersion.getVersionString());
                    } else {
                        // 回退方案: 使用传统 PackageManager 获取
                        Log.runtime(TAG, "⚠️ Hook 未捕获到版本号,使用回退方案");
                        try {
                            PackageInfo pInfo = appContext.getPackageManager().getPackageInfo(packageName, 0);
                            if (pInfo.versionName != null) {
                                alipayVersion = new AlipayVersion(pInfo.versionName);
                                Log.runtime(TAG, "📦 支付宝版本(回退): " + pInfo.versionName);

                            } else {
                                Log.runtime(TAG, "⚠️ 无法获取版本信息");
                                alipayVersion = new AlipayVersion(""); // 空版本
                            }
                        } catch (Exception e) {
                            Log.printStackTrace(TAG, "❌ 获取版本号失败", e);
                            alipayVersion = new AlipayVersion(""); // 空版本
                        }
                    }

                    // 加载 Native 库
                    loadNativeLibs(appContext, AssetUtil.INSTANCE.getCheckerDestFile());
                    loadNativeLibs(appContext, AssetUtil.INSTANCE.getDexkitDestFile());

                    // 特殊版本处理 (如果使用 Hook 获取的版本)
                    if (VersionHook.hasVersion() && "10.7.26.8100".equals(alipayVersion.getVersionString())) {
                        HookUtil.INSTANCE.fuckAccounLimit(classLoader);
                        Log.runtime(TAG, "✅ 已对版本 10.7.26.8100 进行特殊处理");
                    }

if (VersionHook.hasVersion() && alipayVersion.getVersionString() != null) {
    String version = alipayVersion.getVersionString();
    
    // 正则表达式匹配：
    // 1. 主版本小于10的所有版本: [0-9]\.[0-9]+\.[0-9]+\.[0-9]+
    // 2. 10.0.x.x 到 10.5.x.x: 10\.[0-5]\.[0-9]+\.[0-9]+
    // 3. 10.6.0.x 到 10.6.58.x: 10\.6\.([0-9]|[0-4][0-9]|5[0-8])\.[0-9]+
    if (version.matches("^([0-9]\\.[0-9]+\\.[0-9]+\\.[0-9]+|10\\.[0-5]\\.[0-9]+\\.[0-9]+|10\\.6\\.([0-9]|[0-4][0-9]|5[0-8])\\.[0-9]+)$")) {
        // 启用SimplePageManager窗口监控
        SimplePageManager.INSTANCE.enableWindowMonitoring(classLoader);
        
        // 初始化CaptchaHandler
        Log.runtime(TAG, "✅ 开始初始化CaptchaHandler，版本: " + version);
        SimplePageManager.INSTANCE.addHandler(
                "com.alipay.mobile.nebulax.xriver.activity.XRiverActivity",
                new Captcha1Handler());
        SimplePageManager.INSTANCE.addHandler(
                "com.eg.android.AlipayGphone.AlipayLogin",
                new Captcha2Handler());
    } else {
        Log.debug(TAG, "当前支付宝版本 " + version + " 不支持自动滑块Hook");
    }
}

                    if (BuildConfig.DEBUG) {
                        try {
                            Log.runtime(TAG, "start service for debug rpc");
                            // 使用管理器，仅主进程启动并防重复
                            ModuleHttpServerManager.INSTANCE.startIfNeeded(
                                    8080,
                                    "ET3vB^#td87sQqKaY*eMUJXP",
                                    XposedEnv.processName,
                                    General.PACKAGE_NAME
                            );
                        } catch (Throwable e) {
                            Log.printStackTrace(TAG, "forward services started error: ", e);
                        }
                    }

                    super.afterHookedMethod(param);
                }
            });
        } catch (Exception e) {
            Log.printStackTrace(e);
        }

        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Log.runtime(TAG, "hook onResume after start");
                            String targetUid = getUserId();
                            Log.runtime(TAG, "onResume targetUid: " + targetUid);
                            if (targetUid == null) {
                                Log.record(TAG, "onResume:用户未登录");
                                Toast.INSTANCE.show("用户未登录");
                                return;
                            }
                            if (!init) {
                                if (initHandler(true)) {
                                    init = true;
                                }
                                Log.runtime(TAG, "initHandler success");
                                return;
                            }
                            String currentUid = UserMap.INSTANCE.getCurrentUid();
                            Log.runtime(TAG, "onResume currentUid: " + currentUid);
                            if (!targetUid.equals(currentUid)) {
                                if (currentUid != null) {
                                    initHandler(true);  // 重新初始化
                                    lastExecTime = 0;   // 重置执行时间，防止被间隔逻辑拦截
                                    Log.record(TAG, "用户已切换");
                                    Toast.INSTANCE.show("用户已切换");
                                    return;
                                }
                                HookUtil.INSTANCE.hookUser(classLoader);
                            }
                            if (offline) {
                                offline = false;
                                execHandler();
                                ((Activity) param.thisObject).finish();
                                Log.runtime(TAG, "Activity reLogin");
                            }
                            // 如果所有特殊情况都未命中，执行一次常规任务检查
                            execHandler();
                            Log.runtime(TAG, "hook onResume after end");
                        }
                    });
            Log.runtime(TAG, "hook login successfully");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "hook login err",t);
        }
        try {
            XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Service appService = (Service) param.thisObject;
                            if (!General.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                                return;
                            }



                            Log.runtime(TAG, "Service onCreate");
                            appContext = appService.getApplicationContext();
                            boolean isok = Detector.INSTANCE.isLegitimateEnvironment(appContext);
                            if (isok) {
                                Detector.INSTANCE.dangerous(appContext);
                                return;
                            }
                            try (DexKitBridge ignored = DexKitBridge.create(apkPath)) {
                                // Other use cases
                                Log.runtime(TAG, "hook dexkit successfully");
                            }
                            service = appService;
                            mainTask = MainTask.newInstance("MAIN_TASK", () -> {
                                // 使用 TaskLock 自动管理锁生命周期（重构：防止遗漏释放）
                                try (TaskLock ignored = new TaskLock()) {
                                    boolean isAlarmTriggered = alarmTriggeredFlag;
                                    if (isAlarmTriggered) {
                                        alarmTriggeredFlag = false; // Consume the flag
                                    }

                                    if (!init) {
                                        Log.record(TAG, "️🐣跳过执行-未初始化");
                                        return;
                                    }
                                    if (!Config.isLoaded()) {
                                        Log.record(TAG, "️⚙跳过执行-用户模块配置未加载");
                                        return;
                                    }

                                    if (isAlarmTriggered) {
                                        Log.record(TAG, "⏰ 开始新一轮任务 (定时任务触发)");
                                    } else {
                                        if (lastExecTime == 0) {
                                            Log.record(TAG, "▶️ 首次手动触发，开始运行");
                                        } else {
                                            if (BaseModel.Companion.getManualTriggerAutoSchedule().getValue()) {
                                                Log.record(TAG, "手动APP触发，已开启");
                                                TaskRunnerAdapter adapter = new TaskRunnerAdapter();
                                                adapter.run();
                                            }
                                            Log.record(TAG, "手动APP触发，已关闭");
                                            return;
                                        }
                                    }

                                    long currentTime = System.currentTimeMillis();

                                    // 获取最小执行间隔（2秒）
                                    final long MIN_EXEC_INTERVAL = 2000;
                                    // 计算距离上次执行的时间间隔
                                    long timeSinceLastExec = currentTime - lastExecTime;

                                    if (isAlarmTriggered && timeSinceLastExec < MIN_EXEC_INTERVAL) {
                                        Log.record(TAG, "⚠️ 定时任务触发间隔较短(" + timeSinceLastExec + "ms)，跳过执行，安排下次执行");
                                        ensureScheduler();
                                        SchedulerAdapter.scheduleDelayedExecution(BaseModel.Companion.getCheckInterval().getValue());
                                        return;
                                    }
                                    String currentUid = UserMap.INSTANCE.getCurrentUid();
                                    String targetUid = HookUtil.INSTANCE.getUserId(classLoader);
                                    if (targetUid == null || !targetUid.equals(currentUid)) {
                                        Log.record(TAG, "用户切换或为空，重新登录");
                                        reLogin();
                                        return;
                                    }
                                    lastExecTime = currentTime; // 更新最后执行时间
                                    // 方式1：直接使用数组转换
                                    TaskRunnerAdapter adapter = new TaskRunnerAdapter();
                                    adapter.run();
                                    scheduleNextExecutionInternal(lastExecTime);
                                } catch (IllegalStateException e) {
                                    Log.record(TAG, "⚠️ " + e.getMessage());
                                } catch (Exception e) {
                                    Log.record(TAG, "❌执行异常");
                                    Log.printStackTrace(TAG, e);
                                }
                            });
                            dayCalendar = Calendar.getInstance();
                            if (initHandler(true)) {
                                init = true;
                            }
                        }
                    }

            );
            Log.runtime(TAG, "hook service onCreate successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook service onCreate err");
            Log.printStackTrace(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Service service = (Service) param.thisObject;
                            if (!General.CURRENT_USING_SERVICE.equals(service.getClass().getCanonicalName()))
                                return;
                            Log.record(TAG, "支付宝前台服务被销毁");
                            Notify.updateStatusText("支付宝前台服务被销毁");
                            destroyHandler();
                            try {
                                fansirsqi.xposed.sesame.hook.server.ModuleHttpServerManager.INSTANCE.stopIfRunning();
                            } catch (Throwable ignore) {
                            }
                            restartByBroadcast();
                        }
                    });
        } catch (Throwable t) {
            Log.runtime(TAG, "hook service onDestroy err");
            Log.printStackTrace(TAG, t);
        }

        HookUtil.INSTANCE.hookOtherService(classLoader);

        hooked = true;
    }

    /**
     * 设置定时唤醒
     */
    private static void setWakenAtTimeAlarm() {
        setWakenAtTimeAlarmWithRetry(0);
    }

    /**
     * 设置定时唤醒（带重试机制）
     */
    private static void setWakenAtTimeAlarmWithRetry(int retryCount) {
        try {
            if (appContext == null) {
                if (retryCount < 3) {
                    final int currentRetry = retryCount + 1;
                    Log.runtime(TAG, "appContext 未初始化，延迟" + (currentRetry * 2) + "秒后重试 (第" + currentRetry + "次)");
                    if (mainHandler != null) {
                        mainHandler.postDelayed(() -> setWakenAtTimeAlarmWithRetry(currentRetry), currentRetry * 2000L);
                    }
                } else {
                    Log.error(TAG, "appContext 初始化超时，放弃设置定时任务");
                }
                return;
            }

            // 确保调度器已初始化
            ensureScheduler();

            List<String> wakenAtTimeList = BaseModel.Companion.getWakenAtTimeList().getValue();
            if (wakenAtTimeList != null && wakenAtTimeList.contains("-1")) {
                Log.record(TAG, "定时唤醒未开启");
                return;
            }

            // 清理旧唤醒任务
            unsetWakenAtTimeAlarm();
            // 设置0点唤醒
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            resetToMidnight(calendar);
            boolean success = SchedulerAdapter.scheduleWakeupAlarm(calendar.getTimeInMillis(), 0, true);
            if (success) {
                Log.record(TAG, "⏰ 设置0点定时任务成功");
            } else {
                Log.error(TAG, "⏰ 设置0点定时任务失败");
            }

            // 设置自定义时间点唤醒
            if (wakenAtTimeList != null && !wakenAtTimeList.isEmpty()) {
                Calendar nowCalendar = Calendar.getInstance();
                int successCount = 0;
                for (int i = 1, len = wakenAtTimeList.size(); i < len; i++) {
                    try {
                        String wakenAtTime = wakenAtTimeList.get(i);
                        Calendar wakenAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakenAtTime);
                        if (wakenAtTimeCalendar != null && wakenAtTimeCalendar.compareTo(nowCalendar) > 0) {
                            boolean customSuccess = SchedulerAdapter.scheduleWakeupAlarm(wakenAtTimeCalendar.getTimeInMillis(), i, false);
                            if (customSuccess) {
                                successCount++;
                                Log.record(TAG, "⏰ 设置定时任务成功: " + wakenAtTime);
                            }
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(TAG,"设置自定义唤醒时间失败:", e);
                    }
                }
                if (successCount > 0) {
                    Log.record(TAG, "⏰ 共设置了 " + successCount + " 个自定义定时任务");
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG,"setWakenAtTimeAlarm err:", e);
        }
    }

    /**
     * 取消所有定时任务
     */
    private static void unsetWakenAtTimeAlarm() {
        ensureScheduler();
        SchedulerAdapter.cancelAllWakeupAlarms();
        Log.record(TAG, "已取消所有定时任务");
    }

    private static synchronized Boolean initHandler(Boolean force) {
        try {
            if (init && !force) { // 已经初始化 & 非强制，直接跳过
                return true;
            }

            if (init) {
                destroyHandler(); // 重新初始化时销毁旧的handler
            }

            // 调度器确保可用
            ensureScheduler();

            Model.initAllModel(); // 在所有服务启动前装模块配置
            if (service == null) {
                Log.runtime(TAG, "⚠️ Service 未就绪，初始化将推迟到 Service 启动");
                return false;
            }

            if (force) {
                String userId = HookUtil.INSTANCE.getUserId(classLoader);
                if (userId == null) {
                    Log.record(TAG, "initHandler: 用户未登录");
                    Toast.INSTANCE.show("用户未登录");
                    return false;
                }

                HookUtil.INSTANCE.hookUser(classLoader);
                String startMsg = "芝麻粒-TK 开始初始化...";
                Log.record(TAG, startMsg);
                Log.record(TAG, "⚙️模块版本：" + modelVersion);
                Log.record(TAG, "📦应用版本：" + alipayVersion.getVersionString());
                Log.record(TAG, "📶网络类型：" + NetworkUtils.INSTANCE.getNetworkType());

                Config.load(userId); // 加载配置
                if (!Config.isLoaded()) {
                    Log.record(TAG, "用户模块配置加载失败");
                    Toast.INSTANCE.show("用户模块配置加载失败");
                    return false;
                }

                Notify.start(service);

                // 优化：使用纯协程调度，无需唤醒锁
                Log.record(TAG, "✅ 使用 AlarmManager 进行精确调度，任务执行时将使用唤醒锁");

                setWakenAtTimeAlarm();

                synchronized (rpcBridgeLock) {
                    if (BaseModel.Companion.getNewRpc().getValue()) {
                        rpcBridge = new NewRpcBridge();
                    } else {
                        rpcBridge = new OldRpcBridge();
                    }
                    rpcBridge.load();
                    rpcVersion = rpcBridge.getVersion();
                }

                //!!注意⚠️所有BaseModel相关配置需要在 Config.load(userId)//initHandler;之后获取才有意义！！否则都取的默认值
                if (BaseModel.Companion.getNewRpc().getValue() && BaseModel.Companion.getDebugMode().getValue()) {
                    HookUtil.INSTANCE.hookRpcBridgeExtension(classLoader, BaseModel.Companion.getSendHookData().getValue(), BaseModel.Companion.getSendHookDataUrl().getValue());
                    HookUtil.INSTANCE.hookDefaultBridgeCallback(classLoader);
                }

                // 注册 VIPHook handler，用于抓取蚂蚁庄园广告 referToken
                VIPHook.INSTANCE.registerRpcHandler("com.alipay.adexchange.ad.facade.xlightPlugin", paramsJson -> {
                    try {
                        // paramsJson 就是完整 RPC 数据
                        // 找 positionRequest → referInfo → referToken
                        JSONObject positionRequest = paramsJson.optJSONObject("positionRequest");
                        if (positionRequest == null) {
                            Log.error("VIPHook", "未找到 positionRequest");
                            return Unit.INSTANCE;
                        }

                        JSONObject referInfo = positionRequest.optJSONObject("referInfo");
                        if (referInfo == null) {
                            Log.error("VIPHook", "未找到 referInfo");
                            return Unit.INSTANCE;
                        }

                        String token = referInfo.optString("referToken", "");
                        if (token.isEmpty()) {
                            Log.error("VIPHook", "referToken 为空");
                            return Unit.INSTANCE;
                        }

                        // 取得当前用户 UID
                        String userId1 = UserMap.INSTANCE.getCurrentUid();
                        if (userId1 == null || userId1.isEmpty()) {
                            Log.error("VIPHook", "无法保存 referToken：当前用户ID为空");
                            return Unit.INSTANCE;
                        }

                        // --- 与你的 fishpond riskToken 完全一样的保存逻辑 ---
                        VipDataIdMap vipData = IdMapManager.getInstance(VipDataIdMap.class);
                        vipData.load(userId1);

                        // 存储键名：AntFarmReferToken
                        vipData.add("AntFarmReferToken", token);

                        boolean saved = vipData.save(userId1);
                        if (saved) {
                            Log.other("VIPHook", "捕获到蚂蚁庄园 referToken 并已保存到 vipdata.json, uid=" + userId1);
                        } else {
                            Log.error("VIPHook", "保存 vipdata.json 失败, uid=" + userId1);
                        }

                    } catch (Exception e) {
                        Log.error("VIPHook", "解析 referToken 失败: " + e.getMessage());
                    }

                    return Unit.INSTANCE;
                });

                // 后台运行权限检查!!
                if (General.PACKAGE_NAME.equals(finalProcessName) && !batteryPermissionChecked) {
                    if (BaseModel.Companion.getBatteryPerm().getValue() && !PermissionUtil.checkBatteryPermissions()) {
                        Log.record(TAG, "支付宝无始终在后台运行权限,准备申请");
                        mainHandler.postDelayed(
                                () -> {
                                    if (!PermissionUtil.checkOrRequestBatteryPermissions(appContext)) {
                                        Toast.INSTANCE.show("请授予支付宝始终在后台运行权限");
                                    }
                                },
                                2000);
                    }
                    batteryPermissionChecked = true;
                }

                Model.bootAllModel(classLoader);
                Status.load(userId);

                updateDay();
                String successMsg = "芝麻粒-TK 加载成功✨";
                Log.record(successMsg);
                Toast.INSTANCE.show(successMsg);
            }
            offline = false;
            init = true;
            // 首次初始化后，立即执行一次任务并调度下次执行
            execHandler();
            return true;
        } catch (Throwable th) {
            Log.printStackTrace(TAG, "startHandler", th);
            Toast.INSTANCE.show("芝麻粒加载失败 🎃");
            return false;
        }
    }


    /**
     * 销毁处理程序
     */
    static synchronized void destroyHandler() {
        try {
            GlobalThreadPools.INSTANCE.shutdownAndRestart();
            if (service != null) {
                stopHandler();
                BaseModel.Companion.destroyData();
                Status.unload();
                Notify.stop();
                RpcIntervalLimit.INSTANCE.clearIntervalLimit();
                Config.unload();
                UserMap.unload();
            }
            // 协程调度器会自动清理，无需手动释放唤醒锁
            synchronized (rpcBridgeLock) {
                if (rpcBridge != null) {
                    rpcVersion = null;
                    rpcBridge.unload();
                    rpcBridge = null;
                }
                ModelTask.stopAllTask();
            }
        } catch (Throwable th) {
            Log.printStackTrace(TAG, "stopHandler err:", th);
        }
    }

    static void execHandler() {
        mainTask.startTask(false);// 非强制执行，避免重复排队
    }

    /**
     * 检查长时间未执行的情况，如果超过阈值则自动重启
     * 特别针对0点后可能出现的执行中断情况
     */
    private static void checkInactiveTime() {
        try {
            if (lastExecTime == 0) {
                return; // 首次执行，跳过检查
            }
            long currentTime = System.currentTimeMillis();
            long inactiveTime = currentTime - lastExecTime;
            // 检查是否经过了0点
            Calendar lastExecCalendar = Calendar.getInstance();
            lastExecCalendar.setTimeInMillis(lastExecTime);
            Calendar currentCalendar = Calendar.getInstance();
            currentCalendar.setTimeInMillis(currentTime);
            boolean crossedMidnight = lastExecCalendar.get(Calendar.DAY_OF_YEAR) != currentCalendar.get(Calendar.DAY_OF_YEAR) ||
                    lastExecCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR);
            // 如果超过最大不活动时间或者跨越了0点但已经过了一段时间
            if (inactiveTime > MAX_INACTIVE_TIME || (crossedMidnight && currentCalendar.get(Calendar.HOUR_OF_DAY) >= 1)) {
                Log.record(TAG, "⚠️ 检测到长时间未执行(" + (inactiveTime / 60000) + "分钟)，可能跨越0点，尝试重新登录");
                reLogin();
            }
        } catch (Exception e) {
            Log.runtime(TAG, "checkInactiveTime err:" + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }

    private static void stopHandler() {
        mainTask.stopTask();
        ModelTask.stopAllTask();
    }

    public static void updateDay() {
        Calendar nowCalendar = Calendar.getInstance();
        try {
            if (dayCalendar == null) {
                dayCalendar = (Calendar) nowCalendar.clone();
                resetToMidnight(dayCalendar);
                Log.record(TAG, "初始化日期为：" + dayCalendar.get(Calendar.YEAR) + "-" + (dayCalendar.get(Calendar.MONTH) + 1) + "-" + dayCalendar.get(Calendar.DAY_OF_MONTH));
                setWakenAtTimeAlarm();
                return;
            }

            int nowYear = nowCalendar.get(Calendar.YEAR);
            int nowMonth = nowCalendar.get(Calendar.MONTH);
            int nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH);
            if (dayCalendar.get(Calendar.YEAR) != nowYear || dayCalendar.get(Calendar.MONTH) != nowMonth || dayCalendar.get(Calendar.DAY_OF_MONTH) != nowDay) {
                dayCalendar = (Calendar) nowCalendar.clone();
                resetToMidnight(dayCalendar);
                Log.record(TAG, "日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
                setWakenAtTimeAlarm();
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }

        try {
            Status.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }


    /**
     * 通用广播发送方法（重构：消除重复代码）
     *
     * @param action   广播动作
     * @param errorMsg 错误日志消息
     */
    public static void sendBroadcast(String action, String errorMsg) {
        try {
            appContext.sendBroadcast(new Intent(action));
        } catch (Throwable th) {
            Log.runtime(TAG, errorMsg);
            Log.printStackTrace(TAG, th);
        }
    }
    /**
     * 发送广播到其他应用（显式广播）
     * @param message 要发送的字符串消息
     */
    public static void sendBroadcastShell(String API,String message) {
        Intent intent = new Intent("fansirsqi.xposed.sesame.SHELL");
        intent.putExtra(API, message);
        appContext.sendBroadcast(intent,null);
    }
    /**
     * 通过广播发送重新登录的指令
     */
    public static void reLoginByBroadcast() {
        sendBroadcast(BroadcastActions.RE_LOGIN, "sesame sendBroadcast reLogin err:");
    }

    /**
     * 通过广播发送重启模块服务的指令
     */
    public static void restartByBroadcast() {
        sendBroadcast(BroadcastActions.RESTART, "发送重启广播时出错:");
    }


    /**
     * 工具方法：将 Calendar 重置为当天午夜 0 点
     * 重构：消除重复代码
     *
     * @param calendar 要重置的 Calendar 对象
     */
    private static void resetToMidnight(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    /**
     * 获取支付宝微应用上下文（优化：使用反射缓存）
     */
    public static Object getMicroApplicationContext() {
        if (microApplicationContextObject == null) {
            try {
                Class<?> alipayApplicationClass = ReflectionCache.getAlipayApplicationClass(classLoader);
                if (alipayApplicationClass == null) {
                    Log.runtime(TAG, "⚠️ 无法获取 AlipayApplication 类");
                    return null;
                }

                Object alipayApplicationInstance = XposedHelpers.callStaticMethod(
                        alipayApplicationClass, "getInstance"
                );
                if (alipayApplicationInstance == null) {
                    return null;
                }
                microApplicationContextObject = XposedHelpers.callMethod(
                        alipayApplicationInstance, "getMicroApplicationContext"
                );
            } catch (Throwable t) {
                Log.printStackTrace(t);
            }
        }
        return microApplicationContextObject;
    }

    /**
     * 获取服务对象
     */
    public static Object getServiceObject(String service) {
        try {
            return XposedHelpers.callMethod(getMicroApplicationContext(), "findServiceByInterface", service);
        } catch (Throwable th) {
            Log.runtime(TAG, "getServiceObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    /**
     * 获取用户对象（优化：使用反射缓存）
     */
    public static Object getUserObject() {
        try {
            Class<?> socialSdkClass = ReflectionCache.getSocialSdkClass(classLoader);
            if (socialSdkClass == null) {
                Log.runtime(TAG, "⚠️ 无法获取 SocialSdkContactService 类");
                return null;
            }

            return XposedHelpers.callMethod(
                    getServiceObject(socialSdkClass.getName()),
                    "getMyAccountInfoModelByLocal");
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    /**
     * 获取用户ID
     */
    public static String getUserId() {
        try {
            Object userObject = getUserObject();
            if (userObject != null) {
                return (String) XposedHelpers.getObjectField(userObject, "userId");
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserId err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    public static void reLogin() {
        mainHandler.post(
                () -> {
                    long delayMillis;
                    if (reLoginCount.get() < 5) {
                        delayMillis = reLoginCount.getAndIncrement() * 5000L;
                    } else {
                        delayMillis = Math.max(BaseModel.Companion.getCheckInterval().getValue(), 180_000);
                    }
                    Log.record("TAG", "🔄 准备重新登录，延迟 " + (delayMillis / 1000) + " 秒后执行");
                    // 使用调度器（协程或 WorkManager）
                    ensureScheduler();
                    SchedulerAdapter.scheduleDelayedExecution(delayMillis);

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setClassName(General.PACKAGE_NAME, General.CURRENT_USING_ACTIVITY);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    offline = true;
                    appContext.startActivity(intent);
                });
    }


    static class AlipayBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                Log.runtime(TAG, "Alipay got Broadcast " + action + " intent:" + intent);
                if (action != null) {
                    switch (action) {
                        case BroadcastActions.RESTART:
                            Log.printStack(TAG);
                            GlobalThreadPools.INSTANCE.execute(() -> initHandler(true));
                            break;
                        case BroadcastActions.EXECUTE:
                            Log.printStack(TAG);
                            if (intent.getBooleanExtra("alarm_triggered", false)) {
                                alarmTriggeredFlag = true;
                                Log.record(TAG, "⏰ 收到定时任务触发广播 (闹钟调度器)EXECUTE");
                                WakeLockManager.INSTANCE.acquire(context, 600_000L); // 获取10分钟的唤醒锁
                            }
                            // 如果已初始化，直接执行任务；否则先初始化
                            if (init) {
                                Log.record(TAG, "✅ 模块已初始化，开始执行任务EXECUTE");
                                execHandler();
                            } else {
                                // Service 已就绪，可以初始化
                                Log.record(TAG, "⚠️ 模块未初始化，开始初始化流程EXECUTE");
                                GlobalThreadPools.INSTANCE.execute(() -> {
                                    if (initHandler(true)) {
                                        Log.record(TAG, "✅ 初始化成功，开始执行任务EXECUTE");
                                        execHandler();
                                    }
                                });
                            }
                            break;
                        case BroadcastActions.PRE_WAKEUP:
                            Log.record(TAG, "⏰ 收到唤醒广播，准备执行任务PRE_WAKEUP");
                            WakeLockManager.INSTANCE.acquire(context, 120_000L); // 2 minute wakelock
                            alarmTriggeredFlag = true;

                            // 立即执行，不再延迟
                            if (init) {
                                execHandler();
                            } else {
                                Log.record(TAG, "⚠️ 模块未初始化，开始初始化流程PRE_WAKEUP");
                                GlobalThreadPools.INSTANCE.execute(() -> {
                                    if (initHandler(false)) {
                                        Log.record(TAG, "✅ 初始化成功，开始执行任务PRE_WAKEUP");
                                        execHandler();
                                    }
                                });
                            }
                            break;
                        case BroadcastActions.RE_LOGIN:
                            Log.printStack(TAG);
                            GlobalThreadPools.INSTANCE.execute(ApplicationHook::reLogin);
                            break;
                        case BroadcastActions.STATUS:
                            // 状态查询处理
                            Log.printStack(TAG);
                            if (ViewAppInfo.getRunType() == RunType.DISABLE) {
                                Intent replyIntent = new Intent("fansirsqi.xposed.sesame.status");
                                replyIntent.putExtra("EXTRA_RUN_TYPE", RunType.ACTIVE.getNickName());
                                replyIntent.setPackage(General.MODULE_PACKAGE_NAME);
                                context.sendBroadcast(replyIntent);
                                Log.system(TAG, "Replied with status: " + RunType.ACTIVE.getNickName());
                            }
                            break;
                        case BroadcastActions.RPC_TEST:
                            GlobalThreadPools.INSTANCE.execute(() -> {
                                try {
                                    String method = intent.getStringExtra("method");
                                    String data = intent.getStringExtra("data");
                                    String type = intent.getStringExtra("type");
                                    Log.runtime(TAG, "收到RPC测试请求 - Method: " + method + ", Type: " + type);
                                    DebugRpc rpcInstance = new DebugRpc();
                                    rpcInstance.start(method, data, type);
                                } catch (Throwable th) {
                                    Log.runtime(TAG, "sesame 测试RPC请求失败:");
                                    Log.printStackTrace(TAG, th);
                                }
                            });
                            break;
                        default:
                            // 协程调度器会自动处理任务触发，无需额外处理
                            Log.debug(TAG, "收到未知广播: " + action);
                            break;
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, "AlipayBroadcastReceiver.onReceive err:", t);
            }
        }
    }

    /**
     * 注册广播接收器以监听支付宝相关动作。
     *
     * @param context 应用程序上下文
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    // 忽略Lint关于注册广播接收器时未指定导出属性的警告
    void registerBroadcastReceiver(Context context) {
        //创建一个IntentFilter实例，用于过滤出我们需要捕获的广播
        try {
            IntentFilter intentFilter = getIntentFilter();
            // 根据Android SDK版本注册广播接收器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 在Android 13及以上版本，注册广播接收器并指定其可以被其他应用发送的广播触发
                context.registerReceiver(new AlipayBroadcastReceiver(), intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                // 在Android 13以下版本，注册广播接收器
                context.registerReceiver(new AlipayBroadcastReceiver(), intentFilter);
            }
            // 记录成功注册广播接收器的日志
            Log.runtime(TAG, "hook registerBroadcastReceiver successfully");
        } catch (Throwable th) {
            // 记录注册广播接收器失败的日志
            Log.runtime(TAG, "hook registerBroadcastReceiver err:");
            // 打印异常堆栈信息
            Log.printStackTrace(TAG, th);
        }
    }

    @NonNull
    private static IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BroadcastActions.RESTART); // 重启支付宝服务的动作
        intentFilter.addAction(BroadcastActions.EXECUTE); // 执行特定命令的动作
        intentFilter.addAction(BroadcastActions.PRE_WAKEUP); // 预唤醒
        intentFilter.addAction(BroadcastActions.RE_LOGIN); // 重新登录支付宝的动作
        intentFilter.addAction(BroadcastActions.STATUS); // 查询支付宝状态的动作
        intentFilter.addAction(BroadcastActions.RPC_TEST); // 调试RPC的动作
        return intentFilter;
    }



}

