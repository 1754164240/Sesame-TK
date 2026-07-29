package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONArray
import org.json.JSONObject

data class ForestPatrolRecordCandidate(
    val patrolId: Int,
    val startDate: Long,
    val hasUnreachedNodes: Boolean
)

enum class ForestPatrolTargetReason {
    MISSING_PIECES,
    UNREACHED_NODES,
    LATEST_LOOP
}

data class ForestPatrolTarget(
    val patrolId: Int,
    val reason: ForestPatrolTargetReason
)

data class ForestAnimalDispatchSelection(
    val index: Int,
    val holdsNum: Int,
    val estimatedEnergy: Int
)

object ForestPatrolPolicy {

    fun hasRecognizedAnimalCatalog(patrolConfig: JSONObject): Boolean =
        patrolConfig.optJSONArray("animals")?.length()?.let { it > 0 } == true

    fun normalOnlineAnimalIds(patrolConfig: JSONObject): Set<Int> {
        val animals = patrolConfig.optJSONArray("animals") ?: return emptySet()
        val result = linkedSetOf<Int>()
        for (index in 0 until animals.length()) {
            val animal = animals.optJSONObject(index) ?: continue
            val animalId = animal.optInt("id", -1)
            if (
                animalId <= 0 ||
                !animal.optString("status").equals("ONLINE", true) ||
                isLimitedAnimal(animal)
            ) {
                continue
            }
            result += animalId
        }
        return result
    }

    fun hasMissingNormalAnimalPieces(
        normalAnimalIds: Set<Int>,
        animalProps: JSONArray
    ): Boolean {
        if (normalAnimalIds.isEmpty()) {
            return false
        }
        for (index in 0 until animalProps.length()) {
            val animalProp = animalProps.optJSONObject(index) ?: continue
            val animalId = animalProp.optJSONObject("animal")
                ?.optInt("id", -1)
                ?: continue
            if (animalId !in normalAnimalIds) {
                continue
            }
            val pieces = animalProp.optJSONArray("pieces") ?: continue
            for (pieceIndex in 0 until pieces.length()) {
                val piece = pieces.optJSONObject(pieceIndex) ?: continue
                if (piece.optInt("holdsNum", 0) <= 0) {
                    return true
                }
            }
        }
        return false
    }

    fun findAnimalProps(response: JSONObject): JSONArray? {
        val pending = ArrayDeque<JSONObject>()
        pending += response
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            current.optJSONArray("animalProps")?.let { return it }
            for (key in listOf("data", "result", "resultData")) {
                current.optJSONObject(key)?.let(pending::addLast)
            }
        }
        return null
    }

    fun selectTarget(
        records: List<ForestPatrolRecordCandidate>,
        missingPiecePatrolIds: Set<Int>
    ): ForestPatrolTarget? {
        val sorted = records
            .filter { it.patrolId > 0 }
            .sortedWith(
                compareBy<ForestPatrolRecordCandidate> { it.startDate }
                    .thenBy { it.patrolId }
            )
        if (sorted.isEmpty()) {
            return null
        }
        sorted.firstOrNull { it.patrolId in missingPiecePatrolIds }?.let {
            return ForestPatrolTarget(
                it.patrolId,
                ForestPatrolTargetReason.MISSING_PIECES
            )
        }
        sorted.firstOrNull { it.hasUnreachedNodes }?.let {
            return ForestPatrolTarget(
                it.patrolId,
                ForestPatrolTargetReason.UNREACHED_NODES
            )
        }
        val latest = sorted.last()
        return ForestPatrolTarget(
            latest.patrolId,
            ForestPatrolTargetReason.LATEST_LOOP
        )
    }

    fun selectDispatchAnimal(
        animalProps: JSONArray
    ): ForestAnimalDispatchSelection? {
        var best: ForestAnimalDispatchSelection? = null
        for (index in 0 until animalProps.length()) {
            val animalProp = animalProps.optJSONObject(index) ?: continue
            val holdsNum = animalProp.optJSONObject("main")
                ?.optInt("holdsNum", 0)
                ?: 0
            if (holdsNum <= 0) {
                continue
            }
            val estimatedEnergy = estimateAnimalEnergy(animalProp)
            val current = best
            if (
                current == null ||
                holdsNum > current.holdsNum ||
                holdsNum == current.holdsNum &&
                estimatedEnergy > current.estimatedEnergy
            ) {
                best = ForestAnimalDispatchSelection(
                    index = index,
                    holdsNum = holdsNum,
                    estimatedEnergy = estimatedEnergy
                )
            }
        }
        return best
    }

    private fun isLimitedAnimal(animal: JSONObject): Boolean {
        if (
            animal.optBoolean("limited", false) ||
            animal.optBoolean("limit", false) ||
            animal.optBoolean("special", false)
        ) {
            return true
        }
        val extInfo = animal.optJSONObject("extInfo") ?: return false
        return extInfo.optBoolean("limited", false) ||
            extInfo.optBoolean("limit", false) ||
            extInfo.optBoolean("special", false) ||
            extInfo.optString("shortDesc").contains("限定")
    }

    private fun estimateAnimalEnergy(animalProp: JSONObject): Int {
        val partner = animalProp.optJSONObject("partner")
        val main = animalProp.optJSONObject("main")
        return maxOf(
            extractEnergy(partner),
            extractEnergy(main),
            extractEnergy(parseExtInfo(partner)),
            extractEnergy(parseExtInfo(main))
        )
    }

    private fun extractEnergy(container: JSONObject?): Int {
        if (container == null) {
            return 0
        }
        val ability = container.optJSONObject("robAbility")
            ?: container.optJSONObject("animal")
                ?.optJSONObject("robAbility")
            ?: return 0
        return maxOf(
            ability.optInt("robEnergyInDaily", 0),
            ability.optInt("robEnergyInRound", 0)
        )
    }

    private fun parseExtInfo(container: JSONObject?): JSONObject? {
        val extInfo = container?.opt("extInfo") ?: return null
        return when (extInfo) {
            is JSONObject -> extInfo
            is String -> runCatching {
                extInfo.takeIf { it.trim().startsWith("{") }
                    ?.let(::JSONObject)
            }.getOrNull()

            else -> null
        }
    }
}
