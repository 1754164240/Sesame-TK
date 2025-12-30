package fansirsqi.xposed.sesame.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.util.PermissionUtil

open class BaseActivity : AppCompatActivity() {

    // 🔥 修复点 1: 改为可空类型，不要使用 lateinit 或直接 lazy 非空
    // Compose 模式下，这个 Toolbar 可能根本不存在
    protected val toolbar: MaterialToolbar? by lazy {
        findViewById(R.id.x_toolbar)
    }

    // 暂存标题
    private var pendingTitle: CharSequence? = null
    private var pendingSubtitle: CharSequence? = null

    // 标记是否使用 Compose (可选，或者直接判断 toolbar 是否为 null)
    protected var isComposeMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 权限检查逻辑保持不变...
        if (PermissionUtil.checkOrRequestFilePermissions(this)) {
            initialize()
        } else {
            // ...
        }
    }

    private fun initialize() {
        // Edge-to-Edge 支持
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 控制状态栏文字颜色
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode()
    }

    override fun onContentChanged() {
        super.onContentChanged()

        // 🔥 修复点 2: 安全访问 toolbar
        // 如果是 Compose 模式，findViewById 会返回 null，我们直接忽略即可
        toolbar?.let { tb ->
            setSupportActionBar(tb)
            tb.setContentInsetsAbsolute(0, 0)
            updateToolbarText()
        }
    }

    // 基础标题
    open var baseTitle: String?
        get() = pendingTitle?.toString()
        set(value) {
            pendingTitle = value
            updateToolbarText()
        }

    // 基础副标题
    open var baseSubtitle: String?
        get() = pendingSubtitle?.toString()
        set(value) {
            pendingSubtitle = value
            updateToolbarText()
        }

    private fun updateToolbarText() {
        // 🔥 修复点 3: 只有当 toolbar 存在时才更新
        toolbar?.let {
            it.title = pendingTitle
            it.subtitle = pendingSubtitle
        }
    }

    fun setBaseTitleTextColor(color: Int) {
        // 🔥 修复点 4: 安全调用
        toolbar?.setTitleTextColor(color)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if ((newConfig.diff(resources.configuration) and Configuration.UI_MODE_NIGHT_MASK) != 0) {
            recreate()
        } else {
            updateToolbarText()
        }
    }

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

}