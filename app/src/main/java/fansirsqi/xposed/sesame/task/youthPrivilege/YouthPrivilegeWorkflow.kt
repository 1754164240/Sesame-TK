package fansirsqi.xposed.sesame.task.youthPrivilege

data class YouthForestReward(
    val queryTaskType: String,
    val rewardTaskType: String,
    val name: String
)

data class YouthCheckInResult(
    val confirmed: Boolean,
    val actionExecuted: Boolean,
    val retryNeeded: Boolean
)

data class YouthForestPropsResult(
    val confirmed: Boolean,
    val claimedCount: Int,
    val retryNeeded: Boolean
)

interface YouthPrivilegeRpcGateway {
    fun queryCheckIn(): String

    fun executeCheckIn(): String

    fun queryForestReward(queryTaskType: String): String

    fun claimForestReward(rewardTaskType: String): String
}

class YouthPrivilegeWorkflow(
    private val gateway: YouthPrivilegeRpcGateway
) {
    fun checkIn(onConfirmed: () -> Unit): YouthCheckInResult {
        val initialResponse = runCatching { gateway.queryCheckIn() }.getOrNull()
            ?: return YouthCheckInResult(false, false, true)

        return when (YouthPrivilegePolicy.checkInDecision(initialResponse)) {
            YouthCheckInDecision.CONFIRMED -> {
                onConfirmed()
                YouthCheckInResult(true, false, false)
            }

            YouthCheckInDecision.EXECUTE -> {
                if (runCatching { gateway.executeCheckIn() }.isFailure) {
                    return YouthCheckInResult(false, true, true)
                }
                val confirmed = runCatching { gateway.queryCheckIn() }
                    .getOrNull()
                    ?.let(YouthPrivilegePolicy::checkInDecision) ==
                    YouthCheckInDecision.CONFIRMED
                if (confirmed) {
                    onConfirmed()
                }
                YouthCheckInResult(confirmed, true, !confirmed)
            }

            YouthCheckInDecision.RETRY ->
                YouthCheckInResult(false, false, true)
        }
    }

    fun claimForestProps(
        rewards: List<YouthForestReward> = DEFAULT_FOREST_REWARDS,
        onAllConfirmed: () -> Unit
    ): YouthForestPropsResult {
        if (rewards.isEmpty()) {
            return YouthForestPropsResult(false, 0, true)
        }

        var claimedCount = 0
        var confirmedCount = 0
        for (reward in rewards) {
            val initialResponse = runCatching {
                gateway.queryForestReward(reward.queryTaskType)
            }.getOrNull() ?: continue

            when (
                YouthPrivilegePolicy.rewardDecision(
                    initialResponse,
                    reward.rewardTaskType
                )
            ) {
                YouthRewardDecision.CONFIRMED -> confirmedCount += 1
                YouthRewardDecision.CLAIM -> {
                    if (
                        runCatching {
                            gateway.claimForestReward(reward.rewardTaskType)
                        }.isFailure
                    ) {
                        continue
                    }
                    claimedCount += 1
                    val confirmed = runCatching {
                        gateway.queryForestReward(reward.queryTaskType)
                    }.getOrNull()?.let {
                        YouthPrivilegePolicy.rewardDecision(
                            it,
                            reward.rewardTaskType
                        )
                    } == YouthRewardDecision.CONFIRMED
                    if (confirmed) {
                        confirmedCount += 1
                    }
                }

                YouthRewardDecision.RETRY -> Unit
            }
        }

        val allConfirmed = confirmedCount == rewards.size
        if (allConfirmed) {
            onAllConfirmed()
        }
        return YouthForestPropsResult(
            confirmed = allConfirmed,
            claimedCount = claimedCount,
            retryNeeded = !allConfirmed
        )
    }

    fun queryTaskOverview(): Boolean {
        val response = runCatching { gateway.queryCheckIn() }.getOrNull()
            ?: return false
        return YouthPrivilegePolicy.isRpcSuccess(response)
    }

    companion object {
        val DEFAULT_FOREST_REWARDS = listOf(
            YouthForestReward(
                queryTaskType = "DNHZ_SL_college",
                rewardTaskType = "DAXUESHENG_SJK",
                name = "双击卡"
            ),
            YouthForestReward(
                queryTaskType = "DXS_BHZ",
                rewardTaskType = "NENGLIANGZHAO_20230807",
                name = "保护罩"
            ),
            YouthForestReward(
                queryTaskType = "DXS_JSQ",
                rewardTaskType = "JIASUQI_20230808",
                name = "加速器"
            )
        )
    }
}
