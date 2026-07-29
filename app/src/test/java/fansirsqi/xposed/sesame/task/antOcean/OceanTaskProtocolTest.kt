package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OceanTaskProtocolTest {
    @Test
    fun `海洋任务按稳定 taskType 读取状态`() {
        val response = responseWith("OCEAN_TASK_1", "FINISHED")

        assertEquals(
            OceanTaskState("OCEAN_TASK_1", "FINISHED"),
            OceanTaskProtocol.statusOf(response, "OCEAN_TASK_1")
        )
        assertNull(OceanTaskProtocol.statusOf(JSONObject(), "OCEAN_TASK_1"))
    }

    @Test
    fun `海洋任务状态不推进时 ACK 不能确认`() {
        val todo = OceanTaskState("OCEAN_TASK_1", "TODO")
        val finished = OceanTaskState("OCEAN_TASK_1", "FINISHED")
        val received = OceanTaskState("OCEAN_TASK_1", "RECEIVED")

        assertTrue(OceanTaskProtocol.isAdvanced(todo, finished))
        assertTrue(OceanTaskProtocol.isAdvanced(finished, received))
        assertTrue(OceanTaskProtocol.isAdvanced(finished, null))
        assertFalse(OceanTaskProtocol.isAdvanced(todo, todo))
        assertFalse(OceanTaskProtocol.isAdvanced(todo, null))
    }

    private fun responseWith(taskType: String, status: String): JSONObject {
        return JSONObject().put(
            "antOceanTaskVOList",
            JSONArray().put(
                JSONObject()
                    .put("taskType", taskType)
                    .put("taskStatus", status)
            )
        )
    }
}
