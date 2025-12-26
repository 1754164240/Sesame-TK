package fansirsqi.xposed.sesame.hook.simple

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import fansirsqi.xposed.sesame.hook.simple.xpcompat.CompatHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 精简版 PageManager - 仅保留 Activity 监控和 Dialog 跟踪功能
 */
@SuppressLint("StaticFieldLeak")
object SimplePageManager {
    
    private const val TAG = "SimplePageManager"
    
    private var mContextRef: WeakReference<Context>? = null
    private var mClassLoader: ClassLoader? = null
    private var topActivity: Activity? = null
    
    private val activityFocusHandlerMap = ConcurrentHashMap<String, ActivityFocusHandler>()
    
    val handler = Handler(Looper.getMainLooper())
    
    private var taskDuration = 200
    private var hasPendingActivityTask = false
    private var disable = false
    
    private val dialogs = ArrayList<WeakReference<android.app.Dialog>>()
    private var windowMonitorEnabled = false
    
    interface ActivityFocusHandler {
        suspend fun handleActivity(activity: Activity, root: SimpleViewImage): Boolean
    }
    
    init {
        enablePageMonitor()
    }
    
    fun getContext(): Context? = mContextRef?.get()
    
    fun getClassLoader(): ClassLoader? = mClassLoader
    
    fun getTopActivity(): Activity? = topActivity
    
    fun setTaskDuration(duration: Int) {
        taskDuration = duration
    }
    
    fun setDisable(disabled: Boolean) {
        disable = disabled
    }
    
    fun addHandler(activityClassName: String, handler: ActivityFocusHandler) {
        activityFocusHandlerMap[activityClassName] = handler
    }
    
    fun removeHandler(activityClassName: String) {
        activityFocusHandlerMap.remove(activityClassName)
    }
    
    fun getDialogs(): ArrayList<WeakReference<android.app.Dialog>> = dialogs
    
    fun enableWindowMonitoring(classLoader: ClassLoader? = null) {
        if (classLoader != null) {
            mClassLoader = classLoader
        }
        Log.i(TAG, "enableWindowMonitoring called, windowMonitorEnabled: $windowMonitorEnabled, classLoader: ${mClassLoader?.javaClass?.name}")
        if (!windowMonitorEnabled) {
            enableWindowMonitor()
            windowMonitorEnabled = true
            Log.i(TAG, "窗口监控已启用")
        } else {
            Log.i(TAG, "窗口监控已经启用，跳过")
        }
    }
    
    /**
     * 尝试在 Dialog 中查找视图
     */
    @SuppressLint("UseKtx")
    fun tryGetTopView(xpath: String): SimpleViewImage? {
        Log.d(TAG, "tryGetTopView searching for xpath: $xpath")
        Log.d(TAG, "  Dialogs: ${dialogs.size}")
        
        for (dialogWeakReference in dialogs) {
            val dialog = dialogWeakReference.get()
            if (dialog == null) {
                dialogs.remove(dialogWeakReference)
                continue
            }
            if (!dialog.isShowing) {
                continue
            }
            val decorView = dialog.window?.decorView ?: continue
            Log.d(TAG, "  Dialog: ${dialog.javaClass.name}, showing: ${dialog.isShowing}")
            
            debugPrintAllTextViews(decorView, 0)
            
            val viewImage = SimpleViewImage(decorView)
            val results = SimpleXpathParser.evaluate(viewImage, xpath)
            if (results.isNotEmpty()) {
                Log.d(TAG, "  Found in Dialog!")
                return results[0]
            }
        }
        
        Log.d(TAG, "  Not found in any dialog")
        return null
    }
    
    /**
     * 打印所有 TextView 的文本内容（用于调试）
     */
    private fun debugPrintAllTextViews(view: View, depth: Int) {
        val indent = "  ".repeat(depth)
        if (view is android.widget.TextView) {
            val text = view.text?.toString() ?: ""
            val contentDesc = view.contentDescription?.toString() ?: ""
            if (text.isNotEmpty() || contentDesc.isNotEmpty()) {
                Log.d(TAG, "${indent}TextView[${view.javaClass.simpleName}] text='$text' contentDesc='$contentDesc'")
            }
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                debugPrintAllTextViews(view.getChildAt(i), depth + 1)
            }
        }
    }
    
    /**
     * 启用 Activity 监控
     */
    private fun enablePageMonitor() {
        try {
            CompatHelpers.findAndHookMethod(
                Application::class.java,
                "dispatchActivityResumed",
                Activity::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        topActivity = param.args[0] as Activity
                        if (mContextRef?.get() == null) {
                            mContextRef = WeakReference(topActivity?.applicationContext)
                        }
                        mClassLoader = topActivity?.classLoader
                        triggerActivity()
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Hook Activity->dispatchActivityResumed error: ${e.message}")
        }
    }
    
    /**
     * 启用 Dialog 监控
     */
    private fun enableWindowMonitor() {
        Log.i(TAG, "enableWindowMonitor called, classLoader: ${mClassLoader?.javaClass?.name}")
        
        try {
            CompatHelpers.findAndHookConstructor(
                "android.app.Dialog",
                getClassLoader(),
                "android.content.Context",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as android.app.Dialog
                        dialogs.add(WeakReference(dialog))
                        Log.d(TAG, "Dialog created via constructor(Context), total: ${dialogs.size}")
                        triggerDialogProcessing()
                    }
                }
            )
            Log.i(TAG, "Hook Dialog constructor(Context) success")
        } catch (e: Throwable) {
            Log.e(TAG, "Hook Dialog constructor(Context) error: ${e.message}")
        }
        
        try {
            CompatHelpers.findAndHookConstructor(
                "android.app.Dialog",
                getClassLoader(),
                "android.content.Context",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as android.app.Dialog
                        dialogs.add(WeakReference(dialog))
                        Log.d(TAG, "Dialog created via constructor(Context,theme), total: ${dialogs.size}")
                        triggerDialogProcessing()
                    }
                }
            )
            Log.i(TAG, "Hook Dialog constructor(Context,theme) success")
        } catch (e: Throwable) {
            Log.e(TAG, "Hook Dialog constructor(Context,theme) error: ${e.message}")
        }
        
        try {
            CompatHelpers.findAndHookConstructor(
                "android.app.Dialog",
                getClassLoader(),
                "android.content.Context",
                "boolean",
                "android.content.DialogInterface.OnCancelListener",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as android.app.Dialog
                        dialogs.add(WeakReference(dialog))
                        Log.d(TAG, "Dialog created via constructor(Context,cancelable,cancelListener), total: ${dialogs.size}")
                        triggerDialogProcessing()
                    }
                }
            )
            Log.i(TAG, "Hook Dialog constructor(Context,cancelable,cancelListener) success")
        } catch (e: Throwable) {
            Log.e(TAG, "Hook Dialog constructor(Context,cancelable,cancelListener) error: ${e.message}")
        }
        
        try {
            val captchaDialogClass = de.robv.android.xposed.XposedHelpers.findClass(
                "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog",
                getClassLoader()
            )
            CompatHelpers.findAndHookMethod(
                captchaDialogClass,
                "show",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as android.app.Dialog
                        if (!dialogs.any { it.get() === dialog }) {
                            dialogs.add(WeakReference(dialog))
                            Log.d(TAG, "CaptchaDialog.show() called, added to dialogs, total: ${dialogs.size}")
                            triggerDialogProcessing()
                        } else {
                            Log.d(TAG, "CaptchaDialog.show() called, already in dialogs")
                        }
                    }
                }
            )
            Log.i(TAG, "Hook CaptchaDialog.show() success")
        } catch (e: Throwable) {
            Log.e(TAG, "Hook CaptchaDialog.show() error: ${e.message}")
        }
    }
    
    /**
     * 触发 Activity 处理
     */
    private fun triggerActivity() {
        val activity = topActivity ?: run {
            Log.i(TAG, "no top activity found")
            return
        }
        
        val handler = activityFocusHandlerMap[activity.javaClass.name]
        if (handler != null) {
            if (hasPendingActivityTask) {
                return
            }
            hasPendingActivityTask = true
            triggerActivityActive(activity, handler, 0)
        } else {
            Log.i(TAG, "triggerActivity not found activity handler: ${activity.javaClass.name}")
        }
    }
    
    /**
     * 触发 Dialog 处理
     */
    private fun triggerDialogProcessing() {
        val activity = topActivity ?: run {
            Log.i(TAG, "triggerDialogProcessing: no top activity found")
            return
        }
        
        val handler = activityFocusHandlerMap[activity.javaClass.name]
        if (handler != null) {
            if (hasPendingActivityTask) {
                Log.d(TAG, "triggerDialogProcessing: has pending activity task, skip")
                return
            }
            hasPendingActivityTask = true
            Log.i(TAG, "triggerDialogProcessing: triggering activity handler for Dialog: ${activity.javaClass.name}")
            triggerActivityActive(activity, handler, 0)
        } else {
            Log.d(TAG, "triggerDialogProcessing: not found activity handler: ${activity.javaClass.name}")
        }
    }
    
    /**
     * 延迟触发 Activity 处理
     */
    private fun triggerActivityActive(
        activity: Activity,
        activityFocusHandler: ActivityFocusHandler,
        triggerCount: Int
    ) {
        if (disable) {
            Log.i(TAG, "Page Trigger manager disabled")
            return
        }
        
        val job = CoroutineScope(Dispatchers.Main).launch {
            delay(taskDuration.toLong())
            try {
                Log.i(TAG, "triggerActivityActive activity: ${activity.javaClass.name} for ActivityFocusHandler: ${activityFocusHandler.javaClass.name}")
                hasPendingActivityTask = false
                if (activityFocusHandler.handleActivity(activity, SimpleViewImage(activity.window.decorView))) {
                    return@launch
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "error to handle activity: ${activity.javaClass.name}", throwable)
            }
            
            if (triggerCount > 10) {
                Log.w(TAG, "the activity event trigger failed too many times: ${activityFocusHandler.javaClass}")
                return@launch
            }
            triggerActivityActive(activity, activityFocusHandler, triggerCount + 1)
        }
    }
}
