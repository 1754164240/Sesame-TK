package fansirsqi.xposed.sesame.task.antSports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntSportsStepSyncTest {

    private class RecordingClient(
        private val querySteps: ArrayDeque<Int>
    ) : FamilyWalkStepSyncClient {
        val signInfoSteps = mutableListOf<Int>()
        val homeSteps = mutableListOf<Int>()

        override fun queryDonationSteps(): String {
            val step = querySteps.removeFirst()
            return """{"success":true,"resultCode":"SUCCESS","stepCount":$step}"""
        }

        override fun walkDonateSignInfo(step: Int): String {
            signInfoSteps.add(step)
            return """{"isSuccess":true,"resultCode":"100"}"""
        }

        override fun donateWalkHome(step: Int): String {
            homeSteps.add(step)
            return """
                {
                  "isSuccess": true,
                  "resultCode": "100",
                  "walkDonateHomeModel": {
                    "walkUserInfoModel": {
                      "userStepsToday": $step
                    }
                  }
                }
            """.trimIndent()
        }
    }

    @Test
    fun `8点前也允许把本地步数改为配置步数`() {
        assertTrue(AntSportsStepSync.shouldOverrideDailyStep(originStep = 16, targetStep = 22000))
    }

    @Test
    fun `本地步数已达到配置步数时不覆盖`() {
        assertFalse(AntSportsStepSync.shouldOverrideDailyStep(originStep = 22000, targetStep = 22000))
        assertFalse(AntSportsStepSync.shouldOverrideDailyStep(originStep = 23000, targetStep = 22000))
    }

    @Test
    fun `家庭捐步流程刷新后必须再次查询确认目标步数`() {
        val client = RecordingClient(ArrayDeque(listOf(1200, 23000)))

        val result = FamilyWalkStepSync.sync(targetStep = 22000, client = client)

        assertEquals(FamilyWalkStepSyncOutcome.VERIFIED, result.outcome)
        assertEquals(23000, result.verifiedStep)
        assertEquals(listOf(22000), client.signInfoSteps)
        assertEquals(listOf(22000), client.homeSteps)
    }

    @Test
    fun `家庭捐步二次查询仍不足时不得视为同步成功`() {
        val client = RecordingClient(ArrayDeque(listOf(1200, 1500)))

        val result = FamilyWalkStepSync.sync(targetStep = 22000, client = client)

        assertEquals(FamilyWalkStepSyncOutcome.UNVERIFIED, result.outcome)
        assertEquals(1500, result.verifiedStep)
    }

    @Test
    fun `服务端已有目标步数时不重复刷新家庭捐步首页`() {
        val client = RecordingClient(ArrayDeque(listOf(22587)))

        val result = FamilyWalkStepSync.sync(targetStep = 22000, client = client)

        assertEquals(FamilyWalkStepSyncOutcome.VERIFIED, result.outcome)
        assertTrue(client.signInfoSteps.isEmpty())
        assertTrue(client.homeSteps.isEmpty())
    }

    @Test
    fun `刷新接口明确失败时停止后续调用`() {
        var homeCalled = false
        val client = object : FamilyWalkStepSyncClient {
            override fun queryDonationSteps(): String {
                return """{"success":true,"resultCode":"SUCCESS","stepCount":1200}"""
            }

            override fun walkDonateSignInfo(step: Int): String {
                return """{"isSuccess":false,"resultCode":"100","resultDesc":"处理失败"}"""
            }

            override fun donateWalkHome(step: Int): String {
                homeCalled = true
                return """{"isSuccess":true,"resultCode":"100"}"""
            }
        }

        val result = FamilyWalkStepSync.sync(targetStep = 22000, client = client)

        assertEquals(FamilyWalkStepSyncOutcome.FAILED, result.outcome)
        assertFalse(homeCalled)
    }

    @Test
    fun `家庭捐步接口返回数值200时视为成功`() {
        val client = object : FamilyWalkStepSyncClient {
            private var queryCount = 0

            override fun queryDonationSteps(): String {
                queryCount += 1
                val step = if (queryCount == 1) 1200 else 23000
                return """{"resultCode":200,"stepCount":$step}"""
            }

            override fun walkDonateSignInfo(step: Int): String {
                return """{"resultCode":200}"""
            }

            override fun donateWalkHome(step: Int): String {
                return """{"resultCode":"200"}"""
            }
        }

        val result = FamilyWalkStepSync.sync(targetStep = 22000, client = client)

        assertEquals(FamilyWalkStepSyncOutcome.VERIFIED, result.outcome)
        assertEquals(23000, result.verifiedStep)
    }
}
