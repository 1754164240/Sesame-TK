package fansirsqi.xposed.sesame.hook

import fansirsqi.xposed.sesame.hook.simple.SimplePageManager


/**
 * 验证码处理器 - 处理支付宝验证码页面
 * 使用精简版框架自动识别和处理各种验证码场景
 */
class Captcha2Handler : BaseCaptchaHandler(), SimplePageManager.ActivityFocusHandler {

    /**
     * 获取滑动路径在 DataStore 中的存储 key
     */
    override fun getSlidePathKey(): String {
        return "slide_path_API"
    }

}
