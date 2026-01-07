package fansirsqi.xposed.sesame.newui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import fansirsqi.xposed.sesame.util.CommandUtil
import fansirsqi.xposed.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DeviceInfoCard(info: Map<String, String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)//阴影
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
        ) {
            info.forEach { (label, value) ->
                when (label) {
                    "Verify ID" -> {
                        var showFull by remember { mutableStateOf(false) }
                        val displayValue = if (showFull) value else "**********************"
                        val context = LocalContext.current
                        Text(
                            text = "$label: $displayValue",
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { showFull = !showFull },
                                    onLongClick = {
                                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Verify ID", value)
                                        clipboardManager.setPrimaryClip(clip)
                                        ToastUtil.showToast("Verify ID copied")
                                    }
                                )
                        )
                    }

                    else -> {
                        Text(text = "$label: $value", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}

object DeviceInfoUtil {

    private suspend fun getProp(context: Context, prop: String): String {
        return CommandUtil.executeCommand(context, "getprop $prop")?.trim() ?: ""
    }


    private suspend fun getDeviceName(context: Context): String {
        val candidates = listOf("ro.product.marketname", "ro.product.model")
        for (prop in candidates) {
            val value = getProp(context, prop)
            if (value.isNotBlank()) return value
        }
        return "${Build.BRAND} ${Build.MODEL}"
    }

    private suspend fun getSn(context: Context): String {
        return getProp(context, "ro.serialno")
    }

    suspend fun showInfo(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val currentShellType = CommandUtil.getShellType(context)

        val permissionStatus = when (currentShellType) {
            "RootShell" -> "Root ✓"
            "ShizukuShell" -> "Shizuku (Shell) ✓"
            "UserShell" -> "普通用户权限(正常使用) ✓"
            null, "no_executor" -> "未授权滑块服务 ❌"
            else -> "未知 ❌"
        }

        mapOf(
            "Product" to "${Build.MANUFACTURER} ${Build.PRODUCT}",
            "Device" to getDeviceName(context),
            "Android Version" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Verify ID" to getSn(context),
            "Captcha Permission" to permissionStatus,
            "Module Version" to "v${BuildConfig.VERSION_NAME} ${BuildConfig.VERSION_CODE}",
            "Module Build" to "${BuildConfig.BUILD_DATE} ${BuildConfig.BUILD_TIME}"
        )
    }
}
