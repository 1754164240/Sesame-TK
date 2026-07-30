package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCenterRewardPolicyTest {

    @Test
    fun `签到查询只有明确签到模块才被识别`() {
        val unsigned = GameCenterRewardPolicy.parseSignIn(
            """{"success":true,"data":{"signInBallModule":{"signInStatus":false}}}"""
        )
        val unknown = GameCenterRewardPolicy.parseSignIn(
            """{"success":true,"data":{}}"""
        )

        assertTrue(unsigned.recognized)
        assertFalse(unsigned.signedIn)
        assertFalse(unknown.recognized)
        assertFalse(unknown.signedIn)
    }

    @Test
    fun `签到动作只有回查到服务端已签到才确认`() {
        assertFalse(
            GameCenterRewardPolicy.isSignInConfirmed(
                """{"success":true,"data":{"signInBallModule":{"signInStatus":false}}}"""
            )
        )
        assertTrue(
            GameCenterRewardPolicy.isSignInConfirmed(
                """{"success":true,"data":{"signInBallModule":{"signInStatus":true}}}"""
            )
        )
        assertFalse(GameCenterRewardPolicy.isSignInConfirmed(""))
    }

    @Test
    fun `玩乐豆查询解析待收编号和总资产`() {
        val snapshot = GameCenterRewardPolicy.parsePointBalls(
            """
            {
              "success": true,
              "data": {
                "totalAmount": 120,
                "pointBallList": [
                  {"pointBallId":"ball-1"},
                  {"id":"ball-2"}
                ]
              }
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals(setOf("ball-1", "ball-2"), snapshot.pendingIds)
        assertEquals(120L, snapshot.totalAmount)
    }

    @Test
    fun `玩乐豆领取后待收为空或资产增加才确认`() {
        val previous = GameCenterPointBallSnapshot(
            recognized = true,
            pendingIds = setOf("ball-1"),
            totalAmount = 120L
        )

        assertFalse(
            GameCenterRewardPolicy.isPointBallCollectionConfirmed(
                previous,
                GameCenterPointBallSnapshot(true, setOf("ball-1"), 120L)
            )
        )
        assertTrue(
            GameCenterRewardPolicy.isPointBallCollectionConfirmed(
                previous,
                GameCenterPointBallSnapshot(true, emptySet(), 120L)
            )
        )
        assertTrue(
            GameCenterRewardPolicy.isPointBallCollectionConfirmed(
                previous,
                GameCenterPointBallSnapshot(true, setOf("ball-1"), 121L)
            )
        )
        assertFalse(
            GameCenterRewardPolicy.isPointBallCollectionConfirmed(
                previous,
                GameCenterPointBallSnapshot(false, emptySet(), null)
            )
        )
    }

    @Test
    fun `空待收列表与未知响应结构必须分离`() {
        val empty = GameCenterRewardPolicy.parsePointBalls(
            """{"success":true,"data":{"pointBallList":[]}}"""
        )
        val unknown = GameCenterRewardPolicy.parsePointBalls(
            """{"success":true,"data":{}}"""
        )

        assertTrue(empty.recognized)
        assertTrue(empty.pendingIds.isEmpty())
        assertFalse(unknown.recognized)
        assertTrue(unknown.pendingIds.isEmpty())
    }

    @Test
    fun `P2E平台浏览和真实游戏进入对应执行链`() {
        val allowed = JSONObject()
            .put("taskType", "PLATFORM_TRAN_TASK")
            .put("actionType", "VIEW_TASK")
            .put("taskStatus", "NOT_DONE")
        val game = JSONObject()
            .put("taskType", "GAME_TRAN_TASK")
            .put("actionType", "VIEW_TASK")
            .put("taskStatus", "NOT_DONE")
        val unknown = JSONObject()
            .put("taskType", "UNKNOWN")
            .put("actionType", "VIEW_TASK")
            .put("taskStatus", "NOT_DONE")

        assertEquals(
            GameCenterTaskDecision.SEND,
            GameCenterTaskPolicy.classifyP2eTask(allowed)
        )
        assertEquals(
            GameCenterTaskDecision.EXECUTE_GAME,
            GameCenterTaskPolicy.classifyP2eTask(game)
        )
        assertEquals(
            GameCenterTaskDecision.SKIP_UNSUPPORTED,
            GameCenterTaskPolicy.classifyP2eTask(unknown)
        )
    }

    @Test
    fun `贴纸领取只有目标编号从待领列表消失才确认`() {
        val stillPending = """
            {
              "success": true,
              "canReceivePageList": [{
                "stickerCanReceiveList": [{"id":"sticker-1"}]
              }]
            }
        """.trimIndent()
        val removed = """{"success":true,"canReceivePageList":[]}"""

        assertFalse(
            GameCenterRewardPolicy.isStickerCollectionConfirmed(
                stillPending,
                setOf("sticker-1")
            )
        )
        assertTrue(
            GameCenterRewardPolicy.isStickerCollectionConfirmed(
                removed,
                setOf("sticker-1")
            )
        )
        assertFalse(
            GameCenterRewardPolicy.isStickerCollectionConfirmed(
                """{"success":true,"data":{}}""",
                setOf("sticker-1")
            )
        )
    }

    @Test
    fun `现金档位仅解析查询结果`() {
        val snapshot = GameCenterRewardPolicy.parseCashTiers(
            """
            {
              "success": true,
              "data": {
                "assetModuleVO": {"goldAmount":"1200"},
                "cashExchangeModule": {
                  "prizes": [{
                    "prizeConfigId":"tier-1",
                    "prizeAmount":"0.10",
                    "prizeStatus":"CAN_EXG"
                  }]
                }
              }
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals(1, snapshot.tiers.size)
        assertEquals("tier-1", snapshot.tiers.single().tierId)
        assertEquals("0.10", snapshot.tiers.single().cashAmount)
        assertEquals("CAN_EXG", snapshot.tiers.single().status)
        assertEquals(1200L, snapshot.goldAmount)
    }

    @Test
    fun `P2E签到解析今日记录和动态参数`() {
        val snapshot = GameCenterRewardPolicy.parseP2eSignIn(
            """
            {
              "success": true,
              "data": {
                "signUpModuleVO": {
                  "date":"2026-07-28",
                  "index":3,
                  "signSequenceId":"sequence-1",
                  "signRecordVOList":[{
                    "isToday":true,
                    "signUpStatus":"UN_SIGNED"
                  }]
                }
              }
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertFalse(snapshot.signedIn)
        assertEquals("2026-07-28", snapshot.date)
        assertEquals(3, snapshot.index)
        assertEquals("sequence-1", snapshot.signSequenceId)
    }

    @Test
    fun `P2E签到回查今日记录为SIGNED才确认`() {
        val unsigned = """
            {"success":true,"data":{"signUpModuleVO":{
              "date":"2026-07-28",
              "signRecordVOList":[{"isToday":true,"signUpStatus":"UN_SIGNED"}]
            }}}
        """.trimIndent()
        val signed = """
            {"success":true,"data":{"signUpModuleVO":{
              "date":"2026-07-28",
              "signRecordVOList":[{"isToday":true,"signUpStatus":"SIGNED"}]
            }}}
        """.trimIndent()

        assertFalse(GameCenterRewardPolicy.isP2eSignInConfirmed(unsigned))
        assertTrue(GameCenterRewardPolicy.isP2eSignInConfirmed(signed))
    }

    @Test
    fun `P2E免费抽金币只有明确状态才被识别`() {
        val ready = GameCenterRewardPolicy.parseP2eDraw(
            """{"success":true,"data":{"drawGoldCoinModuleVO":{"status":"NOT_DRAWN"}}}"""
        )
        val unknown = GameCenterRewardPolicy.parseP2eDraw(
            """{"success":true,"data":{}}"""
        )

        assertTrue(ready.recognized)
        assertEquals("NOT_DRAWN", ready.status)
        assertFalse(unknown.recognized)
        assertFalse(GameCenterRewardPolicy.isP2eDrawConfirmed(unknown))
    }

    @Test
    fun `拼贴世界只读解析画布章节和待领贴纸`() {
        val snapshot = GameCenterRewardPolicy.parseBillBlockWorld(
            """
            {
              "success":true,
              "resultCode":200,
              "data":{
                "canvas":{
                  "currentChapterId":"chapter-1",
                  "seasonId":"season-1",
                  "canvasWidth":6,
                  "canvasLength":8
                },
                "chapterTasks":[{
                  "chapterId":"chapter-1",
                  "status":"COMPLETED"
                }],
                "pendingBlocks":[{"blockRecordId":"block-1"}],
                "placedBlocks":[{"blockRecordId":"block-2"}]
              }
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals("chapter-1", snapshot.currentChapterId)
        assertEquals("season-1", snapshot.seasonId)
        assertEquals("COMPLETED", snapshot.chapterStatus)
        assertEquals(setOf("block-1"), snapshot.pendingBlockIds)
        assertEquals(1, snapshot.placedBlockCount)
    }

    @Test
    fun `拼贴世界缺少画布不得推定为空状态`() {
        val snapshot = GameCenterRewardPolicy.parseBillBlockWorld(
            """{"success":true,"resultCode":200,"data":{}}"""
        )

        assertFalse(snapshot.recognized)
        assertTrue(snapshot.pendingBlockIds.isEmpty())
    }
}
