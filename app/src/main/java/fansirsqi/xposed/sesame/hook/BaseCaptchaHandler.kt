package fansirsqi.xposed.sesame.hook

import android.annotation.SuppressLint
import android.app.Activity
import fansirsqi.xposed.sesame.hook.simple.SimplePageManager
import fansirsqi.xposed.sesame.hook.simple.SimpleViewImage
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.newutil.DataStore
import fansirsqi.xposed.sesame.util.GlobalThreadPools.sleepCompat
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.SwipeUtil

/**
 * 滑动路径数据类 - 封装滑动验证码的路径信息
 * @property startX 滑动起始X坐标
 * @property startY 滑动起始Y坐标
 * @property endX 滑动结束X坐标
 * @property endY 滑动结束Y坐标
 */
data class SlidePath(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int
) {
    fun toIntArray(): IntArray = intArrayOf(startX, startY, endX, endY)
}

/**
 * 验证码处理器基类 - 提供滑动验证码处理的公共逻辑
 * 处理支付宝验证码页面的滑动验证码
 */
abstract class BaseCaptchaHandler {

    companion object {
        private const val TAG = "CaptchaHandler"
        
        // 滑动起始位置偏移量（像素）
        private const val SLIDE_START_OFFSET = 50
        // 滑动结束位置距离屏幕右侧的边距（像素）
        private const val SLIDE_END_MARGIN = 100
        // 滑动持续时间（毫秒）
        private const val SLIDE_DURATION = 500L
        // 最大滑动重试次数
        private const val MAX_SLIDE_RETRIES = 2
        // 滑动重试间隔时间（毫秒）
        private const val SLIDE_RETRY_INTERVAL = 500L

    }

    /**
     * 获取滑动路径在 DataStore 中的存储 key
     * @return 存储滑动路径的 key
     */
    protected abstract fun getSlidePathKey(): String


    /**
     * 处理 Activity 中的验证码
     * @param activity 当前 Activity 实例
     * @param root 根视图
     * @return true 表示验证码处理成功，false 表示处理失败
     */
    open suspend fun handleActivity(activity: Activity, root: SimpleViewImage): Boolean {
        try {

            if (handleSlideCaptcha(activity, root)) {
                return true
            }
        } catch (e: Exception) {
            Log.error(TAG, "处理验证码页面时发生异常: ${e.message}")
        }
        return false
    }


    @SuppressLint("SuspiciousIndentation")
    private suspend fun handleSlideCaptcha(activity: Activity, root: SimpleViewImage): Boolean {
        return try {
            Log.captcha(TAG, "========== 开始处理滑动验证码 ==========")

            val slideTextInDialog = findSlideTextInDialog()
            if (slideTextInDialog == null) {
                Log.captcha(TAG, "Dialog 中未找到滑动验证文字，跳过处理")
                return false
            }
            Log.captcha(TAG, "发现滑动验证文字: ${slideTextInDialog.getText()}")
            val slideRect = getSlideRect(slideTextInDialog) ?: run {
                Log.captcha(TAG, "未找到父节点")
                return false
            }
            val slidePath = calculateSlidePath(activity, slideRect) // 计算滑动路径


            // logSlideInfo(activity, slideRect, slidePath)
            if (!BaseModel.enableSlide.value) {
                Log.captcha(TAG, "Sesame-TK 滑块验证功能已关闭，使用ShortX广播方式滑动")
                ApplicationHook.sendBroadcastShell(
                    getSlidePathKey(),
                    "input swipe " + slidePath.toIntArray().joinToString(" ")
                )
                saveSlidePathIfNeeded(slidePath)
            } else {
                executeSlideWithRetry(activity, root, slidePath)
                saveSlidePathIfNeeded(slidePath)
            }

            true
        } catch (_: Exception) {
          //  Log.captcha(TAG, "处理滑动验证码时发生异常: ${e.message}")
            false
        }
    }

    /**
     * 获取滑动区域的位置信息
     * @param slideText 滑动文本视图
     * @return 滑动区域的位置数组，获取失败返回 null
     */
    private fun getSlideRect(slideText: SimpleViewImage): IntArray? {
        val slideTextParent = slideText.parentNode(1) ?: return null
        return slideTextParent.locationOnScreen()
    }

    /**
     * 计算滑动路径
     * @param activity 当前 Activity
     * @param slideRect 滑动区域位置
     * @return 计算得到的滑动路径
     */
    private fun calculateSlidePath(activity: Activity, slideRect: IntArray): SlidePath {
        val displayMetrics = activity.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        
        val startX = slideRect[0] + SLIDE_START_OFFSET
        val centerY = slideRect[1] + SLIDE_START_OFFSET
        val endX = screenWidth - SLIDE_END_MARGIN
        
        return SlidePath(startX, centerY, endX, centerY)
    }

    /**
     * 记录滑动信息到日志
     * @param activity 当前 Activity
     * @param slideRect 滑动区域位置
     * @param slidePath 滑动路径
     */
    private fun logSlideInfo(activity: Activity, slideRect: IntArray, slidePath: SlidePath) {
        val displayMetrics = activity.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        Log.captcha(TAG, "滑动区域位置: ${slideRect.contentToString()}")
        Log.captcha(TAG, "屏幕尺寸: ${screenWidth}x$screenHeight")
        Log.captcha(TAG, "滑动路径: (${slidePath.startX}, ${slidePath.startY}) -> (${slidePath.endX}, ${slidePath.endY})")
    }

    /**
     * 保存滑动路径到 DataStore（仅在路径变化时保存）
     * @param slidePath 要保存的滑动路径
     */
    private fun saveSlidePathIfNeeded(slidePath: SlidePath) {
        try {
            val slidePathArray = slidePath.toIntArray()
            val slidePathKey = getSlidePathKey()
            val existingPath = DataStore.get(slidePathKey, IntArray::class.java)

            if (existingPath == null || !existingPath.contentEquals(slidePathArray)) {
                DataStore.put(slidePathKey, slidePathArray)
                Log.captcha(TAG, "滑动路径已保存到DataStore: [${slidePathArray.joinToString(", ")}]")
            }
            Log.captcha(TAG, "路径数据: [${slidePathArray.joinToString(", ")}]")
        } catch (e: Exception) {
            Log.captcha(TAG, "保存滑动路径到DataStore失败: ${e.message}")
        }
    }

    /**
     * 执行滑动操作并重试
     * @param activity 当前 Activity
     * @param root 根视图
     * @param slidePath 滑动路径
     * @return true 表示滑动成功，false 表示滑动失败
     */
    private suspend fun executeSlideWithRetry(activity: Activity, root: SimpleViewImage, slidePath: SlidePath): Boolean {
        repeat(MAX_SLIDE_RETRIES) { retry ->
            Log.captcha(TAG, "========== 第 ${retry + 1} 次尝试滑动 ==========")
            val swipeSuccess = SwipeUtil.swipe(
                activity,
                slidePath.startX,
                slidePath.startY,
                slidePath.endX,
                slidePath.endY,
                SLIDE_DURATION
            )

            if (swipeSuccess) {
                Log.captcha(TAG, "滑动操作执行成功，等待验证码文本消失...")
                sleepCompat(2500)
                Log.captcha(TAG, "开始检测验证码文本...")
                if (checkCaptchaTextGone()) {
                    Log.captcha(TAG, "验证码文本已消失，滑动成功")
                    return true
                } else {
                    Log.captcha(TAG, "验证码文本仍然存在，准备重试...")
                }
            } else {
                Log.captcha(TAG, "滑动操作执行失败，准备重试...")
            }
            
            if (retry < MAX_SLIDE_RETRIES - 1) {
                sleepCompat(SLIDE_RETRY_INTERVAL)
            }
        }
        
        Log.captcha(TAG, "已重试 $MAX_SLIDE_RETRIES 次，验证码文本仍然存在")
        return false
    }

    /**
     * 检测验证码文本是否消失
     * @param root 根视图
     * @return true 表示文本已消失，false 表示文本仍然存在
     */
    private fun checkCaptchaTextGone(): Boolean {
        val slideTextInDialog = findSlideTextInDialog()
        if (slideTextInDialog == null) {
            Log.captcha(TAG, "验证码文本已消失（Dialog 中未找到）")
            return true
        }
        Log.captcha(TAG, "验证码文本仍然存在（在 Dialog 中找到）")
        return false
    }

    /**
     * 在 Dialog 中查找滑动验证文本
     * @return 找到的滑动文本视图，未找到返回 null
     */
    private fun findSlideTextInDialog(): SimpleViewImage? {
        return try {
            SimplePageManager.tryGetTopView("//TextView[contains(@text,'向右滑动验证')]")
        } catch (e: Exception) {
            Log.captcha(TAG, "在 Dialog 中查找验证码文本失败: ${e.message}")
            null
        }
    }



}
