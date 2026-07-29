package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberTreasureBoxWorkflowTest {

    @Test
    fun `触发响应成功但回查仍为处理中时不得确认`() {
        var triggerCalls = 0
        var queryCalls = 0
        val task = MemberTreasureBoxTask(
            bizNo = "box-1",
            taskType = "SIGN_BOX",
            endTime = 0L,
            awardNum = 5
        )
        val workflow = MemberTreasureBoxWorkflow(
            triggerTask = {
                triggerCalls++
                """
                {
                  "success": true,
                  "currentTaskInfo": {
                    "bizNo": "box-1",
                    "taskStatus": "SUCCESS",
                    "awardNum": 5
                  }
                }
                """.trimIndent()
            },
            queryTask = {
                queryCalls++
                """
                {
                  "success": true,
                  "allTaskCompleted": false,
                  "taskType": "SIGN_BOX",
                  "currentTaskInfo": {
                    "bizNo": "box-1",
                    "taskStatus": "PROCESSING",
                    "awardNum": 5
                  }
                }
                """.trimIndent()
            }
        )

        val result = workflow.triggerAndVerify(task)

        assertEquals(1, triggerCalls)
        assertEquals(1, queryCalls)
        assertFalse(result.confirmed)
        assertTrue(result.retryable)
        assertEquals(0, result.awardNum)
    }

    @Test
    fun `回查切换到新宝箱时确认奖励并返回后续任务`() {
        val task = MemberTreasureBoxTask(
            bizNo = "box-1",
            taskType = "SIGN_BOX",
            endTime = 0L,
            awardNum = 5
        )
        val workflow = MemberTreasureBoxWorkflow(
            triggerTask = {
                """
                {
                  "success": true,
                  "currentTaskInfo": {
                    "bizNo": "box-1",
                    "taskStatus": "SUCCESS",
                    "awardNum": 5
                  }
                }
                """.trimIndent()
            },
            queryTask = {
                """
                {
                  "success": true,
                  "allTaskCompleted": false,
                  "taskType": "SIGN_BOX",
                  "currentTaskInfo": {
                    "bizNo": "box-2",
                    "taskStatus": "PROCESSING",
                    "endDt": 123456,
                    "awardNum": 3
                  }
                }
                """.trimIndent()
            }
        )

        val result = workflow.triggerAndVerify(task)

        assertTrue(result.confirmed)
        assertFalse(result.retryable)
        assertEquals(5, result.awardNum)
        assertEquals("box-2", result.nextTask?.bizNo)
    }
}
