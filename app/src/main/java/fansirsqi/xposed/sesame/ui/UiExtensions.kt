package fansirsqi.xposed.sesame.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.util.Detector
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 扩展函数：打开浏览器
 */
fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 扩展函数：带密码验证的执行器
 */
fun Context.executeWithVerification(action: () -> Unit) {
    if (BuildConfig.DEBUG) {
        action()
    } else {
        showPasswordDialog(action)
    }
}

/**
 * 扩展函数：显示密码对话框
 */
@SuppressLint("SetTextI18n")
private fun Context.showPasswordDialog(onSuccess: () -> Unit) {
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(50, 30, 50, 10)
    }

    val label = TextView(this).apply {
        text = "非必要情况无需查看异常日志\n（有困难联系闲鱼卖家帮你处理）"
        textSize = 16f
        setTextColor(Color.DKGRAY)
        setPadding(0, 0, 0, 20)
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    val editText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = "请输入密码"
        setTextColor(Color.BLACK)
        setHintTextColor(Color.GRAY)
        setPadding(40, 30, 40, 30)
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 60f, 60f, 60f, 60f)
            setStroke(3, Color.LTGRAY)
        }
    }

    container.addView(label)
    container.addView(editText)

    val dialog = AlertDialog.Builder(this)
        .setTitle("🔐 防呆验证")
        .setView(container)
        .setPositiveButton("确定", null)
        .setNegativeButton("取消") { d, _ -> d.dismiss() }
        .create()

    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 60f
        })

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor("#3F51B5".toColorInt())
            setOnClickListener {
                if (editText.text.toString() == "Sesame-TK") {
                    ToastUtil.showToast(context, "验证成功😊")
                    onSuccess()
                    dialog.dismiss()
                } else {
                    ToastUtil.showToast(context, "密码错误😡")
                    editText.text.clear()
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.DKGRAY)
    }
    dialog.show()
}

/**
 * 扩展函数：跳转到设置页面
 */
fun Context.navigateToSettings(userEntity: UserEntity) {
    if (Detector.loadLibrary("checker")) {
        Log.record("载入用户配置 ${userEntity.showName}")
        val targetActivity = fansirsqi.xposed.sesame.data.UIConfig.INSTANCE.targetActivityClass
        val intent = Intent(this, targetActivity).apply {
            putExtra("userId", userEntity.userId)
            putExtra("userName", userEntity.showName)
        }
        startActivity(intent)
    } else {
        Detector.tips(this, "缺少必要依赖！")
    }
}

/**
 * 扩展函数：显示用户选择弹窗
 * 封装了原本复杂的 StringDialog 调用逻辑
 */
fun Context.showUserSelectionDialog(
    userList: List<UserEntity>,
    onUserSelected: (UserEntity) -> Unit
) {
    if (userList.isEmpty()) {
        ToastUtil.showToast(this, "暂无用户配置")
        return
    }

    // 构造显示名称数组
    val names = userList.map {
        if (it.account != null) "${it.showName}: ${it.account}" else it.showName
    }.toTypedArray()

    val latch = CountDownLatch(1)

    // 注意：这里假设 StringDialog 是你项目中已有的工具类
    // 如果 StringDialog 也是你写的，可以考虑把它也改成更现代的写法
    val dialog = StringDialog.showSelectionDialog(
        this,
        "📌 请选择配置",
        names,
        { d, which ->
            onUserSelected(userList[which])
            d.dismiss()
            latch.countDown()
        },
        "返回",
        { d ->
            d.dismiss()
            latch.countDown()
        }
    )

    // 自动选择逻辑 (保留原代码逻辑)
    if (userList.size in 1..2) {
        Thread {
            try {
                if (!latch.await(800, TimeUnit.MILLISECONDS)) {
                    // 需要切回主线程操作 UI
                    if (this is android.app.Activity) {
                        this.runOnUiThread {
                            if (dialog.isShowing) {
                                onUserSelected(userList.last())
                                dialog.dismiss()
                            }
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
    }
}