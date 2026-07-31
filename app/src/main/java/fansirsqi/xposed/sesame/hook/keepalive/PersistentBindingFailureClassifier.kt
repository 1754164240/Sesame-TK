package fansirsqi.xposed.sesame.hook.keepalive

enum class PersistentBindingFailureKind {
    BIND_RETURNED_FALSE,
    SECURITY_REJECTED,
    CONNECTION_TIMEOUT,
    BINDER_DIED,
    UNKNOWN
}

object PersistentBindingFailureClassifier {
    fun classify(error: Throwable): PersistentBindingFailureKind {
        val causes = generateSequence(error as Throwable?) { it.cause }.toList()
        val messages = causes.mapNotNull(Throwable::message)
        return when {
            causes.any { it is SecurityException } ->
                PersistentBindingFailureKind.SECURITY_REJECTED
            causes.any { it.javaClass.simpleName == "DeadObjectException" } ->
                PersistentBindingFailureKind.BINDER_DIED
            messages.any { it.contains("bindService返回false") } ->
                PersistentBindingFailureKind.BIND_RETURNED_FALSE
            messages.any { it.contains("连接超时") } ->
                PersistentBindingFailureKind.CONNECTION_TIMEOUT
            else -> PersistentBindingFailureKind.UNKNOWN
        }
    }
}
