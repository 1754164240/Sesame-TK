package fansirsqi.xposed.sesame.hook

import android.app.Activity
import fansirsqi.xposed.sesame.hook.simple.SimplePageManager
import fansirsqi.xposed.sesame.hook.simple.SimpleViewImage


/**
 * 验证码处理器 - 处理目标应用验证码页面
 * 使用精简版框架自动识别和处理各种验证码场景
 */
class Captcha1Handler : BaseCaptchaHandler(), SimplePageManager.ActivityFocusHandler {

    /**
     * 获取滑动路径在 DataStore 中的存储 key
     */
    override fun getSlidePathKey(): String {
        return "slide_path_Sheng_Huo"
    }


}
