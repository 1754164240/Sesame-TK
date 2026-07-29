package fansirsqi.xposed.sesame.task.antForest

object ForestWateringPolicy {
    fun shouldRunBeforeCollect(
        enabled: Boolean,
        prioritized: Boolean,
        alreadyExecuted: Boolean
    ): Boolean {
        return enabled && prioritized && !alreadyExecuted
    }

    fun shouldNotify(
        configuredWatering: Boolean,
        configuredNotify: Boolean,
        randomNotify: Boolean
    ): Boolean {
        return if (configuredWatering) configuredNotify else randomNotify
    }
}

object ForestShieldPolicy {
    private const val MAX_THRESHOLD_HOURS = 168
    private const val MAX_STALE_DAYS = 365L
    private const val HOUR_MILLIS = 60L * 60L * 1000L

    fun shouldRenew(
        shieldEndTime: Long,
        nowMillis: Long,
        thresholdHours: Int
    ): Boolean {
        if (
            shieldEndTime > 0L &&
            shieldEndTime < nowMillis - MAX_STALE_DAYS * 24L * HOUR_MILLIS
        ) {
            return false
        }
        if (shieldEndTime <= nowMillis) {
            return true
        }
        val safeThreshold = thresholdHours.coerceIn(0, MAX_THRESHOLD_HOURS)
        return shieldEndTime - nowMillis <= safeThreshold * HOUR_MILLIS
    }

    fun isShield(propGroup: String?, propType: String?): Boolean {
        return propGroup.equals("shield", ignoreCase = true) ||
            propType.orEmpty().contains("ENERGY_SHIELD", ignoreCase = true)
    }
}
