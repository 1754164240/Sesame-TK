package fansirsqi.xposed.sesame.hook.keepalive

object PersistentSchedulerCallerPolicy {
    fun isAllowed(
        callerPackages: Collection<String>,
        modulePackageName: String,
        targetPackageName: String
    ): Boolean =
        callerPackages.any { it == modulePackageName || it == targetPackageName }
}
