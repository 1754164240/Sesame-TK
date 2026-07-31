package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupCompletionTest {
    @Test
    fun `初始化完成后触发首次任务`() {
        var launchCount = 0

        StartupCompletion.launchInitialTask {
            launchCount += 1
        }

        assertEquals(1, launchCount)
    }
}
