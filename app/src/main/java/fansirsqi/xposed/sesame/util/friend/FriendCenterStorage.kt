package fansirsqi.xposed.sesame.util.friend

import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log

interface FriendCenterStorage {
    fun load(ownerUserId: String): FriendCenterConfig?

    fun save(ownerUserId: String, config: FriendCenterConfig): Boolean
}

object DataStoreFriendCenterStorage : FriendCenterStorage {
    private const val TAG = "FriendCenterStorage"
    private const val KEY_PREFIX = "friendCenter:"

    override fun load(ownerUserId: String): FriendCenterConfig? {
        val normalizedOwner = ownerUserId.trim()
        if (normalizedOwner.isEmpty()) return null
        return runCatching {
            DataStore.get(KEY_PREFIX + normalizedOwner, FriendCenterConfig::class.java)
        }.onFailure {
            Log.error(TAG, "读取好友中心失败: ${it.message}")
        }.getOrNull()
    }

    override fun save(ownerUserId: String, config: FriendCenterConfig): Boolean {
        val normalizedOwner = ownerUserId.trim()
        if (normalizedOwner.isEmpty()) return false
        return runCatching {
            DataStore.put(KEY_PREFIX + normalizedOwner, config)
        }.onFailure {
            Log.error(TAG, "保存好友中心失败: ${it.message}")
        }.isSuccess
    }
}
