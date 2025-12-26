package fansirsqi.xposed.sesame.hook;

import android.content.pm.PackageInfo;
import androidx.core.content.pm.PackageInfoCompat;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.util.Log;
import lombok.Getter;

/**
 * 版本号 Hook 工具类
 * 用于在应用启动早期拦截并获取支付宝版本信息
 */
public class VersionHook {
    private static final String TAG = "VersionHook";

    /**
     * -- GETTER --
     *  获取已捕获的版本信息
     *
     */
    // 缓存捕获的版本信息
    @Getter
    private static volatile AlipayVersion capturedVersion = null;
    private static volatile boolean hookInstalled = false;

    /**
     * 在 loadPackage 阶段尽早安装 Hook
     *
     * @param classLoader 类加载器
     */
    public static void installHook(ClassLoader classLoader) {
        // 防止重复安装
        if (hookInstalled) {
            Log.runtime(TAG, "⚠️ Hook 已安装,跳过");
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageInfo",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                PackageInfo packageInfo = (PackageInfo) param.getResult();

                                // 只处理支付宝的包信息
                                if (packageInfo != null &&
                                        General.PACKAGE_NAME.equals(packageInfo.packageName)) {

                                    String versionName = packageInfo.versionName;
                                    long longVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
                                    int versionCode = (int) (longVersionCode);

                                    // 只在第一次捕获时记录日志
                                    if (capturedVersion == null && versionName != null) {
                                        capturedVersion = new AlipayVersion(versionName);
                                        Log.runtime(TAG, "✅ 捕获支付宝版本: " + versionName +
                                                " (code: " + versionCode +
                                                ", longCode: " + longVersionCode + ")");
                                    }
                                }
                            } catch (Throwable t) {
                                // 静默处理异常,避免影响应用正常运行
                                Log.printStackTrace(TAG, t);
                            }
                        }
                    }
            );

            hookInstalled = true;
            Log.runtime(TAG, "✅ 版本号 Hook 安装成功");

        } catch (Throwable t) {
            Log.runtime(TAG, "❌ 安装版本号 Hook 失败");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 检查是否已成功捕获版本号
     *
     * @return true: 已捕获, false: 未捕获
     */
    public static boolean hasVersion() {
        return capturedVersion != null;
    }

    /**
     * 重置捕获状态 (用于测试或重新初始化)
     */
    public static void reset() {
        capturedVersion = null;
        hookInstalled = false;
        Log.runtime(TAG, "🔄 版本号 Hook 状态已重置");
    }
}