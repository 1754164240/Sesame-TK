package fansirsqi.xposed.sesame.ui.log

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.FileObserver
import android.util.LruCache
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
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

/**
 * 日志 UI 状态
 */
data class LogUiState(
    val mappingList: List<Int> = emptyList(),
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val autoScroll: Boolean = true
)

/**
 * 日志查看器 ViewModel
 * ✨ V5 版：修复协程在临界区挂起的死锁风险。
 */
class LogViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "LogViewerViewModel"

    private val prefs = application.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
    private val logFontSizeKey = "pref_font_size"

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState = _uiState.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getFloat(logFontSizeKey, 12f))
    val fontSize = _fontSize.asStateFlow()

    private val _scrollEvent = Channel<Int>(Channel.BUFFERED)
    val scrollEvent = _scrollEvent.receiveAsFlow()

    private var fileObserver: FileObserver? = null
    private var currentFilePath: String? = null
    private var searchJob: Job? = null
    private var loadJob: Job? = null

    // --- 核心数据结构 ---
    private var raf: RandomAccessFile? = null
    private val allLineOffsets = ArrayList<Long>()
    private var displayLineOffsets: List<Long> = emptyList()
    private val lineCache = LruCache<Long, String>(200)

    private var lastKnownFileSize = 0L
    private val maxLines = 200_000

    fun loadLogs(path: String) {
        if (currentFilePath == path && loadJob?.isActive == true) return
        currentFilePath = path
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            closeFile()
            _uiState.update { it.copy(isLoading = true, mappingList = emptyList(), totalCount = 0) }

            val file = File(path)
            if (!file.exists() || !file.canRead()) {
                _uiState.update { it.copy(isLoading = false) }
                if (file.exists()) {
                    ToastUtil.showToast(getApplication(), "文件不可读")
                }
                return@launch
            }

            indexFileContent(file)
            startFileObserver(path)
        }
    }

    private suspend fun indexFileContent(file: File) = withContext(Dispatchers.IO) {
        try {
            val localRaf = RandomAccessFile(file, "r")
            raf = localRaf

            lastKnownFileSize = localRaf.length()
            val buffer = ArrayDeque<Long>(maxLines)
            val varCurrentOffset: Long

            val totalLines = countLines(localRaf)
            if (totalLines > maxLines) {
                val estimatedPosition = localRaf.length() * (totalLines - maxLines) / totalLines
                localRaf.seek(estimatedPosition)
                localRaf.readLine()
                varCurrentOffset = localRaf.filePointer
            } else {
                localRaf.seek(0)
                varCurrentOffset = localRaf.filePointer
            }

            var currentOffset = varCurrentOffset

            while (localRaf.readLine() != null) {
                ensureActive()
                if (buffer.size >= maxLines) {
                    buffer.removeFirst()
                }
                buffer.addLast(currentOffset)
                currentOffset = localRaf.filePointer
            }

            synchronized(allLineOffsets) {
                allLineOffsets.clear()
                allLineOffsets.addAll(buffer)
            }
            lineCache.evictAll()
            refreshList()

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "索引失败: ${e.message}"
            Log.error(tag, errorMsg)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false) }
                ToastUtil.showToast(getApplication(), errorMsg)
            }
        }
    }

    @Throws(IOException::class)
    private fun countLines(raf: RandomAccessFile): Long {
        val originalPos = raf.filePointer
        raf.seek(0)
        var lines = 0L
        val buffer = ByteArray(8192)
        var read: Int
        while (raf.read(buffer).also { read = it } != -1) {
            for (i in 0 until read) {
                if (buffer[i] == '\n'.code.toByte()) {
                    lines++
                }
            }
        }
        raf.seek(originalPos)
        return lines
    }

    private suspend fun refreshList() {
        val query = _uiState.value.searchQuery.trim()

        val resultOffsets = withContext(Dispatchers.IO) {
            synchronized(allLineOffsets) {
                if (query.isEmpty()) {
                    ArrayList(allLineOffsets)
                } else {
                    allLineOffsets.filter { offset ->
                        ensureActive()
                        val line = readLineAt(offset)
                        line?.contains(query, ignoreCase = true) ?: false
                    }
                }
            }
        }

        displayLineOffsets = resultOffsets
        val newMapping = List(resultOffsets.size) { it }

        _uiState.update {
            it.copy(
                mappingList = newMapping,
                totalCount = resultOffsets.size,
                isLoading = false,
                isSearching = false
            )
        }

        if (_uiState.value.autoScroll && resultOffsets.isNotEmpty()) {
            _scrollEvent.send(resultOffsets.size - 1)
        }
    }

    fun getLineContent(position: Int): String {
        if (position !in displayLineOffsets.indices) return ""
        val offset = displayLineOffsets[position]

        val cachedLine = lineCache.get(offset)
        if (cachedLine != null) {
            return cachedLine
        }

        val line = readLineAt(offset) ?: " [读取错误]"
        lineCache.put(offset, line)
        return line
    }

    private fun readLineAt(offset: Long): String? {
        val localRaf = raf ?: return null

        return try {
            synchronized(localRaf) {
                localRaf.seek(offset)
                val lineBytes = localRaf.readLine()?.toByteArray(StandardCharsets.ISO_8859_1)
                lineBytes?.let { bytes -> String(bytes, StandardCharsets.UTF_8) }
            }
        } catch (e: Exception) {
            Log.printStackTrace(tag, "readLineAt failed at offset $offset", e)
            null
        }
    }

    private fun startFileObserver(path: String) {
        val file = File(path)
        val parentPath = file.parent ?: return

        fileObserver?.stopWatching()

        val eventMask = FileObserver.MODIFY or FileObserver.CREATE
        val observerFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) file else File(parentPath)
        val onFileEvent: (String?) -> Unit = { p ->
            val eventFileName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) null else p
            if (eventFileName == null || eventFileName == file.name) {
                viewModelScope.launch { handleFileUpdate() }
            }
        }

        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(observerFile, eventMask) {
                override fun onEvent(event: Int, p: String?) { onFileEvent(p) }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(observerFile.absolutePath, eventMask) {
                override fun onEvent(event: Int, p: String?) { onFileEvent(p) }
            }
        }
        fileObserver?.startWatching()
    }

    private suspend fun handleFileUpdate() {
        val path = currentFilePath ?: return
        val file = File(path)
        if (!file.exists()) return

        try {
            val currentSize = file.length()
            when {
                currentSize > lastKnownFileSize -> appendNewLines()
                currentSize < lastKnownFileSize -> withContext(Dispatchers.Main) { loadLogs(path) }
            }
            lastKnownFileSize = currentSize
        } catch (e: Exception) {
            Log.printStackTrace(tag, "handleFileUpdate failed", e)
        }
    }

    private suspend fun appendNewLines() = withContext(Dispatchers.IO) {
        val localRaf = raf ?: return@withContext

        try {
            val newOffsets = mutableListOf<Long>()
            synchronized(localRaf) {
                localRaf.seek(lastKnownFileSize)
                var currentOffset = lastKnownFileSize

                while (localRaf.readLine() != null) {
                    ensureActive()
                    newOffsets.add(currentOffset)
                    currentOffset = localRaf.filePointer
                }
            }

            if (newOffsets.isNotEmpty()) {
                // ✨ 核心修复：锁的范围仅限于列表修改
                synchronized(allLineOffsets) {
                    allLineOffsets.addAll(newOffsets)
                    while (allLineOffsets.size > maxLines) {
                        allLineOffsets.removeAt(0)
                    }
                }
                // 在锁之外调用挂起函数
                refreshList()
            }
        } catch (e: Exception) {
            Log.printStackTrace(tag, "appendNewLines failed", e)
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }
        searchJob = viewModelScope.launch {
            if (query.isNotEmpty()) {
                delay(300) // Debounce
            }
            refreshList()
        }
    }

    fun clearLogFile(context: Context) {
        val path = currentFilePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Files.clearFile(File(path))) {
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

    fun exportLogFile(context: Context) {
        val path = currentFilePath ?: return
        try {
            val file = File(path)
            if (!file.exists()) {
                ToastUtil.showToast(context, "源文件不存在")
                return
            }
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

    private fun closeFile() {
        try {
            raf?.close()
            raf = null
            fileObserver?.stopWatching()
            fileObserver = null
        } catch (e: Exception) {
            Log.printStackTrace(tag, "closeFile failed", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeFile()
        loadJob?.cancel()
        searchJob?.cancel()
    }
}