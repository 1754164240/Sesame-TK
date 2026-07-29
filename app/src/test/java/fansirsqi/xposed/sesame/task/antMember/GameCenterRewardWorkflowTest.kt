package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCenterRewardWorkflowTest {

    @Test
    fun `签到ACK后状态未刷新必须重试`() {
        var queryCalls = 0
        val workflow = workflow(
            querySignIn = {
                queryCalls++
                signInResponse(false)
            }
        )

        val outcome = workflow.signIn()

        assertEquals(2, queryCalls)
        assertEquals(GameCenterRewardState.RETRY, outcome.state)
    }

    @Test
    fun `签到回查到服务端已签到才确认`() {
        val responses = ArrayDeque(listOf(signInResponse(false), signInResponse(true)))
        val workflow = workflow(querySignIn = { responses.removeFirst() })

        val outcome = workflow.signIn()

        assertEquals(GameCenterRewardState.CONFIRMED, outcome.state)
    }

    @Test
    fun `乐豆ACK后目标仍存在且资产未增加必须重试`() {
        var collectCalls = 0
        val workflow = workflow(
            queryPointBalls = { pointBallResponse("ball-1", 120) },
            collectPointBalls = {
                collectCalls++
                successResponse()
            }
        )

        val outcome = workflow.collectPointBalls()

        assertEquals(1, collectCalls)
        assertEquals(GameCenterRewardState.RETRY, outcome.state)
    }

    @Test
    fun `乐豆回查待收列表为空才确认`() {
        val responses = ArrayDeque(
            listOf(pointBallResponse("ball-1", 120), pointBallResponse(null, 120))
        )
        val workflow = workflow(queryPointBalls = { responses.removeFirst() })

        val outcome = workflow.collectPointBalls()

        assertEquals(GameCenterRewardState.CONFIRMED, outcome.state)
    }

    @Test
    fun `贴纸ACK后目标仍存在必须重试`() {
        var receivedIds = emptySet<String>()
        val workflow = workflow(
            queryStickers = { stickerResponse("sticker-1") },
            receiveStickers = { ids ->
                receivedIds = ids
                successResponse()
            }
        )

        val outcome = workflow.collectStickers()

        assertEquals(setOf("sticker-1"), receivedIds)
        assertEquals(GameCenterRewardState.RETRY, outcome.state)
    }

    @Test
    fun `贴纸回查目标消失才确认`() {
        val responses = ArrayDeque(
            listOf(stickerResponse("sticker-1"), stickerResponse(null))
        )
        val workflow = workflow(queryStickers = { responses.removeFirst() })

        val outcome = workflow.collectStickers()

        assertEquals(GameCenterRewardState.CONFIRMED, outcome.state)
        assertTrue(outcome.targetIds.contains("sticker-1"))
    }

    private fun workflow(
        querySignIn: () -> String = { signInResponse(false) },
        signIn: () -> String = { successResponse() },
        queryPointBalls: () -> String = { pointBallResponse("ball-1", 120) },
        collectPointBalls: () -> String = { successResponse() },
        queryStickers: () -> String = { stickerResponse("sticker-1") },
        receiveStickers: (Set<String>) -> String = { successResponse() }
    ): GameCenterRewardWorkflow {
        return GameCenterRewardWorkflow(
            querySignIn = querySignIn,
            signInAction = signIn,
            queryPointBalls = queryPointBalls,
            collectPointBallsAction = collectPointBalls,
            queryStickers = queryStickers,
            receiveStickersAction = receiveStickers,
            isActionSuccess = { response ->
                JSONObject(response).optBoolean("success", false)
            }
        )
    }

    private fun signInResponse(signedIn: Boolean): String {
        return """{"success":true,"data":{"signInBallModule":{"signInStatus":$signedIn}}}"""
    }

    private fun pointBallResponse(id: String?, totalAmount: Int): String {
        val item = id?.let { "{\"pointBallId\":\"$it\"}" }.orEmpty()
        return """{"success":true,"data":{"totalAmount":$totalAmount,"pointBallList":[$item]}}"""
    }

    private fun stickerResponse(id: String?): String {
        val pages = id?.let {
            "[{\"stickerCanReceiveList\":[{\"id\":\"$it\"}]}]"
        } ?: "[]"
        return """{"success":true,"canReceivePageList":$pages}"""
    }

    private fun successResponse(): String = """{"success":true}"""
}
