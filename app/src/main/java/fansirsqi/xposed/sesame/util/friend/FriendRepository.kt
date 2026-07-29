package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.entity.friend.FriendProfile
import fansirsqi.xposed.sesame.entity.friend.FriendRelation
import fansirsqi.xposed.sesame.util.maps.UserMap

class FriendRepository(
    private val storage: FriendCenterStorage
) {
    @Synchronized
    fun loadAndSync(
        ownerUserId: String,
        users: Map<String, UserEntity>
    ): FriendCenterConfig {
        val owner = ownerUserId.trim()
        if (owner.isEmpty()) return FriendCenterConfig()

        val config = storage.load(owner) ?: FriendCenterConfig(userId = owner)
        config.userId = owner

        val currentIds = linkedSetOf<String>()
        users.values.forEach { user ->
            val userId = user.userId?.trim().orEmpty()
            if (userId.isEmpty()) return@forEach
            currentIds.add(userId)

            val existing = config.profiles[userId]
            val relation = relationOf(owner, userId, user.friendStatus)
            val displayName = user.showName.ifBlank { userId }
            if (existing == null) {
                config.profiles[userId] = FriendProfile(
                    userId = userId,
                    displayName = displayName,
                    friendStatus = user.friendStatus,
                    relation = relation
                )
            } else {
                existing.displayName = displayName
                existing.friendStatus = user.friendStatus
                existing.relation = relation
                existing.removed = false
            }
        }

        config.profiles.values.forEach { profile ->
            if (profile.userId !in currentIds) {
                profile.removed = true
                profile.relation = FriendRelation.REMOVED
            }
        }
        storage.save(owner, config)
        return config
    }

    @Synchronized
    fun current(ownerUserId: String): FriendCenterConfig {
        val owner = ownerUserId.trim()
        if (owner.isEmpty()) return FriendCenterConfig()
        return storage.load(owner) ?: FriendCenterConfig(userId = owner)
    }

    @Synchronized
    fun update(
        ownerUserId: String,
        transform: (FriendCenterConfig) -> Unit
    ): FriendCenterConfig {
        val owner = ownerUserId.trim()
        if (owner.isEmpty()) return FriendCenterConfig()
        val config = current(owner)
        config.userId = owner
        transform(config)
        storage.save(owner, config)
        return config
    }

    companion object {
        private val defaultRepository: FriendRepository by lazy {
            FriendRepository(DataStoreFriendCenterStorage)
        }

        @JvmStatic
        fun syncCurrentUserMap(
            ownerUserId: String = UserMap.currentUid.orEmpty()
        ): FriendCenterConfig =
            defaultRepository.loadAndSync(ownerUserId, UserMap.getUserMap().toMap())

        @JvmStatic
        fun currentUserConfig(
            ownerUserId: String = UserMap.currentUid.orEmpty()
        ): FriendCenterConfig = defaultRepository.current(ownerUserId)

        internal fun production(): FriendRepository = defaultRepository
    }

    private fun relationOf(ownerUserId: String, userId: String, friendStatus: Int?): FriendRelation =
        when {
            userId == ownerUserId -> FriendRelation.SELF
            friendStatus == 1 -> FriendRelation.MUTUAL
            else -> FriendRelation.ONE_WAY
        }
}
