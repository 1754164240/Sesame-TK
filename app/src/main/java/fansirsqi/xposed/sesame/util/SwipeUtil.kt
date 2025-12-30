package fansirsqi.xposed.sesame.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking

/**
 * 滑动操作工具类
 * 通过 AIDL 调用 CommandService 执行 Shizuku 命令
 */
object SwipeUtil {

    private const val TAG = "SwipeUtil"

    /**
     * 使用shell命令启动支付宝，支持自动获取用户ID
     * 首先尝试不带用户ID的启动命令，失败后备份到带用户ID的命令，
     * 最后回退到scheme启动方式
     */
    @JvmStatic
    fun startAlipayWithShellCommand(context: Context): Boolean {
        return runBlocking {
            var launchSuccess = false
            try {
                val firstCommand = "am start com.eg.android.AlipayGphone/com.eg.android.AlipayGphone.AlipayLogin"
                val firstResult = CommandUtil.executeCommand(context, firstCommand)
                if (firstResult != null) {
                    launchSuccess = true
                } else {
                    Log.d(TAG, "不带用户ID启动失败，尝试带用户ID的启动命令")
                    val userId = "999"
                    val fallbackCommand = "am start --user $userId com.eg.android.AlipayGphone/com.eg.android.AlipayGphone.AlipayLogin"
                    val fallbackResult = CommandUtil.executeCommand(context, fallbackCommand)
                    if (fallbackResult != null) {
                        launchSuccess = true
                    } else {
                        Log.d(TAG, "带用户ID启动失败，尝试Intent启动")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "执行支付宝启动命令失败: ${e.message}")
            }
            if (!launchSuccess) {
                Log.d(TAG, "shell命令启动失败，尝试Intent启动")
                launchSuccess = startByIntent(context)
            }
            if (!launchSuccess) {
                Log.d(TAG, "Intent启动失败，回退到scheme启动方式")
                return@runBlocking startBySchemeSync(context)
            }
            true
        }
    }

    @JvmStatic
    fun startByIntent(context: Context): Boolean {
        return try {
            val intent = Intent()
            intent.component = android.content.ComponentName(
                "com.eg.android.AlipayGphone",
                "com.eg.android.AlipayGphone.AlipayLogin"
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Intent 启动成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Intent 启动失败: ${e.message}")
            false
        }
    }

    @JvmStatic
    fun startBySchemeSync(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, "alipays://platformapi/startapp?appId=".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Scheme 启动成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Scheme 启动失败: ${e.message}")
            false
        }
    }
}