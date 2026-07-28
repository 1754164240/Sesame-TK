package fansirsqi.xposed.sesame.task.antFishPond

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FishPondPolicyTest {

    @Test
    fun `已完成任务领奖且危险任务始终跳过`() {
        assertEquals(
            FishPondTaskDecision.CLAIM,
            FishPondPolicy.decideTask(task(status = "FINISHED"))
        )
        assertEquals(
            FishPondTaskDecision.SKIP,
            FishPondPolicy.decideTask(task(type = "FISHPOND_GAME", status = "TODO"))
        )
        assertEquals(
            FishPondTaskDecision.SKIP,
            FishPondPolicy.decideTask(task(title = "观看广告", status = "TODO"))
        )
        assertEquals(
            FishPondTaskDecision.SKIP,
            FishPondPolicy.decideTask(task(adBizNo = "ad-1", status = "TODO"))
        )
        assertEquals(
            FishPondTaskDecision.SKIP,
            FishPondPolicy.decideTask(task(type = "FISHPOND_AD_TASK", status = "TODO"))
        )
        assertEquals(
            FishPondTaskDecision.COMPLETE,
            FishPondPolicy.decideTask(
                task(type = "FISH_TASK_15", status = "TODO", actionType = "VISIT")
            )
        )
        assertEquals(
            FishPondTaskDecision.SKIP,
            FishPondPolicy.decideTask(
                task(type = "NEW_TASK", status = "TODO", actionType = "UNKNOWN")
            )
        )
    }

    @Test
    fun `普通安全任务按状态完成领取或等待`() {
        assertEquals(
            FishPondTaskDecision.COMPLETE,
            FishPondPolicy.decideTask(
                task(type = "FISH_TASK_15", status = "TODO", actionType = "VISIT")
            )
        )
        assertEquals(
            FishPondTaskDecision.COMPLETE,
            FishPondPolicy.decideTask(
                task(type = "FISH_TASK_15", status = "TO_DO", actionType = "VISIT")
            )
        )
        assertEquals(
            FishPondTaskDecision.CLAIM,
            FishPondPolicy.decideTask(task(status = "TO_RECEIVE"))
        )
        assertEquals(
            FishPondTaskDecision.WAIT,
            FishPondPolicy.decideTask(task(status = "RECEIVED"))
        )
        assertEquals(
            FishPondTaskDecision.SKIP,
            FishPondPolicy.decideTask(task(status = "UNKNOWN"))
        )
    }

    @Test
    fun `钓鱼必须有鱼竿和风控令牌且未达到每日上限`() {
        assertTrue(FishPondPolicy.canContinueFishing(1, 0, 30, true))
        assertFalse(FishPondPolicy.canContinueFishing(0, 0, 30, true))
        assertFalse(FishPondPolicy.canContinueFishing(1, 0, 30, false))
        assertFalse(FishPondPolicy.canContinueFishing(1, 30, 30, true))
        assertTrue(FishPondPolicy.canContinueFishing(1, 200, 0, true))
    }

    @Test
    fun `识别常见成功响应且未知结构不视为成功`() {
        assertTrue(FishPondPolicy.isRpcSuccess(JSONObject("""{"success":true}""")))
        assertTrue(FishPondPolicy.isRpcSuccess(JSONObject("""{"resultCode":"SUCCESS"}""")))
        assertTrue(FishPondPolicy.isRpcSuccess(JSONObject("""{"code":"100"}""")))
        assertTrue(
            FishPondPolicy.isRpcSuccess(
                JSONObject("""{"result":{"success":true}}""")
            )
        )
        assertFalse(FishPondPolicy.isRpcSuccess(JSONObject("""{"success":false}""")))
        assertFalse(FishPondPolicy.isRpcSuccess(JSONObject("""{"code":"SYSTEM_ERROR"}""")))
        assertFalse(FishPondPolicy.isRpcSuccess(JSONObject()))
    }

    private fun task(
        type: String = "FISH_TASK_14",
        sceneCode: String = "ANTFISHPOND_TASK",
        status: String = "TODO",
        title: String = "浏览鱼池",
        adBizNo: String = "",
        actionType: String = "GOFISH"
    ) = FishPondTaskSnapshot(type, sceneCode, status, title, adBizNo, actionType)
}
