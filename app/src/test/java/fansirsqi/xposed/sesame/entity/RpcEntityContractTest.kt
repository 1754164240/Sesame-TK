package fansirsqi.xposed.sesame.entity

import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpcEntityContractTest {

    @Test
    fun `关联参数与应用参数分别进入正确字段`() {
        val bridge = RecordingRpcBridge()

        bridge.requestString("relation.method", "[]", """[{"pathList":["a"]}]""")
        assertEquals("""[{"pathList":["a"]}]""", bridge.captured.requestRelation)
        assertNull(bridge.captured.appName)

        bridge.requestString(
            "app.method",
            "[]",
            "20000001",
            "nativeCall",
            "com.example.Facade"
        )
        assertNull(bridge.captured.requestRelation)
        assertEquals("20000001", bridge.captured.appName)
        assertEquals("nativeCall", bridge.captured.methodName)
        assertEquals("com.example.Facade", bridge.captured.facadeName)
    }

    private class RecordingRpcBridge : RpcBridge {
        lateinit var captured: RpcEntity

        override fun getVersion(): RpcVersion = RpcVersion.NEW

        override fun load() = Unit

        override fun unload() = Unit

        override fun requestString(
            rpcEntity: RpcEntity,
            tryCount: Int,
            retryInterval: Int
        ): String {
            captured = rpcEntity
            return """{"success":true}"""
        }

        override fun requestObject(
            rpcEntity: RpcEntity,
            tryCount: Int,
            retryInterval: Int
        ): RpcEntity {
            captured = rpcEntity
            return rpcEntity
        }
    }
}
