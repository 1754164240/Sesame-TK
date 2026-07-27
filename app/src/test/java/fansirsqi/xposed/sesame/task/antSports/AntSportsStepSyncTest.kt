package fansirsqi.xposed.sesame.task.antSports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AntSportsStepSyncTest {

    private class ObfuscatedManager private constructor() {
        companion object {
            @JvmStatic
            fun z(): ObfuscatedManager = ObfuscatedManager()
        }

        @Suppress("UNUSED_PARAMETER")
        fun q(step: Int, background: Boolean, source: String): Boolean = step > 0
    }

    private class AmbiguousManager private constructor() {
        companion object {
            @JvmStatic
            fun x(): AmbiguousManager = AmbiguousManager()

            @JvmStatic
            fun y(): AmbiguousManager = AmbiguousManager()
        }

        @Suppress("UNUSED_PARAMETER")
        fun q(step: Int, background: Boolean, source: String): Boolean = true
    }

    @Test
    fun `8点前也允许把本地步数改为配置步数`() {
        assertTrue(AntSportsStepSync.shouldOverrideDailyStep(originStep = 16, targetStep = 22000))
    }

    @Test
    fun `本地步数已达到配置步数时不覆盖`() {
        assertFalse(AntSportsStepSync.shouldOverrideDailyStep(originStep = 22000, targetStep = 22000))
        assertFalse(AntSportsStepSync.shouldOverrideDailyStep(originStep = 23000, targetStep = 22000))
    }

    @Test
    fun `运动模块读取步数hook不再受8点后条件限制`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSports.kt").readText()

        assertTrue(sourceText.contains("AntSportsStepSync.shouldOverrideDailyStep(originStep, step)"))
        assertFalse(sourceText.contains("TaskCommon.IS_AFTER_8AM && originStep < step"))
    }

    @Test
    fun `同步方法按签名解析而不是依赖混淆名称`() {
        val resolved = StepSyncMethodResolver.resolve(ObfuscatedManager::class.java)

        assertNotNull(resolved)
        assertTrue(resolved!!.factory.name == "z")
        assertTrue(resolved.syncMethod.name == "q")
    }

    @Test
    fun `工厂方法存在歧义时拒绝猜测`() {
        assertNull(StepSyncMethodResolver.resolve(AmbiguousManager::class.java))
    }
}
