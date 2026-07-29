package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONObject

data class FarmNpcAnimalSnapshot(
    val animalId: String,
    val currentFarmId: String,
    val masterFarmId: String,
    val reward: Double,
    val limitReached: Boolean
)

data class FarmNpcSnapshot(
    val recognized: Boolean,
    val farmId: String = "",
    val npc: FarmNpcAnimalSnapshot? = null
)

object AntFarmNpcPolicy {

    fun parseSnapshot(response: String): FarmNpcSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return FarmNpcSnapshot(false)
        if (!isActionSuccess(root)) {
            return FarmNpcSnapshot(false)
        }
        val subFarm = findSubFarm(root)
            ?: return FarmNpcSnapshot(false)
        if (!subFarm.has("animals")) {
            return FarmNpcSnapshot(false)
        }
        val animals = subFarm.optJSONArray("animals")
            ?: return FarmNpcSnapshot(false)
        var npc: FarmNpcAnimalSnapshot? = null
        for (index in 0 until animals.length()) {
            val animal = animals.optJSONObject(index) ?: continue
            if (!animal.optString("subAnimalType").equals("NPC", true)) {
                continue
            }
            val animalId = animal.optString("animalId")
            if (animalId.isBlank()) {
                continue
            }
            npc = FarmNpcAnimalSnapshot(
                animalId = animalId,
                currentFarmId = animal.optString("currentFarmId"),
                masterFarmId = animal.optString("masterFarmId"),
                reward = animal.optDouble("npcBizReward", 0.0)
                    .takeIf { it.isFinite() }
                    ?.coerceAtLeast(0.0)
                    ?: 0.0,
                limitReached = animal.optBoolean(
                    "reachNpcBizRewardLimit",
                    false
                )
            )
            break
        }
        return FarmNpcSnapshot(
            recognized = true,
            farmId = subFarm.optString("farmId"),
            npc = npc
        )
    }

    fun shouldClaimReward(
        snapshot: FarmNpcSnapshot,
        targetAnimalId: String,
        rewardThreshold: Double?
    ): Boolean {
        val npc = snapshot.npc ?: return false
        if (!snapshot.recognized || npc.animalId != targetAnimalId) {
            return false
        }
        return npc.limitReached ||
            (rewardThreshold != null && npc.reward >= rewardThreshold)
    }

    fun isRewardClaimConfirmed(
        before: FarmNpcSnapshot,
        after: FarmNpcSnapshot
    ): Boolean {
        if (!before.recognized || !after.recognized) {
            return false
        }
        val beforeNpc = before.npc ?: return false
        val afterNpc = after.npc ?: return true
        return afterNpc.animalId == beforeNpc.animalId &&
            afterNpc.reward < beforeNpc.reward
    }

    fun isActionSuccess(response: String): Boolean {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return false
        return isActionSuccess(root)
    }

    private fun isActionSuccess(root: JSONObject): Boolean {
        return root.optBoolean("success", false) ||
            root.optString("resultCode").equals("SUCCESS", true) ||
            root.optString("code") == "100"
    }

    private fun findSubFarm(root: JSONObject): JSONObject? {
        root.optJSONObject("subFarmVO")?.let { return it }
        root.optJSONObject("farmVO")
            ?.optJSONObject("subFarmVO")
            ?.let { return it }
        val data = root.optJSONObject("data") ?: return null
        data.optJSONObject("subFarmVO")?.let { return it }
        return data.optJSONObject("farmVO")
            ?.optJSONObject("subFarmVO")
    }
}
