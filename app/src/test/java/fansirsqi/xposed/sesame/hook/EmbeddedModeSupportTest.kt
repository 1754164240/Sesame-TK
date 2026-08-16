package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class EmbeddedModeSupportTest {

    @Test
    fun `目标应用启动时不再拒绝LSPatch集成模式`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/hook/ApplicationHook.kt").readText()

        assertFalse(sourceText.contains("Detector.isLegitimateEnvironment"))
        assertFalse(sourceText.contains("Detector.dangerous"))
    }
}
