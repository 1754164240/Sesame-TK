package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONArray
import org.json.JSONObject

enum class ActiveMultiplierState {
    ACTIVE,
    CONFIRMED_NONE,
    INCONCLUSIVE
}

data class CollectedEnergyResult(
    val recognized: Boolean,
    val collected: Int
)

data class ActiveMultiplierSnapshot(
    val state: ActiveMultiplierState,
    val factor: Double = 0.0,
    val endTime: Long = 0L,
    val propType: String = ""
)

data class MultiplierCardCandidate(
    val propId: String,
    val propType: String,
    val propName: String,
    val factor: Double,
    val limited: Boolean,
    val raw: String
)

data class MultiplierBagSnapshot(
    val recognized: Boolean,
    val cards: List<MultiplierCardCandidate>
)

object ForestMultiplierPolicy {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val FACTOR_EPSILON = 0.0001

    fun parseCollectedEnergy(response: String): CollectedEnergyResult {
        val root = parseSuccessResponse(response)
            ?: return CollectedEnergyResult(false, 0)
        if (!root.has("bubbles")) {
            return CollectedEnergyResult(false, 0)
        }
        val bubbles = root.optJSONArray("bubbles")
            ?: return CollectedEnergyResult(false, 0)
        var collected = 0
        for (index in 0 until bubbles.length()) {
            val bubble = bubbles.optJSONObject(index)
                ?: return CollectedEnergyResult(false, 0)
            val bubbleCollected = bubble
                .takeIf { it.has("collectedEnergy") }
                ?.optString("collectedEnergy")
                ?.toIntOrNull()
                ?: return CollectedEnergyResult(false, 0)
            collected += bubbleCollected
                .coerceAtLeast(0)
        }
        return CollectedEnergyResult(true, collected)
    }

    fun shouldRecordWateredFriend(
        result: CollectedEnergyResult,
        friendId: String
    ): Boolean {
        return result.recognized &&
            result.collected > 0 &&
            friendId.isNotBlank()
    }

    fun parseActiveMultiplier(
        response: String,
        nowMillis: Long
    ): ActiveMultiplierSnapshot {
        val root = parseSuccessResponse(response)
            ?: return inconclusiveActive()
        val payload = unwrapPayload(root)
        val props = findUsingProps(payload)
            ?: return inconclusiveActive()
        for (index in 0 until props.length()) {
            val prop = props.optJSONObject(index)
                ?: return inconclusiveActive()
            val propType = prop.optString("propType")
            val propGroup = prop.optString("propGroup")
            if (!isMultiplierProp(propGroup, propType)) {
                continue
            }
            val endTime = prop.optString("endTime").toLongOrNull()
                ?: return inconclusiveActive()
            if (endTime <= nowMillis) {
                continue
            }
            return ActiveMultiplierSnapshot(
                state = ActiveMultiplierState.ACTIVE,
                factor = parseFactor(
                    propType,
                    prop.optString("propName"),
                    prop.optString("extInfo")
                ),
                endTime = endTime,
                propType = propType
            )
        }
        return ActiveMultiplierSnapshot(
            state = ActiveMultiplierState.CONFIRMED_NONE
        )
    }

    fun parseBag(response: String): MultiplierBagSnapshot {
        val root = parseSuccessResponse(response)
            ?: return MultiplierBagSnapshot(false, emptyList())
        val payload = unwrapPayload(root)
        if (!payload.has("forestPropVOList")) {
            return MultiplierBagSnapshot(false, emptyList())
        }
        val props = payload.optJSONArray("forestPropVOList")
            ?: return MultiplierBagSnapshot(false, emptyList())
        val cards = mutableListOf<MultiplierCardCandidate>()
        for (index in 0 until props.length()) {
            val prop = props.optJSONObject(index)
                ?: return MultiplierBagSnapshot(false, emptyList())
            val config = prop.optJSONObject("propConfigVO")
                ?: return MultiplierBagSnapshot(false, emptyList())
            val propType = config.optString("propType")
                .ifBlank { prop.optString("propType") }
            val propGroup = config.optString("propGroup")
                .ifBlank { prop.optString("propGroup") }
            if (!isMultiplierProp(propGroup, propType)) {
                continue
            }
            if (!prop.has("holdsNum")) {
                return MultiplierBagSnapshot(false, emptyList())
            }
            val propIds = prop.optJSONArray("propIdList")
                ?: return MultiplierBagSnapshot(false, emptyList())
            val propId = propIds.optString(0)
            if (prop.optInt("holdsNum", 0) <= 0) {
                continue
            }
            if (propId.isBlank()) {
                return MultiplierBagSnapshot(false, emptyList())
            }
            cards += MultiplierCardCandidate(
                propId = propId,
                propType = propType,
                propName = config.optString("propName", propType),
                factor = parseFactor(
                    propType,
                    config.optString("propName"),
                    ""
                ),
                limited = isLimited(propType),
                raw = prop.toString()
            )
        }
        return MultiplierBagSnapshot(true, cards)
    }

    fun selectCandidate(
        bag: MultiplierBagSnapshot,
        active: ActiveMultiplierSnapshot,
        limitedOnly: Boolean,
        replaceRemainDays: Int,
        nowMillis: Long,
        regularUseAllowed: Boolean = true
    ): MultiplierCardCandidate? {
        if (!bag.recognized || active.state == ActiveMultiplierState.INCONCLUSIVE) {
            return null
        }
        val candidates = bag.cards
            .filter { !limitedOnly || it.limited }
            .filter { it.limited || regularUseAllowed }
            .filter { it.factor > 0.0 }
        if (candidates.isEmpty()) {
            return null
        }
        if (active.state == ActiveMultiplierState.CONFIRMED_NONE) {
            return candidates.maxByOrNull { it.factor }
        }
        if (active.factor <= 0.0 || replaceRemainDays <= 0) {
            return null
        }
        val replaceThreshold = replaceRemainDays
            .toLong()
            .coerceAtLeast(0L) * DAY_MILLIS
        if (active.endTime - nowMillis > replaceThreshold) {
            return null
        }
        return candidates
            .filter { it.factor > active.factor + FACTOR_EPSILON }
            .maxByOrNull { it.factor }
    }

    fun selectExchangeSku(
        selected: Map<String, Int?>,
        resolveName: (String) -> String?
    ): String? {
        return selected.entries.firstNotNullOfOrNull { (skuId, count) ->
            val name = resolveName(skuId).orEmpty()
            skuId.takeIf {
                skuId.isNotBlank() &&
                    (count ?: 0) > 0 &&
                    isMultiplierSkuName(name)
            }
        }
    }

    fun isRenewablePropType(propType: String): Boolean {
        return propType.contains("SHIELD", ignoreCase = true) ||
            propType.contains("BOMB_CARD", ignoreCase = true) ||
            propType.contains("DOUBLE_CLICK", ignoreCase = true) ||
            propType.contains("ROB_EXPAND", ignoreCase = true)
    }

    fun confirmsCandidate(
        active: ActiveMultiplierSnapshot,
        candidate: MultiplierCardCandidate,
        nowMillis: Long
    ): Boolean {
        if (
            active.state != ActiveMultiplierState.ACTIVE ||
            active.endTime <= nowMillis
        ) {
            return false
        }
        return active.propType == candidate.propType ||
            active.factor + FACTOR_EPSILON >= candidate.factor
    }

    internal fun isActionSuccess(response: String): Boolean {
        return parseSuccessResponse(response) != null
    }

    private fun parseSuccessResponse(response: String): JSONObject? {
        if (response.isBlank()) {
            return null
        }
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return null
        if (root.has("success")) {
            return root.takeIf { it.optBoolean("success", false) }
        }
        val resultCode = root.optString("resultCode").uppercase()
        return root.takeIf {
            resultCode in setOf("SUCCESS", "100", "200")
        }
    }

    private fun unwrapPayload(root: JSONObject): JSONObject {
        return sequenceOf("data", "resultData", "resData")
            .mapNotNull(root::optJSONObject)
            .firstOrNull()
            ?: root
    }

    private fun findUsingProps(payload: JSONObject): JSONArray? {
        for (key in listOf("usingUserPropsNew", "loginUserUsingPropNew")) {
            if (payload.has(key)) {
                return payload.optJSONArray(key)
            }
        }
        val mainMember = payload.optJSONObject("teamHomeResult")
            ?.optJSONObject("mainMember")
        if (mainMember?.has("usingUserProps") == true) {
            return mainMember.optJSONArray("usingUserProps")
        }
        return null
    }

    private fun isMultiplierProp(
        propGroup: String,
        propType: String
    ): Boolean {
        return propGroup.equals("robExpandCard", ignoreCase = true) ||
            propType.contains("ROB_EXPAND", ignoreCase = true)
    }

    private fun isLimited(propType: String): Boolean {
        return propType.contains("LIMIT_TIME", ignoreCase = true) ||
            propType.contains("DAY", ignoreCase = true)
    }

    private fun isMultiplierSkuName(name: String): Boolean {
        val normalized = name
            .replace(" ", "")
            .replace("\n", "")
            .uppercase()
        val hasFactor = Regex(
            pattern = "(?:\\d+(?:\\.\\d+)?|N)倍",
            option = RegexOption.IGNORE_CASE
        ).containsMatchIn(normalized)
        val hasCollectionMeaning =
            normalized.contains("收好友") ||
                normalized.contains("收能量") ||
                normalized.contains("倍能量卡")
        return hasFactor && hasCollectionMeaning && normalized.contains("卡")
    }

    private fun parseFactor(vararg values: String): Double {
        val pattern = Regex("""(\d+(?:\.\d+)?)""")
        for (value in values) {
            val factor = pattern.find(value)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
            if (factor != null && factor >= 1.0) {
                return factor
            }
        }
        return 0.0
    }

    private fun inconclusiveActive(): ActiveMultiplierSnapshot {
        return ActiveMultiplierSnapshot(
            state = ActiveMultiplierState.INCONCLUSIVE
        )
    }
}
