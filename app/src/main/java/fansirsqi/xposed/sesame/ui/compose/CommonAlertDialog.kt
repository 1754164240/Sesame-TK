package fansirsqi.xposed.sesame.ui.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CommonAlertDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    text: String,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary, // 默认图标颜色
    confirmText: String = "确认",
    dismissText: String = "取消",
    confirmButtonColor: Color = MaterialTheme.colorScheme.primary // 默认按钮颜色
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            icon = icon?.let {
                { Icon(it, contentDescription = null, tint = iconTint) }
            },
            title = {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                        onDismissRequest() // 点击确认后自动关闭
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = confirmButtonColor)
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(dismissText)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}