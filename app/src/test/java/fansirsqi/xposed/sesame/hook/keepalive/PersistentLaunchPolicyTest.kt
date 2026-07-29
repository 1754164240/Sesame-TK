package fansirsqi.xposed.sesame.hook.keepalive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentLaunchPolicyTest {

    @Test
    fun globalSwitchMustBeEnabledAndPayloadMustRequestLaunch() {
        val requested = schedule("""{"launchTarget":true}""")
        val notRequested = schedule("""{"launchTarget":false}""")

        assertFalse(PersistentLaunchPolicy.shouldLaunchTarget(false, requested))
        assertFalse(PersistentLaunchPolicy.shouldLaunchTarget(true, notRequested))
        assertTrue(PersistentLaunchPolicy.shouldLaunchTarget(true, requested))
    }

    @Test
    fun malformedPayloadNeverEnablesLaunch() {
        assertFalse(PersistentLaunchPolicy.shouldLaunchTarget(true, schedule("{broken")))
    }

    @Test
    fun persistedConfigEnablesLaunchOnlyForExplicitTrueValue() {
        val enabled = """
            {
              "modelFieldsMap": {
                "BaseModel": {
                  "allowPersistentForegroundLaunch": {
                    "value": true
                  }
                }
              }
            }
        """.trimIndent()
        val missing = """{"modelFieldsMap":{"BaseModel":{}}}"""

        assertTrue(PersistentLaunchPolicy.isEnabledInConfig(enabled))
        assertFalse(PersistentLaunchPolicy.isEnabledInConfig(missing))
        assertFalse(PersistentLaunchPolicy.isEnabledInConfig("{broken"))
        assertFalse(PersistentLaunchPolicy.isEnabledInConfig(""))
    }

    private fun schedule(payload: String): PersistentSchedule =
        PersistentSchedule(
            dedupeKey = "global:poll",
            kind = PersistentScheduleKind.GLOBAL_POLL,
            triggerAtMillis = 1_000L,
            payloadJson = payload
        )
}
