package fansirsqi.xposed.sesame.ui

import fansirsqi.xposed.sesame.hook.keepalive.PersistentLaunchPolicy
import java.io.File

enum class PersistentLaunchUiState(
    val displayText: String
) {
    ENABLED("已开启"),
    DISABLED("已关闭"),
    UNKNOWN("未确认")
}

object PersistentLaunchUiStateResolver {

    fun resolve(
        userId: String?,
        configFile: File?
    ): PersistentLaunchUiState {
        if (
            userId.isNullOrBlank() ||
            configFile == null ||
            !configFile.isFile
        ) {
            return PersistentLaunchUiState.UNKNOWN
        }
        val configJson = runCatching {
            configFile.readText(Charsets.UTF_8)
        }.getOrNull() ?: return PersistentLaunchUiState.UNKNOWN
        return when (
            PersistentLaunchPolicy.configuredForegroundLaunch(configJson)
        ) {
            true -> PersistentLaunchUiState.ENABLED
            false -> PersistentLaunchUiState.DISABLED
            null -> PersistentLaunchUiState.UNKNOWN
        }
    }
}
