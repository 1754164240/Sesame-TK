package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class SlideCaptchaRemovalTest {

    @Test
    fun `目标应用启动时不再注册自动滑块验证处理器`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/hook/ApplicationHook.kt").readText()

        assertFalse(sourceText.contains("initSimplePageManager"))
        assertFalse(sourceText.contains("CaptchaHook.setupHook"))
        assertFalse(sourceText.contains("enableWindowMonitoring(classLoader)"))
        assertFalse(sourceText.contains("Captcha1Handler()"))
        assertFalse(sourceText.contains("Captcha2Handler()"))
        assertFalse(sourceText.contains("shouldEnableSimplePageManager"))
    }

    @Test
    fun `安全验证不再自动启动目标应用滑块`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/hook/rpc/bridge/NewRpcBridge.java").readText()

        assertFalse(sourceText.contains("SwipeUtil.startAlipay"))
        assertFalse(sourceText.contains("自动启动目标应用进行滑块"))
        assertFalse(sourceText.contains("shouldEnableSimplePageManager"))
    }

    @Test
    fun `首页不再展示滑块验证服务卡片`() {
        val sourceText = File("src/main/java/fansirsqi/xposed/sesame/ui/screen/content/HomeContent.kt").readText()

        assertFalse(sourceText.contains("ServicesStatusCard"))
        assertFalse(sourceText.contains("serviceStatus"))
        assertFalse(sourceText.contains("ServiceStatus"))
    }
}
