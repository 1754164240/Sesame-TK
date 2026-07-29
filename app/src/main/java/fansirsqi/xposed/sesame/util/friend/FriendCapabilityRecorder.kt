package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityState
import fansirsqi.xposed.sesame.entity.friend.FriendModuleCapability

class FriendCapabilityRecorder(
    private val repository: FriendRepository = FriendRepository.production()
) {
    fun record(
        ownerUserId: String,
        friendUserId: String,
        moduleKey: String,
        state: FriendCapabilityState,
        source: String,
        reason: String,
        observedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val owner = ownerUserId.trim()
        val friend = friendUserId.trim()
        val module = moduleKey.trim()
        if (owner.isEmpty() || friend.isEmpty() || module.isEmpty()) return false

        var changed = false
        repository.update(owner) { config ->
            val profile = config.profiles[friend] ?: return@update
            profile.capabilities[module] = FriendModuleCapability(
                state = state,
                source = source,
                reason = reason,
                observedAt = observedAt
            )
            changed = true
        }
        return changed
    }
}
