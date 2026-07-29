package fansirsqi.xposed.sesame.ui

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentLaunchUiStateResolverTest {

    @Test
    fun `无当前账户时状态未确认`() {
        val configFile = writeConfig(configJson(true))

        val state = PersistentLaunchUiStateResolver.resolve(
            userId = null,
            configFile = configFile
        )

        assertEquals(PersistentLaunchUiState.UNKNOWN, state)
        assertEquals("未确认", state.displayText)
    }

    @Test
    fun `配置文件不存在时状态未确认且不创建文件`() {
        val configFile = File(
            Files.createTempDirectory("persistent-launch-missing").toFile(),
            "config_v2.json"
        )

        val state = PersistentLaunchUiStateResolver.resolve(
            userId = "user-1",
            configFile = configFile
        )

        assertEquals(PersistentLaunchUiState.UNKNOWN, state)
        assertEquals(false, configFile.exists())
    }

    @Test
    fun `字段缺失和损坏JSON均为未确认`() {
        val missingField = writeConfig(
            """{"modelFieldsMap":{"BaseModel":{}}}"""
        )
        val brokenJson = writeConfig("""{"modelFieldsMap":""")

        assertEquals(
            PersistentLaunchUiState.UNKNOWN,
            PersistentLaunchUiStateResolver.resolve(
                "user-1",
                missingField
            )
        )
        assertEquals(
            PersistentLaunchUiState.UNKNOWN,
            PersistentLaunchUiStateResolver.resolve(
                "user-1",
                brokenJson
            )
        )
    }

    @Test
    fun `显式布尔值分别展示已开启和已关闭`() {
        assertEquals(
            PersistentLaunchUiState.ENABLED,
            PersistentLaunchUiStateResolver.resolve(
                "user-1",
                writeConfig(configJson(true))
            )
        )
        assertEquals(
            PersistentLaunchUiState.DISABLED,
            PersistentLaunchUiStateResolver.resolve(
                "user-1",
                writeConfig(configJson(false))
            )
        )
        assertEquals("已开启", PersistentLaunchUiState.ENABLED.displayText)
        assertEquals("已关闭", PersistentLaunchUiState.DISABLED.displayText)
    }

    @Test
    fun `字符串布尔值不得按已开启展示`() {
        val configFile = writeConfig(
            """
                {
                  "modelFieldsMap":{
                    "BaseModel":{
                      "allowPersistentForegroundLaunch":{"value":"true"}
                    }
                  }
                }
            """.trimIndent()
        )

        assertEquals(
            PersistentLaunchUiState.UNKNOWN,
            PersistentLaunchUiStateResolver.resolve(
                "user-1",
                configFile
            )
        )
    }

    private fun configJson(enabled: Boolean): String {
        return """
            {
              "modelFieldsMap":{
                "BaseModel":{
                  "allowPersistentForegroundLaunch":{"value":$enabled}
                }
              }
            }
        """.trimIndent()
    }

    private fun writeConfig(content: String): File {
        return File(
            Files.createTempDirectory("persistent-launch-config").toFile(),
            "config_v2.json"
        ).apply {
            writeText(content, Charsets.UTF_8)
        }
    }
}
