package fansirsqi.xposed.sesame.task.antStall

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StallTaskProtocolTest {
    @Test
    fun `XLight 只有同一对象同时命中双码才是流量风控`() {
        assertFalse(StallTaskProtocol.isXlightTrafficLimited(
            JSONObject().put("retCode", "217")
        ))
        assertFalse(StallTaskProtocol.isXlightTrafficLimited(
            JSONObject().put("sspErrorCode", "61002")
        ))
        assertFalse(StallTaskProtocol.isXlightTrafficLimited(
            JSONObject()
                .put("retCode", "217")
                .put("resData", JSONObject().put("sspErrorCode", "61002"))
        ))
        assertTrue(StallTaskProtocol.isXlightTrafficLimited(
            JSONObject().put("retCode", "217").put("sspErrorCode", "61002")
        ))
        assertTrue(StallTaskProtocol.isXlightTrafficLimited(
            JSONObject().put(
                "resData",
                JSONObject().put("retCode", "217").put("sspErrorCode", "61002")
            )
        ))
    }

    @Test
    fun `任务状态按 taskType 从服务端列表读取`() {
        val response = responseWith("TASK_1", "FINISHED")

        assertEquals(
            StallTaskState("TASK_1", "FINISHED"),
            StallTaskProtocol.statusOf(response, "TASK_1")
        )
        assertNull(StallTaskProtocol.statusOf(response, "TASK_2"))
        assertNull(StallTaskProtocol.statusOf(JSONObject(), "TASK_1"))
    }

    @Test
    fun `只有任务状态推进才确认动作`() {
        val todo = StallTaskState("TASK_1", "TODO")
        val finished = StallTaskState("TASK_1", "FINISHED")
        val received = StallTaskState("TASK_1", "RECEIVED")

        assertTrue(StallTaskProtocol.isAdvanced(todo, finished))
        assertTrue(StallTaskProtocol.isAdvanced(finished, received))
        assertTrue(StallTaskProtocol.isAdvanced(finished, null))
        assertFalse(StallTaskProtocol.isAdvanced(todo, todo))
        assertFalse(StallTaskProtocol.isAdvanced(todo, null))
        assertFalse(StallTaskProtocol.isAdvanced(todo, StallTaskState("OTHER", "FINISHED")))
    }

    @Test
    fun `签到必须由刷新后的服务端状态确认`() {
        val unsigned = JSONObject().put(
            "signListModel",
            JSONObject().put("currentKeySigned", false)
        )
        val signed = JSONObject().put(
            "signListModel",
            JSONObject().put("currentKeySigned", true)
        )

        assertFalse(StallTaskProtocol.isSignConfirmed(unsigned))
        assertTrue(StallTaskProtocol.isSignConfirmed(signed))
        assertFalse(StallTaskProtocol.isSignConfirmed(JSONObject()))
    }

    private fun responseWith(taskType: String, status: String): JSONObject {
        return JSONObject().put(
            "taskModels",
            JSONArray().put(
                JSONObject()
                    .put("taskType", taskType)
                    .put("taskStatus", status)
                    .put("bizInfo", "{}")
            )
        )
    }
}
