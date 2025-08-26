package fansirsqi.xposed.sesame.ui.util


import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku


object ShizukuApi {
    var isBinderAvailable by mutableStateOf(false)
    var isPermissionGranted by mutableStateOf(false)

    fun init() {
        Shizuku.addBinderReceivedListenerSticky {
            isBinderAvailable = true
            refreshPermission()
        }
        Shizuku.addBinderDeadListener {
            isBinderAvailable = false
            isPermissionGranted = false
        }
    }

    fun refreshPermission() {
        isPermissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }
}

