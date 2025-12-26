package fansirsqi.xposed.sesame.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.newutil.DataStore.init
import fansirsqi.xposed.sesame.util.FansirsqiUtil.getFolderList
import fansirsqi.xposed.sesame.util.Files
import io.github.libxposed.service.XposedService


@SuppressLint("StaticFieldLeak")
object ViewAppInfo {
    val TAG: String = ViewAppInfo::class.java.simpleName
    var context: Context? = null
    var appTitle: String = ""
    var appVersion: String = ""
    var appBuildTarget: String = ""
    var appBuildNumber: String = ""
    var verifyId: String = ""
    var veriftag: Boolean = false

    var verifuids: List<String> = listOf()

    private var _service: XposedService? = null
    val service get() = _service

    @SuppressLint("HardwareIds")
    val emojiList = listOf(
        "🍅", "🍓", "🥓", "🍂", "🍚", "🌰", "🟢", "🌴",
        "🥗", "🧀", "🥩", "🍍", "🌶️", "🍲", "🍆", "🥕",
        "✨", "🍑", "🍘", "🍀", "🥞", "🍈", "🥝", "🧅",
        "🌵", "🌾", "🥜", "🍇", "🌭", "🥑", "🥐", "🥖",
        "🍊", "🌽", "🍉", "🍖", "🍄", "🥚", "🥙", "🥦",
        "🍌", "🍱", "🍏", "🍎", "🌲", "🌿", "🍁", "🍒",
        "🥔", "🌯", "🌱", "🍐", "🍞", "🍳", "🍙", "🍋",
        "🍗", "🌮", "🍃", "🥘", "🥒", "🧄", "🍠", "🥥", "📦"
    )

    @Volatile
    internal var runType: RunType = RunType.DISABLE
        @Synchronized set

    @JvmStatic
    fun getRunType(): RunType = runType

    /**
     * 初始化 ViewAppInfo，设置应用的相关信息，如版本号、构建日期等
     *
     * @param context 上下文对象，用于获取应用的资源信息
     */
    @SuppressLint("HardwareIds")
    fun init(context: Context) {
        init(Files.CONFIG_DIR)
        Log.d(TAG, "app data init")
        if (ViewAppInfo.context == null) {
            ViewAppInfo.context = context
            verifyId = "debug"
            verifuids = getFolderList(Files.CONFIG_DIR.absolutePath)
            appBuildNumber = BuildConfig.VERSION_CODE.toString()
            appTitle = context.getString(R.string.app_name)
            appBuildTarget = BuildConfig.BUILD_DATE + " " + BuildConfig.BUILD_TIME + " ⏰"
            try {
                appVersion = "${BuildConfig.VERSION_NAME} " + emojiList.random()
            } catch (e: Exception) {
                Log.e(TAG, "init: ", e)
            }
            runType = RunType.LOADED
        }
    }
}