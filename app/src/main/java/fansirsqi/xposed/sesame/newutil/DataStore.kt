package fansirsqi.xposed.sesame.newutil

import android.annotation.SuppressLint
import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write
import kotlin.math.abs

object DataStore {
    private const val TAG = "SesameDataStore"
    private const val FILE_NAME = "DataStore.json"

    // 配置 Jackson：忽略未知的属性，防止版本升级导致崩溃
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private val data = ConcurrentHashMap<String, Any>()
    private val lock = ReentrantReadWriteLock()
    private lateinit var storageFile: File

    // 用于防抖：记录最后一次加载的文件修改时间
    private val lastLoadedTime = AtomicLong(0)
    // 用于防抖：记录最后一次写入的时间，避免自己写文件触发自己的监听
    private val lastWriteTime = AtomicLong(0)

    private var onChangeListener: (() -> Unit)? = null

    @Suppress("unused")
    fun setOnChangeListener(listener: () -> Unit) {
        onChangeListener = listener
    }

    /**
     * 初始化 DataStore
     * @param dir 存储目录
     */
    fun init(dir: File) {
        // 1. 确保目录存在 (修复崩溃的核心)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: ${dir.absolutePath}")
                // 如果是 Xposed 环境，这里可能因为权限不足失败，但我们尝试继续
            }
        }

        // 2. 设置目录权限为 777 (对 Xposed 模块至关重要，否则宿主读不到)
        setWorldReadableWritable(dir)

        storageFile = File(dir, FILE_NAME)

        // 3. 确保文件存在
        if (!storageFile.exists()) {
            try {
                storageFile.createNewFile()
                // 设置文件权限 666
                setWorldReadableWritable(storageFile)
                // 写入空 JSON 对象，避免空文件导致解析错误
                storageFile.writeText("{}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create storage file", e)
            }
        }

        loadFromDisk()
        startWatcherNio()
    }

    /**
     * 设置文件/目录为全局可读写 (Linux 权限 777/666)
     * 这在 Xposed 跨进程通信中通常是必须的
     */
    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    private fun setWorldReadableWritable(file: File) {
        try {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false) // 对目录需要执行权限
        } catch (_: Exception) {
            // 忽略某些系统限制导致的失败
        }
    }

    inline fun <reified T : Any> getOrCreate(key: String): T {
        return getOrCreate(key, object : TypeReference<T>() {})
    }

    fun <T> get(key: String, clazz: Class<T>): T? = lock.read {
        try {
            data[key]?.let { mapper.convertValue(it, clazz) }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting value for key: $key", e)
            null
        }
    }

    /* -------------------------------------------------- */
    /*  类型安全读取                                       */
    /* -------------------------------------------------- */
    fun <T : Any> getOrCreate(key: String, typeRef: TypeReference<T>): T = lock.write {
        // 1. 尝试从内存获取
        data[key]?.let {
            try {
                return mapper.convertValue(it, typeRef)
            } catch (e: Exception) {
                Log.w(TAG, "Data mismatch for key $key, overwriting with default.", e)
            }
        }

        // 2. 内存没有，创建默认值
        val default: T = createDefault(typeRef)
        data[key] = default

        // 3. 只有当确实是新数据时才保存，避免频繁 IO
        saveToDisk()
        default
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> createDefault(typeRef: TypeReference<T>): T {
        val javaType = mapper.typeFactory.constructType(typeRef)
        return when (val rawClass = javaType.rawClass) {
            List::class.java, java.util.List::class.java -> ArrayList<Any>() as T
            Set::class.java, java.util.Set::class.java -> LinkedHashSet<Any>() as T
            Map::class.java, java.util.Map::class.java -> LinkedHashMap<String, Any>() as T
            String::class.java -> "" as T
            Boolean::class.java, java.lang.Boolean::class.java -> false as T
            Int::class.java, java.lang.Integer::class.java -> 0 as T
            Long::class.java, java.lang.Long::class.java -> 0L as T
            else -> {
                try {
                    // 尝试无参构造
                    rawClass.getDeclaredConstructor().newInstance() as T
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot create default instance for ${rawClass.simpleName}, relying on Jackson null handling or crash.")
                    throw RuntimeException("Could not create default value for ${rawClass.name}", e)
                }
            }
        }
    }

    private fun loadFromDisk() {
        if (!::storageFile.isInitialized || !storageFile.exists()) return

        // 检查文件修改时间，防止重复加载
        val currentModTime = storageFile.lastModified()
        if (currentModTime <= lastLoadedTime.get()) {
            return
        }

        // 如果文件修改时间非常接近我们最后一次写入的时间（< 500ms），说明是我们自己写的，忽略
        if (abs(currentModTime - lastWriteTime.get()) < 500) {
            lastLoadedTime.set(currentModTime)
            return
        }

        lock.write {
            try {
                // 双重检查，防止在等待锁的过程中文件又被改了
                if (storageFile.length() == 0L) return@write

                val loaded: Map<String, Any> = mapper.readValue(storageFile)
                data.clear()
                data.putAll(loaded)

                lastLoadedTime.set(currentModTime)

                // 通知监听器
                onChangeListener?.invoke()

            } catch (e: Exception) {
                // 仅记录严重错误，忽略文件被占用导致的临时错误
                if (e !is MismatchedInputException) {
                    Log.w(TAG, "Failed to load config: ${e.message}")
                }
            }
        }
    }

    private val prettyPrinter = DefaultPrettyPrinter().apply {
        indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
        indentObjectsWith(DefaultIndenter("    ", DefaultIndenter.SYS_LF))
    }

    private fun saveToDisk() {
        if (!::storageFile.isInitialized) return

        lock.read { // 写文件时只需要读取内存数据的“读锁”，不需要阻塞其他读操作？不，Jackson序列化可能耗时，还是安全起见
            // 但为了防止并发修改导致ConcurrentModificationException（虽然用了ConcurrentMap），
            // jackson序列化整个Map是线程安全的。
            try {
                val tempFile = File(storageFile.parentFile, storageFile.name + ".tmp")

                // 写入临时文件
                mapper.writer(prettyPrinter).writeValue(tempFile, data)

                // 设置临时文件权限，否则 rename 后权限可能丢失
                setWorldReadableWritable(tempFile)

                // 记录写入时间
                lastWriteTime.set(System.currentTimeMillis())

//                // 原子重命名
//                if (storageFile.exists()) {
//                    // Android 上 renameTo 有时不能覆盖已存在文件，需先删除
//                    // 注意：这里有极小的竞态窗口，但在 Android 文件系统中通常是原子或安全的
//                }

                if (tempFile.renameTo(storageFile)) {
                    // 更新加载时间，避免 Watcher 再次触发加载
                    lastLoadedTime.set(storageFile.lastModified())
                } else {
                    // 如果 rename 失败（跨分区或权限），尝试复制+删除
                    storageFile.delete() // 强制删除旧文件
                    if (tempFile.renameTo(storageFile)) {
                        lastLoadedTime.set(storageFile.lastModified())
                    } else {
                        Log.e(TAG, "Failed to rename temp file to storage file")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save config", e)
            }
        }
    }

    fun startWatcherNio() = thread(name = "SesameConfigWatcher", isDaemon = true) {
        try {
            if (!::storageFile.isInitialized) return@thread

            val path = storageFile.toPath().parent ?: return@thread
            val watchService = path.fileSystem.newWatchService()
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

            while (true) {
                val key = try {
                    watchService.take()
                } catch (_: InterruptedException) {
                    break
                }

                var shouldReload = false
                key.pollEvents().forEach { event ->
                    // 安全转换 context
                    val changedPath = event.context() as? java.nio.file.Path
                    val fileName = changedPath?.toString()

                    if (fileName == storageFile.name) {
                        shouldReload = true
                    }
                }

                if (shouldReload) {
                    // 稍微延迟一下，等待文件写入完成
                    Thread.sleep(100)
                    loadFromDisk()
                }

                if (!key.reset()) {
                    break // 目录不可访问，退出循环
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "File watcher died", e)
        }
    }

    fun put(key: String, value: Any) = lock.write {
        data[key] = value
        saveToDisk()
    }

    fun remove(key: String) = lock.write {
        data.remove(key)
        saveToDisk()
    }
}