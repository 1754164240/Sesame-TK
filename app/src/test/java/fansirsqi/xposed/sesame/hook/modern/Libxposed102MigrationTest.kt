package fansirsqi.xposed.sesame.hook.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Libxposed102MigrationTest {

    @Test
    fun `构建仅依赖本地 libxposed 102 产物`() {
        val buildScript = File("build.gradle.kts").readText()
        val libraries = File("libs").listFiles()?.map { it.name }?.sorted().orEmpty()

        assertTrue(buildScript.contains("buildToolsVersion = \"37.0.0\""))
        assertTrue(buildScript.contains("version = release(37)"))
        assertTrue(buildScript.contains("minorApiLevel = 0"))
        assertTrue(buildScript.contains("compileOnly(files(\"libs/api-102.0.0.aar\"))"))
        assertTrue(buildScript.contains("implementation(files(\"libs/interface-102.0.0.aar\"))"))
        assertTrue(buildScript.contains("implementation(files(\"libs/service-102.0.0.aar\"))"))
        assertEquals(
            listOf("api-102.0.0.aar", "interface-102.0.0.aar", "service-102.0.0.aar"),
            libraries
        )
    }

    @Test
    fun `模块元数据声明现代 102 入口且不启用热重载`() {
        val moduleProperties = File("src/main/resources/META-INF/xposed/module.prop").readText()
        val javaEntry = File("src/main/resources/META-INF/xposed/java_init.list").readText().trim()

        assertTrue(moduleProperties.contains("minApiVersion=101"))
        assertTrue(moduleProperties.contains("targetApiVersion=102"))
        assertFalse(moduleProperties.contains("autoHotReload"))
        assertEquals("fansirsqi.xposed.sesame.hook.modern.HookEntry", javaEntry)
        assertFalse(File("src/main/assets/xposed_init").exists())
    }

    @Test
    fun `生产源码不再引用旧版 Xposed API`() {
        val sourceRoot = File("src/main/java")
        val legacyFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { it.readText().contains("de.robv.android.xposed") }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertEquals(emptyList<String>(), legacyFiles)
    }
}
