package fansirsqi.xposed.sesame.hook.keepalive

import java.util.concurrent.atomic.AtomicReference

class PersistentScheduleUnavailableException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class PersistentBindingCircuitBreaker {
    private enum class State {
        READY,
        CONNECTING,
        OPEN
    }

    private val state = AtomicReference(State.READY)

    fun tryAcquireBinding(): Boolean =
        state.compareAndSet(State.READY, State.CONNECTING)

    fun onConnected() {
        state.compareAndSet(State.CONNECTING, State.READY)
    }

    fun onBindingFailed() {
        state.set(State.OPEN)
    }

    fun isOpen(): Boolean = state.get() == State.OPEN
}
