package fansirsqi.xposed.sesame.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.data.ConnectionState
import fansirsqi.xposed.sesame.data.LsposedServiceManager
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.newutil.DataStore
import fansirsqi.xposed.sesame.newutil.IconManager
import fansirsqi.xposed.sesame.util.AssetUtil
import fansirsqi.xposed.sesame.util.FansirsqiUtil
import fansirsqi.xposed.sesame.util.FansirsqiUtil.getFolderList
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 ViewModel
 * 负责所有非 UI 逻辑：数据加载、文件操作、后台任务、状态管理
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {


    // 一言 (初始状态)
    private val _oneWord = MutableStateFlow("正在获取句子...")
    val oneWord: StateFlow<String> = _oneWord.asStateFlow()

    // 模块运行状态 (未激活/已激活/已加载)

    // 当前激活的用户 (LSPosed 注入的那个)
    private val _activeUser = MutableStateFlow<UserEntity?>(null)
    val activeUser: StateFlow<UserEntity?> = _activeUser.asStateFlow()

    // 用户配置列表 (核心修正：类型为 List<UserEntity>)
    private val _userList = MutableStateFlow<List<UserEntity>>(emptyList())
    val userList: StateFlow<List<UserEntity>> = _userList.asStateFlow()

    // 1. 新增一个 Loading 状态
    private val _isOneWordLoading = MutableStateFlow(false)
    val isOneWordLoading = _isOneWordLoading.asStateFlow()

    // ✨ 1. 新增 StateFlow 暴露模块状态
    private val _moduleStatus = MutableStateFlow<ModuleStatus>(ModuleStatus.Loading)
    val moduleStatus: StateFlow<ModuleStatus> = _moduleStatus.asStateFlow()


    // 🔥 1. 将监听器提取为成员变量
    private val serviceListener: (ConnectionState) -> Unit = { _ ->
        checkServiceState()
    }


    // 初始化标志位
    private var isInitialized = false

    /**
     * 核心初始化入口
     * 注意：必须在 MainActivity 确认获取到文件权限后调用
     */
    fun initAppLogic() {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch(Dispatchers.IO) {
            // 1. 基础环境初始化 (Context, Config路径等)
            initEnvironment()

            // 2. 拷贝资源文件 (耗时 IO 操作)
            copyAssets()
            // 4. 加载业务数据
            reloadUserConfigs() // 加载用户列表
            fetchOneWord()      // 获取一言

            // 🔥 2. 使用成员变量注册
            LsposedServiceManager.addConnectionListener(serviceListener)

        }
    }

    // 🔥 3. 在 ViewModel 销毁时移除监听器
    override fun onCleared() {
        super.onCleared()
        LsposedServiceManager.removeConnectionListener(serviceListener)
        Log.d(TAG, "ViewModel cleared, listener removed.")
    }

    /**
     * 初始化基础环境组件
     */
    private fun initEnvironment() {
        try {
            LsposedServiceManager.init()
            DataStore.init(Files.CONFIG_DIR)
        } catch (e: Exception) {
            Log.e(TAG, "Environment init failed", e)
        }
    }

    /**
     * 拷贝 assets 中的 so 文件和 jar 文件到私有目录
     */
    private fun copyAssets() {
        try {
            val ctx = getApplication<Application>()
            // 这里使用了简化的逻辑，如果文件已存在且未更新，AssetUtil 内部应自行判断
            if (!AssetUtil.copySoFileToStorage(ctx, AssetUtil.checkerDestFile)) {
                Log.e(TAG, "checker file copy failed")
            }
            if (!AssetUtil.copySoFileToStorage(ctx, AssetUtil.dexkitDestFile)) {
                Log.e(TAG, "dexkit file copy failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Asset copy error", e)
        }
    }




    /**
     * 获取一言
     */
    fun fetchOneWord() {
        viewModelScope.launch {
            // 2. 开始加载：设置状态为 true
            _isOneWordLoading.value = true

            // 模拟一点延迟，防止请求太快导致 loading 闪烁（可选优化）
            val startTime = System.currentTimeMillis()

            val result = withContext(Dispatchers.IO) {
                FansirsqiUtil.getOneWord()
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 2500) {
                delay(500 - elapsedTime)
            }

            // 3. 加载结束：更新文本并关闭 Loading
            _oneWord.value = result
            _isOneWordLoading.value = false
        }
    }

    /**
     * 重新加载用户配置列表
     * 通常在 onResume 时调用，以刷新列表
     */
    fun reloadUserConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 加载全局 UI 配置
                try {
                    fansirsqi.xposed.sesame.data.UIConfig.load()
                } catch (e: Exception) {
                    Log.e(TAG, "UIConfig load failed", e)
                }

                // 2. 获取配置文件夹列表
                val configFiles = verifuids
                val newList = mutableListOf<UserEntity>()

                for (userId in configFiles) {
                    // 加载该用户的配置到内存 Map
                    UserMap.loadSelf(userId)

                    // 尝试从 Map 获取实体
                    val mapEntity = UserMap.get(userId)
                    if (mapEntity != null) {
                        newList.add(mapEntity)
                    }
//                    else {
//                        // 关键修正：如果配置文件损坏或不存在，手动创建一个包含 userId 的实体
//                        // 这样 UI 列表依然能显示出这个文件夹，允许用户点击进入设置
//                        val fallbackEntity = UserEntity().apply {
//                            this.userId = userId
//                            this.showName = userId // 只有 ID，没有昵称
//                            this.account = "配置未读取"
//                        }
//                        newList.add(fallbackEntity)
//                    }
                }
                // 更新状态流
                _userList.value = newList
                // 顺便刷新一下服务状态，确保激活用户显示正确
                checkServiceState()
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading user configs", e)
            }
        }
    }

    /**
     * 检查 LSPosed 服务连接状态并更新 UI
     */
    fun checkServiceState() {
        val newStatus = when (val connectionState = LsposedServiceManager.connectionState) {
            is ConnectionState.Connected -> ModuleStatus.Activated(
                frameworkName = connectionState.service.frameworkName,
                frameworkVersion = connectionState.service.frameworkVersion,
                apiVersion = connectionState.service.apiVersion
            )

            else -> ModuleStatus.NotActivated
        }
        _moduleStatus.value = newStatus

        val activeUserEntity = try {
            DataStore.get("activedUser", UserEntity::class.java)
        } catch (_: Exception) {
            null
        }
        _activeUser.value = activeUserEntity
    }

    /**
     * 同步应用图标状态 (隐藏/显示)
     */
    fun syncIconState(isHidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            IconManager.syncIconState(getApplication(), isHidden)
        }
    }

    // ✨ 4. 定义 ViewModel 的状态和事件
    sealed class ModuleStatus {
        data object Loading : ModuleStatus()
        data class Activated(
            val frameworkName: String,
            val frameworkVersion: String,
            val apiVersion: Int
        ) : ModuleStatus()

        data object NotActivated : ModuleStatus()
    }



    companion object {
        const val TAG = "MainViewModel"
        val verifuids: List<String> = getFolderList(Files.CONFIG_DIR.absolutePath)
        var verifyId: String = "待施工🚧..."


    }
}