package fansirsqi.xposed.sesame.task.antFarm

enum class FarmNpcOutcome {
    CONFIRMED,
    NO_ACTION,
    RETRY
}

enum class FarmNpcAction {
    NONE,
    HIRE,
    SWITCH,
    CLAIM,
    CLAIM_AND_REHIRE
}

data class FarmNpcRunResult(
    val outcome: FarmNpcOutcome,
    val action: FarmNpcAction,
    val message: String
)

class AntFarmNpcWorkflow(
    private val queryFarm: suspend () -> String,
    private val hireNpc: suspend (String, String) -> String,
    private val sendBackNpc: suspend (FarmNpcAnimalSnapshot) -> String,
    private val waitForRefresh: suspend () -> Unit = {}
) {
    suspend fun run(
        targetAnimalId: String,
        source: String,
        rewardThreshold: Double?,
        onTargetPresent: suspend () -> Unit = {}
    ): FarmNpcRunResult {
        if (targetAnimalId.isBlank() || source.isBlank()) {
            return retry(FarmNpcAction.NONE, "目标NPC配置无效")
        }
        val initial = querySnapshot()
            ?: return retry(FarmNpcAction.NONE, "NPC状态查询失败")
        val current = initial.npc
        if (current == null) {
            return hireAndConfirm(
                targetAnimalId,
                source,
                FarmNpcAction.HIRE
            )
        }
        if (current.animalId != targetAnimalId) {
            if (!submitSendBack(current)) {
                return retry(FarmNpcAction.SWITCH, "旧NPC遣返请求失败")
            }
            val afterRemoval = querySnapshot()
                ?: return retry(FarmNpcAction.SWITCH, "遣返后状态查询失败")
            if (afterRemoval.npc != null) {
                return retry(FarmNpcAction.SWITCH, "遣返后旧NPC仍存在")
            }
            return hireAndConfirm(
                targetAnimalId,
                source,
                FarmNpcAction.SWITCH
            )
        }

        onTargetPresent()
        if (!AntFarmNpcPolicy.shouldClaimReward(
                initial,
                targetAnimalId,
                rewardThreshold
            )
        ) {
            return FarmNpcRunResult(
                FarmNpcOutcome.NO_ACTION,
                FarmNpcAction.NONE,
                "目标NPC仍在工作且产出未满"
            )
        }
        if (!submitSendBack(current)) {
            return retry(FarmNpcAction.CLAIM, "NPC产出领取请求失败")
        }
        val afterClaim = querySnapshot()
            ?: return retry(FarmNpcAction.CLAIM, "领取后状态查询失败")
        if (!AntFarmNpcPolicy.isRewardClaimConfirmed(initial, afterClaim)) {
            return retry(FarmNpcAction.CLAIM, "领取后产出状态未推进")
        }
        val afterNpc = afterClaim.npc
        if (afterNpc != null) {
            return if (afterNpc.animalId == targetAnimalId) {
                FarmNpcRunResult(
                    FarmNpcOutcome.CONFIRMED,
                    FarmNpcAction.CLAIM,
                    "NPC产出下降已确认"
                )
            } else {
                retry(FarmNpcAction.CLAIM, "领取后出现非目标NPC")
            }
        }
        return hireAndConfirm(
            targetAnimalId,
            source,
            FarmNpcAction.CLAIM_AND_REHIRE
        )
    }

    private suspend fun submitSendBack(
        npc: FarmNpcAnimalSnapshot
    ): Boolean {
        val response = runCatching { sendBackNpc(npc) }.getOrDefault("")
        if (!AntFarmNpcPolicy.isActionSuccess(response)) {
            return false
        }
        waitForRefresh()
        return true
    }

    private suspend fun hireAndConfirm(
        targetAnimalId: String,
        source: String,
        action: FarmNpcAction
    ): FarmNpcRunResult {
        val response = runCatching {
            hireNpc(targetAnimalId, source)
        }.getOrDefault("")
        if (!AntFarmNpcPolicy.isActionSuccess(response)) {
            return retry(action, "目标NPC雇佣请求失败")
        }
        waitForRefresh()
        val afterHire = querySnapshot()
            ?: return retry(action, "雇佣后状态查询失败")
        return if (afterHire.npc?.animalId == targetAnimalId) {
            FarmNpcRunResult(
                FarmNpcOutcome.CONFIRMED,
                action,
                "目标NPC已在主页确认"
            )
        } else {
            retry(action, "雇佣后目标NPC未出现")
        }
    }

    private suspend fun querySnapshot(): FarmNpcSnapshot? {
        val response = runCatching { queryFarm() }.getOrDefault("")
        return AntFarmNpcPolicy
            .parseSnapshot(response)
            .takeIf { it.recognized }
    }

    private fun retry(
        action: FarmNpcAction,
        message: String
    ): FarmNpcRunResult {
        return FarmNpcRunResult(
            FarmNpcOutcome.RETRY,
            action,
            message
        )
    }
}
