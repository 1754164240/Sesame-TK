package fansirsqi.xposed.sesame.task.antFishPond

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.IdMapManager
import fansirsqi.xposed.sesame.util.maps.UserMap
import fansirsqi.xposed.sesame.util.maps.VipDataIdMap

class AntFishPond : ModelTask() {
    private val fishPondTask =
        BooleanModelField("fishPondTask", "鱼池任务 | 签到与领奖", false)
    private val autoFish =
        BooleanModelField("autoFish", "自动钓鱼 | 开启", false)
    private val fishDailyLimit =
        IntegerModelField("fishDailyLimit", "自动钓鱼 | 每日次数", 30, 0, 200)

    override fun getName(): String = "福气鱼池"

    override fun getGroup(): ModelGroup = ModelGroup.FOREST

    override fun getIcon(): String = "AntOcean.png"

    override fun getFields(): ModelFields {
        return ModelFields().apply {
            addField(fishPondTask)
            addField(autoFish)
            addField(fishDailyLimit)
        }
    }

    override fun runJava() {
        val taskEnabled = fishPondTask.value == true
        val autoFishEnabled = autoFish.value == true
        if (!taskEnabled && !autoFishEnabled) {
            return
        }

        try {
            val riskToken = loadRiskToken()
            if (autoFishEnabled && riskToken.isNullOrBlank() &&
                !Status.hasFlagToday(StatusFlags.FLAG_ANTFISHPOND_RISK_TOKEN_MISSING)
            ) {
                Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_RISK_TOKEN_MISSING)
                Log.other(
                    TAG,
                    "缺少 fishpondAngle riskToken，跳过自动钓鱼；请先手动钓鱼以捕获令牌"
                )
            }

            val todayCount =
                Status.getIntFlagToday(StatusFlags.FLAG_ANTFISHPOND_FISH_COUNT) ?: 0
            val result = FishPondWorkflow(AntFishPondRpcGateway()).run(
                taskEnabled = taskEnabled,
                autoFishEnabled = autoFishEnabled,
                todayFishCount = todayCount,
                dailyLimit = fishDailyLimit.value ?: 30,
                riskToken = riskToken,
                onFishConfirmed = { currentCount ->
                    Status.setIntFlagToday(
                        StatusFlags.FLAG_ANTFISHPOND_FISH_COUNT,
                        currentCount
                    )
                }
            )

            if (result.confirmedFishCount > 0) {
                Log.other(TAG, "本轮自动钓鱼 ${result.confirmedFishCount} 次")
            }
            if (result.retryNeeded) {
                Log.other(TAG, "鱼池响应暂不完整，本轮安全停止，等待后续重试")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "福气鱼池执行异常", e)
        }
    }

    private fun loadRiskToken(): String? {
        val userId = UserMap.currentUid
        if (userId.isNullOrBlank()) {
            return null
        }
        val vipData = IdMapManager.getInstance(VipDataIdMap::class.java)
        vipData.load(userId)
        return vipData.get("antfishpond_riskToken")
    }

    companion object {
        private const val TAG = "AntFishPond"
    }
}
