package fansirsqi.xposed.sesame.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcRequestContextTest {

    @Test
    fun `会员任务日志包含定位字段但不包含请求数据`() {
        val context = RpcRequestContext(
            traceId = "member-123",
            source = "会员任务",
            stage = "执行",
            taskName = "浏览会员会场",
            configId = "config-1",
            taskId = "process-2",
            targetBusiness = "BROWSE#15S#scene"
        )

        val log = context.toSafeLog("com.example.execute")

        assertTrue(log.contains("traceId=member-123"))
        assertTrue(log.contains("configId=config-1"))
        assertTrue(log.contains("taskId=process-2"))
        assertTrue(log.contains("RPC方法=com.example.execute"))
        assertFalse(log.contains("requestData"))
        assertFalse(log.contains("token"))
    }

    @Test
    fun `会员任务列表查询触发验证时输出明确提示`() {
        val context = RpcRequestContext(
            traceId = "trace-list",
            source = "会员任务",
            stage = "任务列表查询"
        )

        assertEquals(
            "任务列表查询触发验证 | traceId=trace-list | 来源=会员任务 | " +
                "阶段=任务列表查询 | RPC方法=signPageTaskList",
            context.verificationBlockLog("signPageTaskList")
        )
    }
}
