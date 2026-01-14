package fansirsqi.xposed.sesame.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.SesameApplication.Companion.hasPermissions
import fansirsqi.xposed.sesame.SesameApplication.Companion.preferencesKey
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.newui.DeviceInfoCard
import fansirsqi.xposed.sesame.newui.WatermarkLayer
import fansirsqi.xposed.sesame.newutil.IconManager
import fansirsqi.xposed.sesame.ui.compose.CommonAlertDialog
import fansirsqi.xposed.sesame.ui.log.LogViewerComposeActivity
import fansirsqi.xposed.sesame.ui.theme.AppTheme
import fansirsqi.xposed.sesame.util.CommandUtil
import fansirsqi.xposed.sesame.util.Detector
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.PermissionUtil
import fansirsqi.xposed.sesame.util.ToastUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Shizuku 监听器
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1234) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ToastUtil.showToast(this, "Shizuku 授权成功！")

                // 关键修改：
                lifecycleScope.launch {
                    CommandUtil.executeCommand(this@MainActivity, "echo init_shizuku")
                    delay(200)
                    viewModel.refreshDeviceInfo(this@MainActivity)
                }
            } else {
                ToastUtil.showToast(this, "Shizuku 授权被拒绝")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // 2. 检查权限并初始化逻辑
        hasPermissions = PermissionUtil.checkOrRequestFilePermissions(this)
        if (hasPermissions) {
            viewModel.initAppLogic()
            initNativeDetector()
        }

        // 3. 初始化 Shizuku
        setupShizuku()

        // 4. 同步图标状态
        val prefs = getSharedPreferences(preferencesKey, MODE_PRIVATE)
        IconManager.syncIconState(this, prefs.getBoolean("is_icon_hidden", false))

        // 5. 设置 Compose 内容
        setContent {
            // 收集 ViewModel 状态
            val oneWord by viewModel.oneWord.collectAsStateWithLifecycle()
            val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()

            val moduleStatus by viewModel.moduleStatus.collectAsStateWithLifecycle()


            //  获取实时的 UserEntity 列表
            val userList by viewModel.userList.collectAsStateWithLifecycle()
            // 使用 derivedStateOf 优化性能，只在 userList 变化时重新映射
            val uidList by remember {
                derivedStateOf { userList.map { it.userId } }
            }


            // AppTheme 会处理状态栏颜色
            AppTheme {
                WatermarkLayer(
                    uidList = uidList
                ) {
                    MainScreen(
                        oneWord = oneWord,
                        activeUserName = activeUser?.showName ?: "未载入",
                        moduleStatus = moduleStatus,
                        viewModel = viewModel,
                        userList = userList, // 传入列表
                        // 🔥 处理跳转逻辑
                        onNavigateToSettings = { selectedUser ->
                            performNavigationToSettings(selectedUser)
                        },
                        onEvent = { event -> handleEvent(event) }
                    )
                }
            }
        }
    }

    // 在 Activity 中执行 Native 检测
    private fun initNativeDetector() {
        try {
            if (Detector.loadLibrary("checker")) {
                Detector.initDetector(this)
            }
        } catch (e: Exception) {
            Log.error("MainActivity", "Native detector init failed: ${e.message}")
        }
    }

    /**
     * 定义 UI 事件
     */
    sealed class MainUiEvent {
        data object RefreshOneWord : MainUiEvent()
        data object OpenForestLog : MainUiEvent()
        data object OpenFarmLog : MainUiEvent()
        data object OpenGithub : MainUiEvent()
        data object OpenErrorLog : MainUiEvent()
        data object OpenOtherLog : MainUiEvent()
        data object OpenAllLog : MainUiEvent()
        data object OpenDebugLog : MainUiEvent()
        data class ToggleIconHidden(val isHidden: Boolean) : MainUiEvent()
        data object OpenCaptureLog : MainUiEvent()
        data object OpenExtend : MainUiEvent()
        data object ClearConfig : MainUiEvent()
    }

    /**
     * 统一处理事件
     */
    private fun handleEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.RefreshOneWord -> viewModel.fetchOneWord()
            MainUiEvent.OpenForestLog -> openLogFile(Files.getForestLogFile())
            MainUiEvent.OpenFarmLog -> openLogFile(Files.getFarmLogFile())
            MainUiEvent.OpenOtherLog -> openLogFile(Files.getOtherLogFile())
            MainUiEvent.OpenGithub -> openUrl("https://github.com/Fansirsqi/Sesame-TK")
            MainUiEvent.OpenErrorLog -> executeWithVerification { openLogFile(Files.getErrorLogFile()) }
            MainUiEvent.OpenAllLog -> openLogFile(Files.getRecordLogFile())
            MainUiEvent.OpenDebugLog -> openLogFile(Files.getDebugLogFile())
            is MainUiEvent.ToggleIconHidden -> {
                val shouldHide = event.isHidden
                getSharedPreferences(preferencesKey, MODE_PRIVATE).edit { putBoolean("is_icon_hidden", shouldHide) }
                viewModel.syncIconState(shouldHide)
                Toast.makeText(this, "设置已保存，可能需要重启桌面才能生效", Toast.LENGTH_SHORT).show()
            }

            MainUiEvent.OpenCaptureLog -> openLogFile(Files.getCaptureLogFile())
            MainUiEvent.OpenExtend -> startActivity(Intent(this, ExtendActivity::class.java))
            MainUiEvent.ClearConfig -> {
                // 🔥 这里只负责执行逻辑，不再负责弹窗
                if (Files.delFile(Files.CONFIG_DIR)) {
                    ToastUtil.showToast(this, "🙂 清空配置成功")
                    // 可选：重载配置或刷新 UI
                    viewModel.reloadUserConfigs()
                } else {
                    ToastUtil.showToast(this, "😭 清空配置失败")
                }
            }
        }
    }

    // --- 辅助方法 (替代 BaseActivity) ---

    private fun setupShizuku() {
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        if (Shizuku.pingBinder() && checkSelfPermission(ShizukuProvider.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1234)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions) viewModel.reloadUserConfigs()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }

    private fun openLogFile(logFile: File) {
        if (!logFile.exists()) {
            ToastUtil.showToast(this, "日志文件不存在: ${logFile.name}")
            return
        }
        val intent = Intent(this, LogViewerComposeActivity::class.java).apply {
            data = logFile.toUri()
        }
        startActivity(intent)
    }

    private fun executeWithVerification(block: () -> Unit) {
        // 如果需要生物识别验证，可以在这里添加逻辑
        // 目前直接执行
        block()
    }


}

// ====================================================================================
// Composable 组件部分 (保持不变，直接复制使用)
// ====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusCard(
    status: MainViewModel.ModuleStatus,
    expanded: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor =
                when (status) {
                    is MainViewModel.ModuleStatus.Activated -> MaterialTheme.colorScheme.secondaryContainer
                    is MainViewModel.ModuleStatus.NotActivated -> MaterialTheme.colorScheme.errorContainer
                    is MainViewModel.ModuleStatus.Loading -> MaterialTheme.colorScheme.surfaceVariant
                }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    is MainViewModel.ModuleStatus.Activated -> {
                        Icon(Icons.Outlined.CheckCircle, "已激活")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "Activated", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Version: ${BuildConfig.VERSION_NAME} ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "by ${status.frameworkName} ${status.frameworkVersion} API ${status.apiVersion}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is MainViewModel.ModuleStatus.NotActivated -> {
                        Icon(Icons.Outlined.Warning, "未激活")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "如果你是非root用户,请忽略此状态", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "点击展开帮助", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    is MainViewModel.ModuleStatus.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "正在检查模块状态...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(text = "故障排查指南", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请确认您已在 LSPosed Manager (或类似框架) 中：\n1. 启用了本模块。\n2. 在作用域中勾选了支付宝。\n3. 重启了支付宝进程。",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    oneWord: String,
    activeUserName: String,
    moduleStatus: MainViewModel.ModuleStatus,
    viewModel: MainViewModel,
    userList: List<UserEntity>, // 🔥 确保 userList 被传入 MainScreen
    onNavigateToSettings: (UserEntity) -> Unit, // 🔥 新增回调：跳转设置
    onEvent: (MainActivity.MainUiEvent) -> Unit,
) {
    // 状态卡展开状态
    var isStatusCardExpanded by remember { mutableStateOf(false) }
    // 获取上下文
    val context = LocalContext.current
    // 获取 isOneWordLoading
    val isOneWordLoading by viewModel.isOneWordLoading.collectAsStateWithLifecycle()
    // 获取 SharedPreferences
    val prefs = context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
    // 控制图标隐藏
    var isIconHidden by remember { mutableStateOf(prefs.getBoolean("is_icon_hidden", false)) }
    // 控制菜单状态
    var showMenu by remember { mutableStateOf(false) }
    // 控制用户选择弹窗的状态
    var showUserDialog by remember { mutableStateOf(false) }
    // 控制清空配置弹窗的状态
    var showClearConfigDialog by remember { mutableStateOf(false) }

    // 改为观察 ViewModel
    val deviceInfoMap by viewModel.deviceInfo.collectAsStateWithLifecycle()
    // 首次进入界面时，触发一次加载
    LaunchedEffect(Unit) {
        viewModel.refreshDeviceInfo(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = activeUserName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    val uriHandler = LocalUriHandler.current

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Text("本应用为免费软件", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            },
                            onClick = { showMenu = false },
                            enabled = false
                        )
                        DropdownMenuItem(
                            text = {
                                Text("严禁倒卖/付费购买", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            },
                            onClick = { showMenu = false },
                            enabled = false
                        )
                        DropdownMenuItem(
                            text = { Text("Github 仓库") },
                            onClick = {
                                uriHandler.openUri("https://github.com/Fansirsqi/Sesame-TK")
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Telegram 频道") },
                            onClick = {
                                uriHandler.openUri("https://t.me/Sesame_TK_Channel")
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("加入 QQ 群") },
                            onClick = {
                                joinQQGroup(context)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isIconHidden) "显示应用图标" else "隐藏应用图标") },
                            onClick = {
                                isIconHidden = !isIconHidden
                                onEvent(MainActivity.MainUiEvent.ToggleIconHidden(isIconHidden))
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("查看抓包") },
                            onClick = {
                                onEvent(MainActivity.MainUiEvent.OpenCaptureLog)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("扩展功能") },
                            onClick = {
                                onEvent(MainActivity.MainUiEvent.OpenExtend)
                                showMenu = false
                            }
                        )
                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(
                                text = { Text("清除配置") },
                                onClick = {
                                    showMenu = false
                                    showClearConfigDialog = true
                                }
                            )
                        }
                    }

                }
            )
        },
    )
    { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                StatusCard(
                    status = moduleStatus,
                    expanded = isStatusCardExpanded,
                    onClick = {
                        if (moduleStatus is MainViewModel.ModuleStatus.NotActivated) {
                            isStatusCardExpanded = !isStatusCardExpanded
                        } else {
                            ToastUtil.showToast(oneWord)
                        }
                    }
                )

                if (deviceInfoMap != null) {
                    DeviceInfoCard(deviceInfoMap!!)
                } else {
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp)
                        .padding(8.dp) // 1. 外边距 (Margin)：让卡片和屏幕边缘有距离
                        .clip(RoundedCornerShape(12.dp)) // 2. 裁剪形状：限制水波纹为圆角 (建议稍微大一点的圆角)
//                        .background(MaterialTheme.colorScheme.surfaceContainer) // 3. 背景色：给点击区域一个底色，让它看起来像个卡片
                        .combinedClickable( // 4. 点击事件：必须在 clip 之后，padding(内) 之前
                            enabled = !isOneWordLoading,
                            onClick = { onEvent(MainActivity.MainUiEvent.RefreshOneWord) },
                            onLongClick = {
                                onEvent(MainActivity.MainUiEvent.OpenDebugLog)
                                ToastUtil.showToast(context, "准备起飞🛫")
                            }
                        )
                        .padding(16.dp) // 5. 内边距 (Padding)：让里面的文字和卡片边缘保持距离，不要贴边
                )
                {
                    AnimatedContent(
                        targetState = isOneWordLoading,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "OneWordAnimation"
                    ) { loading ->
                        if (loading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text("本来无一物,何处惹尘..", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                            }

                        } else {
                            Text(
                                text = oneWord,
                                fontSize = 14.sp,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            )
            {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuButton(text = "森林日志", icon = Icons.Rounded.Forest, modifier = Modifier.weight(1f)) { onEvent(MainActivity.MainUiEvent.OpenForestLog) }
                    MenuButton(text = "农场日志", icon = Icons.Rounded.Agriculture, modifier = Modifier.weight(1f)) { onEvent(MainActivity.MainUiEvent.OpenFarmLog) }
                    MenuButton(text = "其他日志", icon = Icons.Rounded.AlignVerticalTop, modifier = Modifier.weight(1f)) { onEvent(MainActivity.MainUiEvent.OpenOtherLog) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuButton(text = "错误日志", icon = Icons.Rounded.BugReport, modifier = Modifier.weight(1f)) { onEvent(MainActivity.MainUiEvent.OpenErrorLog) }
                    MenuButton(text = "全部日志", icon = Icons.Rounded.Description, modifier = Modifier.weight(1f)) { onEvent(MainActivity.MainUiEvent.OpenAllLog) }
                    MenuButton(text = "设置", icon = Icons.Rounded.Settings, modifier = Modifier.weight(1f)) {
//                        onEvent(MainActivity.MainUiEvent.OpenSettings)
                        // 直接在这里处理弹窗逻辑，或者发 Event 给 VM 处理
                        if (userList.isNotEmpty()) {
                            showUserDialog = true
                        } else {
                            ToastUtil.showToast(context, "暂无用户配置")
                        }
                    }
                }
                // ✨ 在 Scaffold 外部（或者内部最上层）挂载 Dialog
                if (showUserDialog) {
                    UserSelectionDialog(
                        userList = userList,
                        onDismissRequest = { showUserDialog = false },
                        onUserSelected = { user ->
                            showUserDialog = false
                            onNavigateToSettings(user) // 触发跳转
                        }
                    )
                }

                // ✨ 挂载清除配置确认弹窗
                if (showClearConfigDialog) {
                    CommonAlertDialog(
                        showDialog = true,
                        onDismissRequest = { showClearConfigDialog = false },
                        onConfirm = { onEvent(MainActivity.MainUiEvent.ClearConfig) },
                        title = "⚠️ 警告",
                        text = "🤔❗ 确认清除所有模块配置？\n此操作无法撤销❗❗❗",
                        icon = Icons.Outlined.Warning,
                        iconTint = MaterialTheme.colorScheme.error, // 红色图标
                        confirmText = "确认清除",
                        confirmButtonColor = MaterialTheme.colorScheme.error // 红色按钮
                    )
                }
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}


@Composable
fun UserSelectionDialog(
    userList: List<UserEntity>,
    onDismissRequest: () -> Unit,
    onUserSelected: (UserEntity) -> Unit
) {
    if (userList.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                Icons.Default.ManageAccounts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "账号设置",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            // 给列表加个最大高度，防止太长铺满屏幕
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Item 之间的间距
                ) {
                    items(userList) { user ->
                        // 使用 Surface 包裹，自带圆角和背景色适配
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh, // 比背景稍微亮一点的颜色
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { // Surface 自带 onClick，自带正确的水波纹
                                onUserSelected(user)
                                onDismissRequest()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp) // 内部留白
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左侧头像/图标
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                // 右侧文本信息
                                Column {
                                    Text(
                                        text = user.showName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!user.account.isNullOrEmpty()) {
                                        Text(
                                            text = user.account,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        },
        // 设置 Dialog 的背景色，使其更融合
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}