package fansirsqi.xposed.sesame.ui.extension

import android.content.Context
object NativeComposeBridge {


    /**
     * 供 C++ 调用的静态入口
     */
    @JvmStatic
    fun showAlertDialog(context: Context, title: String, message: String, buttonText: String) {
        // Disabled: suppress native startup disclaimer dialogs.
    }





    @JvmStatic
    fun showAlertAfterDelay(
        context: Context,
        title: String,
        message: String,
        buttonText: String,
        delayMillis: Long
    ) {
        // Disabled: suppress native startup disclaimer dialogs and delayed exits.
    }
}
