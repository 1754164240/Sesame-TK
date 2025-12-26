package fansirsqi.xposed.sesame.ui.log

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.FileObserver
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.SesameApplication.Companion.preferencesKey
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

/**
 * 日志 UI 状态
 */
data class LogUiState(
    // 为了兼容 UI 层的 items(count)，这里存储的是当前显示列表的索引 [0, 1, 2, ... size-1]
    val mappingList: List<Int> = emptyList(),
    val isLoading: Boolean = true, // 仅用于初次加载文件
    val isSearching: Boolean = false,   // 🔥 新增：专门用于搜索时的加载状态
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val autoScroll: Boolean = true
)

/**
 * 日志查看器 ViewModel
 * 修复版：移除 RandomAccessFile，改用流式读取以解决 Android 10+ 权限崩溃问题。
 */
class LogViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "LogViewerViewModel"

    // SharedPreferences 用于持久化字体大小
    private val prefs = application.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
    private val logFontSizeKey = "pref_font_size"

    // UI 状态流
    private val _uiState = MutableStateFlow(LogUiState())
    val uiState = _uiState.asStateFlow()

    // 字体大小状态流
    private val _fontSize = MutableStateFlow(prefs.getFloat(logFontSizeKey, 12f))
    val fontSize = _fontSize.asStateFlow()

    // 滚动事件通道
    private val _scrollEvent = Channel<Int>(Channel.BUFFERED)
    val scrollEvent = _scrollEvent.receiveAsFlow()

    // 内部变量
    private var fileObserver: FileObserver? = null
    private var currentFilePath: String? = null
    private var searchJob: Job? = null

    // 🔥 核心数据存储
    // allLogLines: 存储从文件读取的所有行（最大 50000 行）
    private val allLogLines = ArrayList<String>()

    // currentDisplayLines: 存储当前过滤后的行（用于 UI 显示）
    private var currentDisplayLines: List<String> = emptyList()

    // 限制最大行数，防止 OOM
    private val maxLines = 200_000

    /**
     * 加载日志文件
     */
    fun loadLogs(path: String) {
        if (currentFilePath == path) return
        currentFilePath = path

        viewModelScope.launch {
            val file = File(path)
            if (!file.exists()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // 1. 初始读取文件内容
            reloadFileContent(file)

            // 2. 开启文件监听
            startFileObserver(path)
        }
    }

    /**
     * 读取文件内容 (核心修复逻辑)
     * 使用 useLines (底层为 BufferedReader) 顺序读取，兼容性最好。
     * 遇到权限问题时会捕获异常，防止崩溃。
     */
    private suspend fun reloadFileContent(file: File) = withContext(Dispatchers.IO) {
        try {
            _uiState.update { it.copy(isLoading = true) }

            val buffer = ArrayDeque<String>(maxLines)

            // 使用 useLines 流式读取，自动处理 buffer，避免 OOM
            // 这种方式不依赖 RandomAccessFile，能避开部分 EACCES 问题
            file.useLines { sequence ->
                sequence.forEach { line ->
                    if (buffer.size >= maxLines) {
                        buffer.removeFirst() // 保持最新的 N 行
                    }
                    buffer.addLast(line)
                }
            }

            // 更新内存数据
            synchronized(allLogLines) {
                allLogLines.clear()
                allLogLines.addAll(buffer)
            }

            // 刷新列表（处理搜索过滤）
            refreshList()// 这里会重置 isLoading

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "读取失败: ${e.message} (可能无权限)"
            Log.error(tag, errorMsg)

            withContext(Dispatchers.Main) {
                // 停止 Loading，显示错误信息
                _uiState.update { it.copy(isLoading = false) }
                ToastUtil.showToast(getApplication(), errorMsg)
            }
        }
    }

    /**
     * 根据搜索关键词刷新显示列表
     */
    private suspend fun refreshList() {
        val query = _uiState.value.searchQuery.trim()

        // 在 Default 调度器中进行过滤计算
        val resultList = withContext(Dispatchers.Default) {
            synchronized(allLogLines) {
                if (query.isEmpty()) {
                    // 没有搜索，显示全部
                    ArrayList(allLogLines)
                } else {
                    // 有搜索，过滤内容 (不区分大小写)
                    allLogLines.filter {
                        ensureActive() // 响应协程取消
                        it.contains(query, true)
                    }
                }
            }
        }

        // 更新 UI 使用的列表
        currentDisplayLines = resultList

        // 生成索引映射 (0..size-1)，兼容 UI 的 items(count)
        val newMapping = List(resultList.size) { it }

        _uiState.update {
            it.copy(
                mappingList = newMapping,
                totalCount = resultList.size,
                isLoading = false,
                isSearching = false // 🔥 搜索结束，隐藏 loading
            )
        }

        // 处理自动滚动
        if (_uiState.value.autoScroll && resultList.isNotEmpty()) {
            _scrollEvent.send(resultList.size - 1)
        }
    }

    /**
     * 获取指定位置的行内容
     * UI 层通过 index 调用此方法
     */
    fun getLineContent(position: Int): String {
        // 直接从过滤后的列表中获取
        if (position in currentDisplayLines.indices) {
            return currentDisplayLines[position]
        }
        return ""
    }

    /**
     * 开启文件监听
     */
    private fun startFileObserver(path: String) {
        val file = File(path)
        val parentPath = file.parent ?: return
        val parentFile = File(parentPath)

        val onFileEvent: (String?) -> Unit = { p ->
            if (p == file.name) {
                viewModelScope.launch {
                    // 文件变化时，重新全量读取
                    // 对于文本日志，全量读取最稳健，且 50MB 以内速度很快
                    reloadFileContent(file)
                }
            }
        }

        // 兼容 Android 10+ 的 FileObserver 构造
        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(parentFile, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    onFileEvent(p)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(parentPath, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    onFileEvent(p)
                }
            }
        }
        fileObserver?.startWatching()
    }

    /**
     * 清空日志文件
     */
    fun clearLogFile(context: Context) {
        val path = currentFilePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 使用 Files 工具类清空
                if (Files.clearFile(File(path))) {
                    // 清空成功后，刷新内存数据
                    reloadFileContent(File(path))
                    withContext(Dispatchers.Main) {
                        ToastUtil.showToast(context, "文件已清空")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        ToastUtil.showToast(context, "清空失败")
                    }
                }
            } catch (e: Exception) {
                Log.printStackTrace(tag, "Clear error", e)
                withContext(Dispatchers.Main) {
                    ToastUtil.showToast(context, "清空异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 导出日志文件
     */
    fun exportLogFile(context: Context) {
        val path = currentFilePath ?: return
        try {
            val file = File(path)
            if (!file.exists()) {
                ToastUtil.showToast(context, "源文件不存在")
                return
            }
            // 使用 Files 工具类导出
            val exportFile = Files.exportFile(file, true)
            if (exportFile != null && exportFile.exists()) {
                val msg = "${context.getString(R.string.file_exported)} ${exportFile.path}"
                ToastUtil.showToast(context, msg)
            } else {
                ToastUtil.showToast(context, "导出失败")
            }
        } catch (e: Exception) {
            Log.printStackTrace(tag, "Export error", e)
            ToastUtil.showToast(context, "导出异常: ${e.message}")
        }
    }

    /**
     * 搜索 (带防抖)
     */
    fun search(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            if (query.isNotEmpty()) {
                delay(300) // 防抖 300ms
            }
            refreshList()
        }
    }

    // --- 字体控制 ---

    private fun saveFontSize(size: Float) {
        prefs.edit { putFloat(logFontSizeKey, size) }
    }

    fun increaseFontSize() {
        _fontSize.update { current ->
            val newValue = (current + 2f).coerceAtMost(30f)
            saveFontSize(newValue)
            newValue
        }
    }

    fun decreaseFontSize() {
        _fontSize.update { current ->
            val newValue = (current - 2f).coerceAtLeast(8f)
            saveFontSize(newValue)
            newValue
        }
    }

    fun scaleFontSize(factor: Float) {
        _fontSize.update { current ->
            val newValue = (current * factor).coerceIn(8f, 50f)
            saveFontSize(newValue)
            newValue
        }
    }

    fun resetFontSize() {
        _fontSize.value = 12f
        saveFontSize(12f)
    }

    fun toggleAutoScroll(enabled: Boolean) {
        _uiState.update { it.copy(autoScroll = enabled) }
        if (enabled) viewModelScope.launch {
            val size = _uiState.value.mappingList.size
            if (size > 0) _scrollEvent.send(size - 1)
        }
    }

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
    }
}