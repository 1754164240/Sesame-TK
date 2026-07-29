package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GameCenterP2eRewardWorkflowTest {

    @Test
    fun `P2E签到ACK后今日状态未刷新必须重试`() {
        var queryCalls = 0
        val workflow = workflow(
            queryHome = {
                queryCalls++
                signResponse("UN_SIGNED")
            }
        )

        val outcome = workflow.signIn()

        assertEquals(2, queryCalls)
        assertEquals(GameCenterRewardState.RETRY, outcome.state)
    }

    @Test
    fun `P2E签到回查SIGNED才确认`() {
        val responses = ArrayDeque(
            listOf(signResponse("UN_SIGNED"), signResponse("SIGNED"))
        )
        val workflow = workflow(queryHome = { responses.removeFirst() })

        assertEquals(GameCenterRewardState.CONFIRMED, workflow.signIn().state)
    }

    @Test
    fun `P2E抽金币ACK后状态未刷新必须重试`() {
        val workflow = workflow(queryHome = { drawResponse("NOT_DRAWN") })

        assertEquals(GameCenterRewardState.RETRY, workflow.drawGold().state)
    }

    @Test
    fun `P2E抽金币回查DRAWN才确认`() {
        val responses = ArrayDeque(
            listOf(drawResponse("NOT_DRAWN"), drawResponse("DRAWN"))
        )
        val workflow = workflow(queryHome = { responses.removeFirst() })

        assertEquals(GameCenterRewardState.CONFIRMED, workflow.drawGold().state)
    }

    @Test
    fun `P2E现金档位工作流只返回查询快照`() {
        val workflow = workflow(
            queryCashTiers = {
                """
                {"success":true,"data":{
                  "assetModuleVO":{"goldAmount":"900"},
                  "cashExchangeModule":{"prizes":[{
                    "prizeConfigId":"cash-1",
                    "prizeAmount":"1.00",
                    "prizeStatus":"CAN_EXG"
                  }]}
                }}
                """.trimIndent()
            }
        )

        val snapshot = workflow.queryCashTiers()

        assertEquals(900L, snapshot.goldAmount)
        assertEquals("cash-1", snapshot.tiers.single().tierId)
    }

    private fun workflow(
        queryHome: () -> String = { signResponse("UN_SIGNED") },
        queryCashTiers: () -> String = { """{"success":true,"data":{}}""" }
    ): GameCenterP2eRewardWorkflow {
        return GameCenterP2eRewardWorkflow(
            queryHome = queryHome,
            signInAction = { successResponse() },
            drawGoldAction = { successResponse() },
            queryCashTierPage = queryCashTiers,
            isActionSuccess = { response ->
                JSONObject(response).optBoolean("success", false)
            }
        )
    }

    private fun signResponse(status: String): String {
        return """
            {"success":true,"data":{"signUpModuleVO":{
              "date":"2026-07-28",
              "index":1,
              "signSequenceId":"sequence-1",
              "signRecordVOList":[{"isToday":true,"signUpStatus":"$status"}]
            }}}
        """.trimIndent()
    }

    private fun drawResponse(status: String): String {
        return """{"success":true,"data":{"drawGoldCoinModuleVO":{"status":"$status"}}}"""
    }

    private fun successResponse(): String = """{"success":true}"""
}
