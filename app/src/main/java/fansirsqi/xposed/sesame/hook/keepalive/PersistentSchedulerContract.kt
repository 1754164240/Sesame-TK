package fansirsqi.xposed.sesame.hook.keepalive

object PersistentSchedulerContract {
    const val ACTION_BIND = "fansirsqi.xposed.sesame.action.BIND_PERSISTENT_SCHEDULER"
    const val ACTION_EXECUTE = "com.eg.android.AlipayGphone.sesame.execute"
    const val EXTRA_DEDUPE_KEY = "dedupeKey"
    const val EXTRA_GENERATION = "generation"
    const val EXTRA_OWNER_USER_ID = "ownerUserId"

}
