package fansirsqi.xposed.sesame.task.antOrchard

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchardBrowseTaskPolicyTest {

    @Test
    fun `仅放行已知淘宝浏览合同并提取直接来源`() {
        val task = parseTask(
            targetUrl = "alipays://platformapi/startapp?source=taobao_orchard"
        )

        val selection = OrchardBrowseTaskPolicy.select(task)

        assertEquals("task-1", selection?.taskId)
        assertEquals("taobao_orchard", selection?.source)
    }

    @Test
    fun `嵌套跳转只有包含来源时才放行`() {
        val nestedUrl = URLEncoder.encode(
            "https://pages.example.test/orchard?source=nested_source",
            StandardCharsets.UTF_8.name()
        )
        val withSource = parseTask(
            targetUrl = "alipays://platformapi/startapp?url=$nestedUrl"
        )
        val withoutSource = parseTask(
            targetUrl = "alipays://platformapi/startapp?url=" +
                URLEncoder.encode(
                    "https://pages.example.test/orchard",
                    StandardCharsets.UTF_8.name()
                )
        )

        assertEquals(
            "nested_source",
            OrchardBrowseTaskPolicy.select(withSource)?.source
        )
        assertNull(OrchardBrowseTaskPolicy.select(withoutSource))
    }

    @Test
    fun `缺少任一已验证标识或浏览语义时拒绝`() {
        val unsafeTasks = listOf(
            parseTask(groupId = ""),
            parseTask(groupId = "other"),
            parseTask(sceneCode = ""),
            parseTask(sceneCode = "other"),
            parseTask(actionType = "TRIGGER"),
            parseTask(taskPlantType = "NORMAL"),
            parseTask(taskId = ""),
            parseTask(title = "完成普通任务"),
            parseTask(targetUrl = "alipays://platformapi/startapp")
        )

        assertTrue(
            unsafeTasks.all { OrchardBrowseTaskPolicy.select(it) == null }
        )
    }

    @Test
    fun `标题或链接含风险信号时拒绝`() {
        val riskyTitles = listOf(
            "浏览游戏领肥料",
            "逛广告会场",
            "浏览充值中心",
            "浏览商品并下单",
            "浏览支付页面",
            "浏览借贷提现活动"
        )
        val riskyUrls = listOf(
            "alipays://platformapi/startapp?source=game_center",
            "alipays://platformapi/startapp?source=light_ad",
            "alipays://platformapi/startapp?source=recharge_order"
        )

        assertTrue(
            riskyTitles.all {
                OrchardBrowseTaskPolicy.select(parseTask(title = it)) == null
            }
        )
        assertTrue(
            riskyUrls.all {
                OrchardBrowseTaskPolicy.select(parseTask(targetUrl = it)) ==
                    null
            }
        )
    }

    @Test
    fun `只有已识别列表中的终态或任务消失才确认`() {
        assertFalse(
            OrchardBrowseTaskPolicy.isCompletionConfirmed(
                taskResponse("TODO"),
                "task-1"
            )
        )
        assertTrue(
            OrchardBrowseTaskPolicy.isCompletionConfirmed(
                taskResponse("FINISHED"),
                "task-1"
            )
        )
        assertTrue(
            OrchardBrowseTaskPolicy.isCompletionConfirmed(
                taskResponse("RECEIVED"),
                "task-1"
            )
        )
        assertTrue(
            OrchardBrowseTaskPolicy.isCompletionConfirmed(
                """{"resultCode":"100","taskList":[]}""",
                "task-1"
            )
        )
        assertFalse(
            OrchardBrowseTaskPolicy.isCompletionConfirmed(
                """{"resultCode":"100","data":{}}""",
                "task-1"
            )
        )
    }

    private fun parseTask(
        taskId: String = "task-1",
        groupId: String = "12172",
        sceneCode: String = "972",
        actionType: String = "VISIT",
        taskPlantType: String = "TAOBAO",
        title: String = "逛淘宝得肥料",
        targetUrl: String =
            "alipays://platformapi/startapp?source=taobao_orchard"
    ): AntOrchardTaskState {
        return AntOrchardRewardPolicy.parseTasks(
            """
                {
                  "resultCode":"100",
                  "taskList":[{
                    "taskId":"$taskId",
                    "groupId":"$groupId",
                    "taskStatus":"TODO",
                    "actionType":"$actionType",
                    "sceneCode":"$sceneCode",
                    "taskPlantType":"$taskPlantType",
                    "taskDisplayConfig":{
                      "title":"$title",
                      "targetUrl":"$targetUrl"
                    }
                  }]
                }
            """.trimIndent()
        ).tasks.single()
    }

    private fun taskResponse(status: String): String {
        return """
            {
              "resultCode":"100",
              "taskList":[{
                "taskId":"task-1",
                "groupId":"12172",
                "taskStatus":"$status",
                "actionType":"VISIT",
                "sceneCode":"972",
                "taskPlantType":"TAOBAO",
                "taskDisplayConfig":{
                  "title":"逛淘宝得肥料",
                  "targetUrl":"alipays://platformapi/startapp?source=taobao_orchard"
                }
              }]
            }
        """.trimIndent()
    }
}
