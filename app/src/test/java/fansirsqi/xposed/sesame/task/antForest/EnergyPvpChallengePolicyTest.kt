package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnergyPvpChallengePolicyTest {

    @Test
    fun `可靠待领奖信号返回领取`() {
        assertEquals(
            EnergyPvpDecision.CLAIM,
            EnergyPvpChallengePolicy.decide(
                entry(hasEntry = true, hasReward = true, status = "SETTLED"),
                home()
            )
        )
        assertEquals(
            EnergyPvpDecision.CLAIM,
            EnergyPvpChallengePolicy.decide(
                null,
                home(waitRewardCount = 1)
            )
        )
        assertEquals(
            EnergyPvpDecision.CLAIM,
            EnergyPvpChallengePolicy.decide(
                entry(hasEntry = true, hasReward = false, status = "SETTLED"),
                home(waitRewardCount = 1)
            )
        )
        assertEquals(
            EnergyPvpDecision.CLAIM,
            EnergyPvpChallengePolicy.decide(
                entry(hasEntry = true, hasReward = false, status = "SETTLED"),
                home(
                    previousRecord = JSONObject()
                        .put("battleStatus", "SETTLED")
                        .put(
                            "rewardDetailList",
                            JSONArray().put(
                                JSONObject()
                                    .put("rewardName", "能量")
                                    .put("rewardStatus", "UNRECEIVED")
                            )
                        )
                )
            )
        )
    }

    @Test
    fun `匹配进行和结算中均保留重试`() {
        for (status in listOf("MATCHING", "PROGRESSING", "SETTLING")) {
            assertEquals(
                EnergyPvpDecision.RETRY_LATER,
                EnergyPvpChallengePolicy.decide(
                    entry(hasEntry = true, hasReward = false, status = status),
                    home(currentRecord = JSONObject().put("battleStatus", status))
                )
            )
        }
    }

    @Test
    fun `明确无活动无奖励才返回完成`() {
        assertEquals(
            EnergyPvpDecision.DONE,
            EnergyPvpChallengePolicy.decide(
                entry(hasEntry = false, hasReward = false, status = ""),
                home()
            )
        )
        assertEquals(
            EnergyPvpDecision.DONE,
            EnergyPvpChallengePolicy.decide(
                entry(hasEntry = true, hasReward = false, status = "SETTLED"),
                home(
                    previousRecord = JSONObject()
                        .put("battleStatus", "SETTLED")
                        .put(
                            "rewardDetailList",
                            JSONArray().put(
                                JSONObject().put("rewardStatus", "RECEIVED")
                            )
                        )
                )
            )
        )
    }

    @Test
    fun `空响应失败响应和未知结构不能写完成`() {
        assertEquals(
            EnergyPvpDecision.RETRY_LATER,
            EnergyPvpChallengePolicy.decide(null, home())
        )
        assertEquals(
            EnergyPvpDecision.RETRY_LATER,
            EnergyPvpChallengePolicy.decide(entry(false, false, ""), null)
        )
        assertEquals(
            EnergyPvpDecision.RETRY_LATER,
            EnergyPvpChallengePolicy.decide(
                JSONObject("""{"success":false}"""),
                home()
            )
        )
        assertEquals(
            EnergyPvpDecision.RETRY_LATER,
            EnergyPvpChallengePolicy.decide(
                JSONObject("""{"success":true}"""),
                home()
            )
        )
        assertEquals(
            EnergyPvpDecision.RETRY_LATER,
            EnergyPvpChallengePolicy.decide(
                entry(true, false, "UNKNOWN"),
                home()
            )
        )
    }

    @Test
    fun `识别重复领取等终态结果`() {
        assertTrue(EnergyPvpChallengePolicy.isTerminalClaimResult("", "奖励已领取"))
        assertTrue(EnergyPvpChallengePolicy.isTerminalClaimResult("", "奖励已发放"))
        assertTrue(EnergyPvpChallengePolicy.isTerminalClaimResult("", "无可领取奖励"))
        assertTrue(EnergyPvpChallengePolicy.isTerminalClaimResult("", "请勿重复领取"))
        assertFalse(EnergyPvpChallengePolicy.isTerminalClaimResult("SYSTEM_BUSY", "系统繁忙"))
    }

    @Test
    fun `奖励摘要兼容能量勋章和未知奖励`() {
        val rewards = JSONArray()
            .put(
                JSONObject()
                    .put("rewardName", "绿色能量")
                    .put("rewardType", "ENERGY")
                    .put("energy", 20)
            )
            .put(
                JSONObject()
                    .put("rewardName", "挑战勋章")
                    .put("rewardType", "MEDAL")
            )
            .put(JSONObject().put("rewardId", "reward-x"))

        assertEquals(
            "绿色能量(20g)、挑战勋章(MEDAL)、reward-x",
            EnergyPvpChallengePolicy.summarizeRewards(rewards)
        )
        assertEquals("无", EnergyPvpChallengePolicy.summarizeRewards(null))
    }

    @Test
    fun `配置默认关闭且只接入正常主页与传统好友扫描之间`() {
        val fields = AntForest().fields
        assertFalse(fields["energyPvpChallenge"]?.value as Boolean)

        val source = File(
            "src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt"
        ).readText()
        val selfHomeIndex = source.indexOf("val selfHomeObj = run")
        val pvpIndex = source.indexOf("handleEnergyPvpChallenge()", selfHomeIndex)
        val friendIndex = source.indexOf("collectFriendEnergyCoroutine()", pvpIndex)

        assertTrue(selfHomeIndex >= 0)
        assertTrue(pvpIndex > selfHomeIndex)
        assertTrue(friendIndex > pvpIndex)

        val energyLoop = source.substring(
            source.indexOf("private fun startEnergyCollectionLoop()"),
            source.indexOf("private fun createSafeIntervalLimit")
        )
        assertFalse(energyLoop.contains("handleEnergyPvpChallenge()"))
    }

    private fun entry(
        hasEntry: Boolean,
        hasReward: Boolean,
        status: String
    ): JSONObject {
        return JSONObject()
            .put("success", true)
            .put(
                "combineHandlerVOMap",
                JSONObject().put(
                    "energyPvpInfo",
                    JSONObject()
                        .put("hasEntry", hasEntry)
                        .put("hasReward", hasReward)
                        .put("battleStatus", status)
                )
            )
    }

    private fun home(
        waitRewardCount: Int = 0,
        currentRecord: JSONObject? = null,
        previousRecord: JSONObject? = null
    ): JSONObject {
        return JSONObject()
            .put("success", true)
            .put("waitToReceiveRecordCount", 0)
            .put("waitToReceiveRewardCount", waitRewardCount)
            .apply {
                currentRecord?.let { put("currentEnergyPvpBattleRecord", it) }
                previousRecord?.let { put("previousEnergyPvpBattleRecord", it) }
            }
    }
}
