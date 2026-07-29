package fansirsqi.xposed.sesame.task.antForest

enum class MultiplierOutcome {
    CONFIRMED,
    NO_ACTION,
    RETRY
}

data class ForestMultiplierRunResult(
    val outcome: MultiplierOutcome,
    val exchanged: Boolean,
    val used: Boolean,
    val message: String,
    val confirmedActive: ActiveMultiplierSnapshot? = null
)

class ForestMultiplierWorkflow(
    private val queryHome: () -> String,
    private val queryBag: () -> String,
    private val exchangeCard: () -> String,
    private val useCard: (MultiplierCardCandidate) -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun run(
        enabled: Boolean,
        allowExchange: Boolean,
        limitedOnly: Boolean,
        replaceRemainDays: Int,
        regularUseAllowed: Boolean = true
    ): ForestMultiplierRunResult {
        if (!enabled) {
            return noAction("收好友 N 倍卡已关闭")
        }
        val now = nowMillis()
        val active = queryActive(now)
            ?: return retry("主页使用中道具状态不确定")
        var bag = queryBagSnapshot()
            ?: return retry("背包状态不确定")
        var selected = ForestMultiplierPolicy.selectCandidate(
            bag = bag,
            active = active,
            limitedOnly = limitedOnly,
            replaceRemainDays = replaceRemainDays,
            nowMillis = now,
            regularUseAllowed = regularUseAllowed
        )
        var exchanged = false

        if (selected == null) {
            if (
                active.state == ActiveMultiplierState.ACTIVE ||
                bag.cards.isNotEmpty()
            ) {
                return noAction("现有卡未满足安全替换条件")
            }
            if (!allowExchange) {
                return noAction("背包无卡且未启用补兑")
            }
            val exchangeResponse = runCatching { exchangeCard() }
                .getOrDefault("")
            if (!ForestMultiplierPolicy.isActionSuccess(exchangeResponse)) {
                return retry("补兑请求失败")
            }
            exchanged = true
            bag = queryBagSnapshot()
                ?: return retry("补兑后背包查询失败", exchanged)
            selected = ForestMultiplierPolicy.selectCandidate(
                bag = bag,
                active = active,
                limitedOnly = limitedOnly,
                replaceRemainDays = replaceRemainDays,
                nowMillis = now,
                regularUseAllowed = regularUseAllowed
            )
                ?: return retry("补兑后仍无可用卡", exchanged)
        }

        val useResponse = runCatching { useCard(selected) }
            .getOrDefault("")
        if (!ForestMultiplierPolicy.isActionSuccess(useResponse)) {
            return retry("使用请求失败", exchanged)
        }
        val refreshed = queryActive(now)
            ?: return retry("使用后主页状态不确定", exchanged, used = true)
        return if (
            ForestMultiplierPolicy.confirmsCandidate(
                refreshed,
                selected,
                now
            )
        ) {
            ForestMultiplierRunResult(
                outcome = MultiplierOutcome.CONFIRMED,
                exchanged = exchanged,
                used = true,
                message = "主页已确认目标倍率生效",
                confirmedActive = refreshed
            )
        } else {
            retry("使用后主页未确认目标倍率", exchanged, used = true)
        }
    }

    private fun queryActive(now: Long): ActiveMultiplierSnapshot? {
        val response = runCatching { queryHome() }.getOrDefault("")
        return ForestMultiplierPolicy
            .parseActiveMultiplier(response, now)
            .takeIf { it.state != ActiveMultiplierState.INCONCLUSIVE }
    }

    private fun queryBagSnapshot(): MultiplierBagSnapshot? {
        val response = runCatching { queryBag() }.getOrDefault("")
        return ForestMultiplierPolicy
            .parseBag(response)
            .takeIf { it.recognized }
    }

    private fun noAction(message: String): ForestMultiplierRunResult {
        return ForestMultiplierRunResult(
            outcome = MultiplierOutcome.NO_ACTION,
            exchanged = false,
            used = false,
            message = message
        )
    }

    private fun retry(
        message: String,
        exchanged: Boolean = false,
        used: Boolean = false
    ): ForestMultiplierRunResult {
        return ForestMultiplierRunResult(
            outcome = MultiplierOutcome.RETRY,
            exchanged = exchanged,
            used = used,
            message = message
        )
    }
}
