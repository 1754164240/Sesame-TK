package fansirsqi.xposed.sesame.hook.keepalive

import fansirsqi.xposed.sesame.model.BaseModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PersistentSchedulerConfigTest {

    @Test
    fun schedulerAndForegroundLaunchAreIndependentAndDisabledByDefault() {
        val fields = BaseModel().fields

        assertFalse(fields["persistentSchedulerEnabled"]?.value as Boolean)
        assertFalse(fields["allowPersistentForegroundLaunch"]?.value as Boolean)
        assertFalse(fields["manualTriggerAutoSchedule"]?.value as Boolean)
    }

    @Test
    fun manifestDeclaresPrivateAlarmAndRecoveryReceivers() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val permissions = document.getElementsByTagName("uses-permission")
        val permissionNames = (0 until permissions.length).map {
            (permissions.item(it) as Element).getAttribute("android:name")
        }
        assertTrue(permissionNames.contains("android.permission.RECEIVE_BOOT_COMPLETED"))

        val receivers = document.getElementsByTagName("receiver")
        val trigger = (0 until receivers.length)
            .map { receivers.item(it) as Element }
            .firstOrNull { it.getAttribute("android:name").endsWith("ScheduledTriggerReceiver") }
        val reconcile = (0 until receivers.length)
            .map { receivers.item(it) as Element }
            .firstOrNull { it.getAttribute("android:name").endsWith("ScheduleReconcileReceiver") }

        assertNotNull(trigger)
        assertNotNull(reconcile)
        assertTrue(trigger?.getAttribute("android:enabled") == "true")
        assertTrue(trigger?.getAttribute("android:exported") == "false")
        assertTrue(reconcile?.getAttribute("android:directBootAware") == "true")
        assertTrue(reconcile?.getAttribute("android:exported") == "false")

        val actions = reconcile?.getElementsByTagName("action")
        val actionNames = (0 until (actions?.length ?: 0)).map {
            (actions?.item(it) as Element).getAttribute("android:name")
        }
        assertTrue(actionNames.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(actionNames.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(actionNames.contains("android.intent.action.TIME_SET"))
        assertTrue(actionNames.contains("android.intent.action.TIMEZONE_CHANGED"))
        assertTrue(actionNames.contains("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
    }
}
