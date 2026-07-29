package fansirsqi.xposed.sesame.hook.keepalive

import org.json.JSONObject

object PersistentLaunchPolicy {
    @JvmStatic
    fun shouldLaunchTarget(
        enabled: Boolean,
        schedule: PersistentSchedule
    ): Boolean {
        if (!enabled) return false
        return runCatching {
            JSONObject(schedule.payloadJson.ifBlank { "{}" })
                .optBoolean("launchTarget", false)
        }.getOrDefault(false)
    }

    @JvmStatic
    fun isEnabledInConfig(configJson: String): Boolean {
        return configuredForegroundLaunch(configJson) == true
    }

    @JvmStatic
    fun configuredForegroundLaunch(configJson: String): Boolean? {
        if (configJson.isBlank()) return null
        return runCatching<Boolean?> {
            val value = JSONObject(configJson)
                .optJSONObject("modelFieldsMap")
                ?.optJSONObject("BaseModel")
                ?.optJSONObject("allowPersistentForegroundLaunch")
                ?.takeIf { it.has("value") }
                ?.opt("value")
            value as? Boolean
        }.getOrNull()
    }

    @JvmStatic
    fun isForegroundLaunchEnabledInPayload(schedule: PersistentSchedule): Boolean =
        runCatching {
            JSONObject(schedule.payloadJson.ifBlank { "{}" })
                .optBoolean("allowPersistentForegroundLaunch", false)
        }.getOrDefault(false)
}
