package fansirsqi.xposed.sesame.newutil

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

object DataStore {
    private val mapper = jacksonObjectMapper()
    private val data = ConcurrentHashMap<String, Any>()
    private val lock = ReentrantReadWriteLock()
    private lateinit var storageFile: File

    fun init(dir: File) {
        storageFile = File(dir, "DataStore.json").apply {
            if (!exists()) createNewFile()
        }
        loadFromDisk()
        
        // 初始化完成后，如果内存中有数据（可能是初始化前保存的），立即写入磁盘
        if (data.isNotEmpty()) {
            saveToDisk()
        }

        startWatcherNio()
    }

    private var onChangeListener: (() -> Unit)? = null

    fun setOnChangeListener(listener: () -> Unit) {
        onChangeListener = listener
    }

    inline fun <reified T : Any> DataStore.getOrCreate(key: String) = getOrCreate(key, object : TypeReference<T>() {})

    private fun checkInit() {
        if (!::storageFile.isInitialized)
            throw IllegalStateException("DataStore.init(dir) must be called first!")
    }

    fun <T> get(key: String, clazz: Class<T>): T? = lock.read {
        data[key]?.let { mapper.convertValue(it, clazz) }
    }


    /* -------------------------------------------------- */
    /*  类型安全读取：TypeReference 版（支持嵌套泛型）       */
    /* -------------------------------------------------- */
    fun <T : Any> getOrCreate(key: String, typeRef: TypeReference<T>): T = lock.write {
        data[key]?.let { return mapper.convertValue(it, typeRef) }
        val default: T = createDefault(typeRef)
        data[key] = default
        saveToDisk()
        default
    }


    /* 根据 TypeReference 创建默认实例（支持嵌套） */
    @Suppress("UNCHECKED_CAST")
    private fun <T> createDefault(typeRef: TypeReference<T>): T {
        mapper.typeFactory.constructType(typeRef)
        return when (val raw = mapper.typeFactory.constructType(typeRef).rawClass) {
            java.util.List::class.java -> mutableListOf<Any>() as T
            java.util.Set::class.java -> mutableSetOf<Any>() as T
            java.util.Map::class.java -> mutableMapOf<String, Any>() as T
            else -> raw.getDeclaredConstructor().newInstance() as T
        }
    }

    private fun loadFromDisk() {
        if (!::storageFile.isInitialized) return
        // 检查文件是否存在，避免在文件被删除-重命名期间读取
        if (!storageFile.exists()) return
        if (storageFile.length() == 0L) return
        lock.write {
            try {
                val loaded: Map<String, Any> = mapper.readValue(storageFile)
                data.clear()
                data.putAll(loaded)
            } catch (_: MismatchedInputException) {
                // 忽略，可能是文件正在写入导致的格式错误
            } catch (_: java.io.FileNotFoundException) {
                // 忽略，可能是文件在检查后、读取前被删除（竞态条件）
            } catch (_: java.io.IOException) {
                // 忽略其他IO异常，如文件被其他进程占用或删除
            } catch (_: Exception) {
                // 忽略其他所有异常，防止文件监视线程崩溃
            }
        }
    }

    private val prettyPrinter = DefaultPrettyPrinter().apply {
        indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)   // 数组换行
        indentObjectsWith(DefaultIndenter("    ", DefaultIndenter.SYS_LF)) // 对象换行 + 4 空格
    }

    private fun saveToDisk() {
        if (!::storageFile.isInitialized) {
            // DataStore 尚未初始化，跳过保存（仅保存在内存中）
            return
        }
        val tempFile = File(storageFile.parentFile, storageFile.name + ".tmp")
        try {
            tempFile.writeText(mapper.writer(prettyPrinter).writeValueAsString(data))
            if (storageFile.exists()) {
                storageFile.delete()
            }
            tempFile.renameTo(storageFile)
        } catch (_: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun startWatcherNio() = thread(isDaemon = true) {
        try {
            val path = storageFile.toPath().parent
            val watch = path.fileSystem.newWatchService()
            path.register(watch, StandardWatchEventKinds.ENTRY_MODIFY)
            while (true) {
                try {
                    val key = watch.take()
                    key.pollEvents().forEach {
                        val fileName = it.context().toString()
                        // 只处理目标文件的修改事件，忽略临时文件
                        if (fileName == storageFile.name && !fileName.endsWith(".tmp")) {
                            loadFromDisk()
                        }
                    }
                    key.reset()
                } catch (e: Exception) {
                    // 忽略监听过程中的异常，继续循环
                    try {
                        Thread.sleep(1000) // 短暂等待后继续
                    } catch (_: InterruptedException) {
                        break // 线程被中断，退出循环
                    }
                }
            }
        } catch (e: Exception) {
            // 监视服务创建失败，记录日志但不崩溃
        }
    }

    /* -------------------------------------------------- */
    /*  简易 put / remove（可选）                          */
    /* -------------------------------------------------- */
    fun put(key: String, value: Any) = lock.write {
        data[key] = value
        saveToDisk()
    }

    fun remove(key: String) = lock.write {
        data.remove(key)
        saveToDisk()
    }
}