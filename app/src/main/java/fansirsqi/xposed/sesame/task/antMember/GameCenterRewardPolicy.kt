package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONArray
import org.json.JSONObject

data class GameCenterSignInSnapshot(
    val recognized: Boolean,
    val signedIn: Boolean
)

data class GameCenterPointBallSnapshot(
    val recognized: Boolean,
    val pendingIds: Set<String>,
    val totalAmount: Long?
)

data class GameCenterStickerSnapshot(
    val recognized: Boolean,
    val receivableIds: Set<String>,
    val hasUnidentifiedItems: Boolean
)

data class GameCenterCashTier(
    val tierId: String,
    val cashAmount: String,
    val status: String
)

data class GameCenterCashTierSnapshot(
    val recognized: Boolean,
    val tiers: List<GameCenterCashTier>,
    val goldAmount: Long?
)

data class GameCenterP2eSignInSnapshot(
    val recognized: Boolean,
    val riskLimited: Boolean,
    val signedIn: Boolean,
    val date: String,
    val index: Int,
    val signSequenceId: String
)

data class GameCenterP2eDrawSnapshot(
    val recognized: Boolean,
    val riskLimited: Boolean,
    val status: String
)

data class BillBlockWorldSnapshot(
    val recognized: Boolean,
    val currentChapterId: String,
    val seasonId: String,
    val chapterStatus: String,
    val pendingBlockIds: Set<String>,
    val placedBlockCount: Int
)

object GameCenterRewardPolicy {
    private val successCodes = setOf("SUCCESS", "100", "200", "0")

    fun parseSignIn(response: String): GameCenterSignInSnapshot {
        val containers = successfulContainers(response)
            ?: return GameCenterSignInSnapshot(false, false)
        for (container in containers) {
            val module = container.optJSONObject("signInBallModule") ?: continue
            if (!module.has("signInStatus")) {
                continue
            }
            return GameCenterSignInSnapshot(
                recognized = true,
                signedIn = module.optBoolean("signInStatus", false)
            )
        }
        return GameCenterSignInSnapshot(false, false)
    }

    fun isSignInConfirmed(response: String): Boolean {
        val snapshot = parseSignIn(response)
        return snapshot.recognized && snapshot.signedIn
    }

    fun parsePointBalls(response: String): GameCenterPointBallSnapshot {
        val containers = successfulContainers(response)
            ?: return GameCenterPointBallSnapshot(false, emptySet(), null)
        for (container in containers) {
            if (!container.has("pointBallList")) {
                continue
            }
            val source = container.optJSONArray("pointBallList")
                ?: return GameCenterPointBallSnapshot(false, emptySet(), null)
            val ids = linkedSetOf<String>()
            for (index in 0 until source.length()) {
                val pointBall = source.optJSONObject(index)
                val id = pointBall?.firstString(
                    "pointBallId",
                    "ballId",
                    "id",
                    "traceId"
                ).orEmpty()
                ids += id.ifBlank { "__unknown_$index" }
            }
            return GameCenterPointBallSnapshot(
                recognized = true,
                pendingIds = ids,
                totalAmount = findLong(containers, "totalAmount", "totalPoint", "pointAmount")
            )
        }
        return GameCenterPointBallSnapshot(false, emptySet(), null)
    }

    fun isPointBallCollectionConfirmed(
        previous: GameCenterPointBallSnapshot,
        refreshed: GameCenterPointBallSnapshot
    ): Boolean {
        if (!previous.recognized || !refreshed.recognized) {
            return false
        }
        if (refreshed.pendingIds.isEmpty()) {
            return true
        }
        val before = previous.totalAmount
        val after = refreshed.totalAmount
        return before != null && after != null && after > before
    }

    fun parseStickers(response: String): GameCenterStickerSnapshot {
        val containers = successfulContainers(response)
            ?: return GameCenterStickerSnapshot(false, emptySet(), false)
        for (container in containers) {
            if (!container.has("canReceivePageList")) {
                continue
            }
            val pages = container.optJSONArray("canReceivePageList")
                ?: return GameCenterStickerSnapshot(false, emptySet(), false)
            val ids = linkedSetOf<String>()
            var hasUnidentifiedItems = false
            for (pageIndex in 0 until pages.length()) {
                val stickers = pages.optJSONObject(pageIndex)
                    ?.optJSONArray("stickerCanReceiveList")
                    ?: continue
                for (stickerIndex in 0 until stickers.length()) {
                    val sticker = stickers.optJSONObject(stickerIndex)
                    val id = sticker?.firstString(
                        "id",
                        "stickerRecordId",
                        "configId"
                    ).orEmpty()
                    if (id.isBlank()) {
                        hasUnidentifiedItems = true
                    } else {
                        ids += id
                    }
                }
            }
            return GameCenterStickerSnapshot(true, ids, hasUnidentifiedItems)
        }
        return GameCenterStickerSnapshot(false, emptySet(), false)
    }

    fun isStickerCollectionConfirmed(
        response: String,
        targetIds: Set<String>
    ): Boolean {
        if (targetIds.isEmpty()) {
            return false
        }
        val snapshot = parseStickers(response)
        return snapshot.recognized &&
            !snapshot.hasUnidentifiedItems &&
            targetIds.none(snapshot.receivableIds::contains)
    }

    fun parseCashTiers(response: String): GameCenterCashTierSnapshot {
        val containers = successfulContainers(response)
            ?: return GameCenterCashTierSnapshot(false, emptyList(), null)
        for (container in containers) {
            val cashModule = container.optJSONObject("cashExchangeModule")
            if (cashModule?.has("prizes") != true) {
                continue
            }
            val source = cashModule.optJSONArray("prizes")
                ?: return GameCenterCashTierSnapshot(false, emptyList(), null)
            val tiers = buildList {
                for (index in 0 until source.length()) {
                    val tier = source.optJSONObject(index) ?: continue
                    val tierId = tier.firstString(
                        "prizeConfigId",
                        "tierId",
                        "id",
                        "configId"
                    )
                    if (tierId.isBlank()) {
                        continue
                    }
                    add(
                        GameCenterCashTier(
                            tierId = tierId,
                            cashAmount = tier.firstString(
                                "prizeAmount",
                                "cashAmount",
                                "amount",
                                "money"
                            ),
                            status = tier.firstString(
                                "prizeStatus",
                                "status",
                                "tierStatus"
                            )
                        )
                    )
                }
            }
            return GameCenterCashTierSnapshot(
                recognized = true,
                tiers = tiers,
                goldAmount = container.optJSONObject("assetModuleVO")
                    ?.firstLong("goldAmount")
            )
        }
        return GameCenterCashTierSnapshot(false, emptyList(), null)
    }

    fun parseP2eSignIn(response: String): GameCenterP2eSignInSnapshot {
        val containers = successfulContainers(response)
            ?: return unknownP2eSignIn()
        val riskLimited = isP2eRiskLimited(containers)
        for (container in containers) {
            val module = container.optJSONObject("signUpModuleVO") ?: continue
            val date = module.optString("date")
            val todayRecord = findP2eTodaySignRecord(module)
            return GameCenterP2eSignInSnapshot(
                recognized = true,
                riskLimited = riskLimited,
                signedIn = todayRecord?.optString("signUpStatus")
                    .equals("SIGNED", true),
                date = date,
                index = module.optInt("index", 0),
                signSequenceId = module.optString("signSequenceId")
            )
        }
        return if (riskLimited) {
            unknownP2eSignIn(recognized = true, riskLimited = true)
        } else {
            unknownP2eSignIn()
        }
    }

    fun isP2eSignInConfirmed(response: String): Boolean {
        val snapshot = parseP2eSignIn(response)
        return snapshot.recognized && snapshot.signedIn
    }

    fun parseP2eDraw(response: String): GameCenterP2eDrawSnapshot {
        val containers = successfulContainers(response)
            ?: return GameCenterP2eDrawSnapshot(false, false, "")
        val riskLimited = isP2eRiskLimited(containers)
        for (container in containers) {
            val module = container.optJSONObject("drawGoldCoinModuleVO") ?: continue
            if (!module.has("status")) {
                continue
            }
            return GameCenterP2eDrawSnapshot(
                recognized = true,
                riskLimited = riskLimited,
                status = module.optString("status").uppercase()
            )
        }
        return GameCenterP2eDrawSnapshot(
            recognized = riskLimited,
            riskLimited = riskLimited,
            status = ""
        )
    }

    fun isP2eDrawConfirmed(snapshot: GameCenterP2eDrawSnapshot): Boolean {
        return snapshot.recognized && snapshot.status == "DRAWN"
    }

    fun parseBillBlockWorld(response: String): BillBlockWorldSnapshot {
        val containers = successfulContainers(response)
            ?: return unknownBillBlockWorld()
        for (container in containers) {
            val canvas = container.optJSONObject("canvas") ?: continue
            val chapters = container.optJSONArray("chapterTasks")
                ?: return unknownBillBlockWorld()
            val pendingBlocks = container.optJSONArray("pendingBlocks")
                ?: return unknownBillBlockWorld()
            val placedBlocks = container.optJSONArray("placedBlocks")
                ?: return unknownBillBlockWorld()
            val chapterId = canvas.optString("currentChapterId")
            val seasonId = canvas.optString("seasonId")
            if (
                chapterId.isBlank() ||
                seasonId.isBlank() ||
                canvas.optInt("canvasWidth", 0) <= 0 ||
                canvas.optInt("canvasLength", 0) <= 0
            ) {
                return unknownBillBlockWorld()
            }
            val pendingIds = parseBlockIds(pendingBlocks)
                ?: return unknownBillBlockWorld()
            val placedIds = parseBlockIds(placedBlocks)
                ?: return unknownBillBlockWorld()
            val chapterStatus = findChapterStatus(chapters, chapterId)
            return BillBlockWorldSnapshot(
                recognized = true,
                currentChapterId = chapterId,
                seasonId = seasonId,
                chapterStatus = chapterStatus,
                pendingBlockIds = pendingIds,
                placedBlockCount = placedIds.size
            )
        }
        return unknownBillBlockWorld()
    }

    private fun successfulContainers(response: String): List<JSONObject>? {
        if (response.isBlank()) {
            return null
        }
        val root = runCatching { JSONObject(response) }.getOrNull() ?: return null
        val containers = responseContainers(root)
        return containers.takeIf(::isRpcSuccess)
    }

    private fun responseContainers(root: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val pending = ArrayDeque<JSONObject>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            result += current
            listOf("data", "result", "resData").forEach { key ->
                current.optJSONObject(key)?.let(pending::addLast)
            }
        }
        return result
    }

    private fun isRpcSuccess(containers: List<JSONObject>): Boolean {
        var markerFound = false
        for (container in containers) {
            if (container.has("success")) {
                markerFound = true
                if (!container.optBoolean("success", false)) {
                    return false
                }
            }
            for (key in listOf("resultCode", "code")) {
                if (!container.has(key)) {
                    continue
                }
                markerFound = true
                if (container.optString(key).trim().uppercase() !in successCodes) {
                    return false
                }
            }
        }
        return markerFound
    }

    private fun findLong(
        containers: List<JSONObject>,
        vararg keys: String
    ): Long? {
        for (container in containers) {
            for (key in keys) {
                if (!container.has(key)) {
                    continue
                }
                val raw = container.opt(key)
                when (raw) {
                    is Number -> return raw.toLong()
                    is String -> raw.toLongOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    private fun findP2eTodaySignRecord(module: JSONObject): JSONObject? {
        val signDate = module.optString("date")
        val records = module.optJSONArray("signRecordVOList") ?: return null
        var dateMatched: JSONObject? = null
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (record.optBoolean("isToday", false)) {
                return record
            }
            if (signDate.isNotBlank() && signDate == record.optString("signDate")) {
                dateMatched = record
            }
        }
        return dateMatched
    }

    private fun isP2eRiskLimited(containers: List<JSONObject>): Boolean {
        return containers.any { container ->
            container.optBoolean("hitRiskControl", false) ||
                container.optBoolean("hitFourControlLimit", false) ||
                container.optString("hitRiskControlMsg").isNotBlank()
        }
    }

    private fun unknownP2eSignIn(
        recognized: Boolean = false,
        riskLimited: Boolean = false
    ): GameCenterP2eSignInSnapshot {
        return GameCenterP2eSignInSnapshot(
            recognized = recognized,
            riskLimited = riskLimited,
            signedIn = false,
            date = "",
            index = 0,
            signSequenceId = ""
        )
    }

    private fun parseBlockIds(blocks: JSONArray): Set<String>? {
        val ids = linkedSetOf<String>()
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: return null
            val recordId = block.optString("blockRecordId")
            if (recordId.isBlank()) {
                return null
            }
            ids += recordId
        }
        return ids
    }

    private fun findChapterStatus(
        chapters: JSONArray,
        chapterId: String
    ): String {
        for (index in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(index) ?: continue
            if (chapter.optString("chapterId") != chapterId) {
                continue
            }
            return chapter.optString("status")
                .ifBlank { chapter.optJSONObject("task")?.optString("status").orEmpty() }
                .uppercase()
        }
        return ""
    }

    private fun unknownBillBlockWorld(): BillBlockWorldSnapshot {
        return BillBlockWorldSnapshot(
            recognized = false,
            currentChapterId = "",
            seasonId = "",
            chapterStatus = "",
            pendingBlockIds = emptySet(),
            placedBlockCount = 0
        )
    }

    private fun JSONObject.firstString(vararg keys: String): String {
        for (key in keys) {
            val value = optString(key).trim()
            if (value.isNotBlank()) {
                return value
            }
        }
        return ""
    }

    private fun JSONObject.firstLong(vararg keys: String): Long? {
        for (key in keys) {
            if (!has(key)) {
                continue
            }
            when (val raw = opt(key)) {
                is Number -> return raw.toLong()
                is String -> raw.toLongOrNull()?.let { return it }
            }
        }
        return null
    }
}
