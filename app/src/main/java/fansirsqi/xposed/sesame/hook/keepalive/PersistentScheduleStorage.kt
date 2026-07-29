package fansirsqi.xposed.sesame.hook.keepalive

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface PersistentScheduleStorage {
    fun load(): List<PersistentSchedule>

    fun save(schedules: List<PersistentSchedule>): Boolean
}

class PersistentScheduleFileStorage(
    private val file: File,
    private val errorSink: (Throwable) -> Unit = {}
) : PersistentScheduleStorage {
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun load(): List<PersistentSchedule> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return runCatching {
            mapper.readValue(
                file,
                object : TypeReference<List<PersistentSchedule>>() {}
            )
        }.onFailure(errorSink).getOrDefault(emptyList())
    }

    override fun save(schedules: List<PersistentSchedule>): Boolean =
        runCatching {
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    error("无法创建持久调度目录: ${parent.absolutePath}")
                }
            }
            val temporary = File(file.parentFile, file.name + ".tmp")
            mapper.writeValue(temporary, schedules)
            moveReplacing(temporary, file)
            true
        }.onFailure(errorSink).getOrDefault(false)

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
