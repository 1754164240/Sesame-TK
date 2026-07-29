package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AntMemberTest {

    @Test
    fun `芝麻信用不可重试错误码会跳过后续重试`() {
        assertTrue(AntMember.isNonRetryableSesameTaskError("PROMISE_TODAY_FINISH_TIMES_LIMIT"))
        assertTrue(AntMember.isNonRetryableSesameTaskError("PROMISE_TEMPLATE_NOT_EXIST"))
        assertFalse(AntMember.isNonRetryableSesameTaskError("SYSTEM_BUSY"))
        assertFalse(AntMember.isNonRetryableSesameTaskError(""))
    }

    @Test
    fun `芝麻信用失败时会先识别不可重试错误码`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt").readText()

        assertTrue(sourceText.contains("isNonRetryableSesameTaskError(errorCode)"))
        assertTrue(sourceText.contains("不可重试"))
    }

    @Test
    fun `游戏中心平台任务必须通过安全工作流执行`() {
        val sourceText = File(
            "src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt"
        ).readText()
        val gameCenterBlock = sourceText
            .substringAfter("private suspend fun enableGameCenter()")
            .substringBefore("private fun beanSignIn()")

        assertTrue(gameCenterBlock.contains("GameCenterPlatformWorkflow("))
        assertFalse(
            gameCenterBlock.contains(
                "for (i in 0..<platformTaskList.length())"
            )
        )
    }
}
