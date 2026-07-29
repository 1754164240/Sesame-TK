package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestTaskPolicyTest {

    @Test
    fun `多任务容器和递归子任务按稳定键去重`() {
        val snapshot = ForestTaskPolicy.parseSnapshot(
            """
            {
              "success": true,
              "taskInfoList": [
                ${task("ROOT_BROWSE", "TODO", "浏览绿色会场")},
                ${task("DUPLICATE_TASK", "TODO", "重复任务")}
              ],
              "forestTasksNew": [{
                "taskInfoList": [
                  ${task("DUPLICATE_TASK", "TODO", "重复任务")},
                  {
                    "taskBaseInfo": {
                      "sceneCode": "SCENE",
                      "taskType": "PARENT_TASK",
                      "taskStatus": "TODO",
                      "bizInfo": "{\"taskTitle\":\"父任务\"}"
                    },
                    "childTaskTypeList": [
                      ${task("CHILD_SIGN", "FINISHED", "子任务")}
                    ]
                  }
                ]
              }],
              "taskGroupInfoList": [{
                "taskInfoList": [
                  ${task("GROUP_TASK", "RECEIVED", "分组任务")}
                ]
              }]
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertTrue(snapshot.complete)
        assertEquals(
            listOf(
                "ROOT_BROWSE",
                "DUPLICATE_TASK",
                "PARENT_TASK",
                "CHILD_SIGN",
                "GROUP_TASK"
            ),
            snapshot.tasks.map { it.taskType }
        )
    }

    @Test
    fun `成功响应但任务容器未知时不得判定为空任务`() {
        val snapshot = ForestTaskPolicy.parseSnapshot(
            """{"success":true,"unexpectedTaskList":[]}"""
        )

        assertFalse(snapshot.recognized)
        assertFalse(snapshot.complete)
        assertTrue(snapshot.tasks.isEmpty())
    }

    @Test
    fun `多查询源存在未知结构时已知任务可见但快照不完整`() {
        val snapshot = ForestTaskPolicy.mergeSnapshots(
            listOf(
                """{"success":true,"taskInfoList":[${task("SAFE_BROWSE", "TODO", "浏览绿色会场")}]}""",
                """{"success":true,"unexpectedTaskList":[]}"""
            )
        )

        assertTrue(snapshot.recognized)
        assertFalse(snapshot.complete)
        assertEquals("SAFE_BROWSE", snapshot.tasks.single().taskType)
    }

    @Test
    fun `多查询源重复任务采用推进后的状态`() {
        val snapshot = ForestTaskPolicy.mergeSnapshots(
            listOf(
                """{"success":true,"taskInfoList":[${task("SAFE_BROWSE", "TODO", "浏览绿色会场")}]}""",
                """{"success":true,"taskInfoList":[${task("SAFE_BROWSE", "FINISHED", "浏览绿色会场")}]}"""
            )
        )

        assertEquals("FINISHED", snapshot.tasks.single().status)
    }

    @Test
    fun `多查询源重复签到采用服务端已签到状态`() {
        val snapshot = ForestTaskPolicy.mergeSnapshots(
            listOf(
                signResponse(signed = false),
                signResponse(signed = true)
            )
        )

        assertTrue(snapshot.signs.single().signed)
    }

    @Test
    fun `游戏广告金融和未知任务固定阻断`() {
        val blocked = listOf(
            state("mokuai_senlin_hlz", "TODO", "玩游戏完成一局"),
            state("FOREST_LIGHT_AD_TASK", "TODO", "看广告得能量"),
            state("FOREST_ORDER_TASK", "TODO", "下单得奖励"),
            state("FOREST_RECHARGE_TASK", "TODO", "充值得能量"),
            state("UNRECOGNIZED_TASK", "TODO", "神秘任务")
        )

        blocked.forEach {
            assertEquals(
                it.taskType,
                ForestTaskDecision.SKIP_UNSAFE,
                ForestTaskPolicy.decide(it)
            )
        }
    }

    @Test
    fun `明确安全浏览任务允许完成且已完成任务允许领奖`() {
        assertEquals(
            ForestTaskDecision.COMPLETE_SAFE,
            ForestTaskPolicy.decide(
                state("ANTFOREST_GREEN_BROWSE", "TODO", "浏览绿色会场")
            )
        )
        assertEquals(
            ForestTaskDecision.CLAIM,
            ForestTaskPolicy.decide(
                state("ANTFOREST_GREEN_BROWSE", "FINISHED", "浏览绿色会场")
            )
        )
        assertEquals(
            ForestTaskDecision.TERMINAL,
            ForestTaskPolicy.decide(
                state("ANTFOREST_GREEN_BROWSE", "RECEIVED", "浏览绿色会场")
            )
        )
    }

    @Test
    fun `签到只读取当前签到项状态`() {
        val snapshot = ForestTaskPolicy.parseSnapshot(
            """
            {
              "success": true,
              "forestSignVOList": [{
                "signId": "sign-1",
                "sceneCode": "SCENE",
                "currentSignKey": "20260729",
                "signRecords": [
                  {"signKey":"20260728","signed":true,"awardCount":5},
                  {"signKey":"20260729","signed":false,"awardCount":8}
                ]
              }]
            }
            """.trimIndent()
        )

        assertTrue(snapshot.recognized)
        assertEquals(1, snapshot.signs.size)
        assertEquals(false, snapshot.signs.single().signed)
        assertEquals(8, snapshot.signs.single().awardCount)
    }

    private fun state(
        taskType: String,
        status: String,
        title: String
    ): ForestTaskState {
        return ForestTaskState(
            stableKey = "SCENE#$taskType",
            sceneCode = "SCENE",
            taskType = taskType,
            status = status,
            title = title
        )
    }

    private fun task(
        taskType: String,
        status: String,
        title: String
    ): String {
        return """
            {
              "taskBaseInfo": {
                "sceneCode": "SCENE",
                "taskType": "$taskType",
                "taskStatus": "$status",
                "bizInfo": "{\"taskTitle\":\"$title\"}"
              }
            }
        """.trimIndent()
    }

    private fun signResponse(signed: Boolean): String {
        return """
            {
              "success": true,
              "forestSignVOList": [{
                "signId": "sign-1",
                "sceneCode": "SCENE",
                "currentSignKey": "20260729",
                "signRecords": [{
                  "signKey": "20260729",
                  "signed": $signed,
                  "awardCount": 8
                }]
              }]
            }
        """.trimIndent()
    }
}
