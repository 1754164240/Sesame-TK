package fansirsqi.xposed.sesame.newui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider


@Composable
fun DeviceInfoCard(info: Map<String, String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            info.forEach { (label, value) ->
                when (label) {
                    "Verify ID" -> {
                        var showFull by remember { mutableStateOf(false) }
                        val displayValue = if (showFull) value else "***********"
                        val context = LocalContext.current
                        Text(
                            text = "$label: $displayValue",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { showFull = !showFull }
                                .combinedClickable(
                                    onClick = { showFull = !showFull },
                                    onLongClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Android ID", value)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Verify ID copied", Toast.LENGTH_SHORT).show()
                                    }
                                )
                        )
                    }

                    else -> {
                        Text(text = "$label: $value", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

object DeviceInfoUtil {

    private const val TAG = "DeviceInfoUtil"
    private const val TIMEOUT_MS = 10000L
    private const val ACTION_BIND = "fansirsqi.xposed.sesame.action.BIND_COMMAND_SERVICE"

    private var commandService: ICommandService? = null
    private var isBound = false

    // 修复：使用可空的 Deferred，每次绑定时重新创建，防止状态复用 BUG
    private var connectionDeferred: CompletableDeferred<Unit>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "CommandService 已连接")
            commandService = ICommandService.Stub.asInterface(service)
            isBound = true
            // 只有当 Deferred 存在且未完成时才完成它
            connectionDeferred?.let {
                if (it.isActive) it.complete(Unit)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "CommandService 已断开")
            commandService = null
            isBound = false
        }
    }

    private suspend fun bindService(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isBound && commandService != null) {
            return@withContext true
        }

        try {
            // 每次尝试绑定前重置 Deferred
            connectionDeferred = CompletableDeferred()

            val intent = Intent(ACTION_BIND)
            intent.setPackage("fansirsqi.xposed.sesame")
            // 使用 Application Context 绑定，防止 Activity 泄露
            val result = context.applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            if (!result) {
                return@withContext false
            }

            // 等待连接
            val connected = withTimeoutOrNull(5000) {
                connectionDeferred?.await()
            }
            connected != null
        } catch (e: Exception) {
            Log.e(TAG, "绑定服务失败: ${e.message}")
            false
        }
    }

    // 执行命令的核心方法
    private suspend fun execCommand(context: Context, command: String): String = withContext(Dispatchers.IO) {
        if (!bindService(context)) return@withContext ""
        val service = commandService ?: return@withContext ""

        val deferred = CompletableDeferred<String>()
        val callback = object : ICallback.Stub() {
            override fun onSuccess(output: String) {
                deferred.complete(output)
            }

            override fun onError(error: String) {
                Log.e(TAG, "CMD Error: $error")
                deferred.complete("")
            }
        }

        try {
            service.executeCommand(command, callback)
            withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 辅助方法：检查 Shizuku 服务状态（仅用于 UI 显示辅助）
     */
    private fun isShizukuAvailable(context: Context): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            // 使用你 MainActivity 里申请过的权限检查
            context.checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun showInfo(vid: String, context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        // 1. 获取设备属性的方法
        suspend fun getProp(prop: String): String {
            return execCommand(context, "getprop $prop").trim()
        }

        suspend fun getDeviceName(): String {
            val candidates = listOf("ro.product.marketname", "ro.product.model")
            for (prop in candidates) {
                val value = getProp(prop)
                if (value.isNotBlank()) return value
            }
            return "${Build.BRAND} ${Build.MODEL}"
        }

        // 2. 关键修改：通过执行 id 命令来判断当前使用的是什么权限
        val idOutput = execCommand(context, "id")

        // 3. 判断 Shizuku 服务是否可用（辅助信息）
        val shizukuAvailable = isShizukuAvailable(context)

        // 4. 生成权限状态字符串
        val permissionStatus = when {
            // 如果 id 命令返回 uid=2000 或 shell，说明正在使用 Shizuku
            idOutput.contains("uid=2000") || idOutput.contains("shell") -> "Shizuku (Shell) ✓"
            // 否则就是没权限
            else -> {
                if (shizukuAvailable) "Shizuku  ✔" else "未授权滑块服务 ❌"
            }
        }

        mapOf(
            "Product" to "${Build.MANUFACTURER} ${Build.PRODUCT}",
            "Device" to getDeviceName(),
            "Android Version" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Verify ID" to vid,
            "Captcha Permission" to permissionStatus,
            "Module Version" to "v${BuildConfig.VERSION_NAME}",
            "Module Build" to "${BuildConfig.BUILD_DATE} ${BuildConfig.BUILD_TIME}"
        )
    }
}
