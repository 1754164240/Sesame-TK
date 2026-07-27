package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParadiseMallHomeParserTest {

    @Test
    fun `RPC失败不能归类为无权益`() {
        val result = ParadiseMallHomeParser.parse(
            """{"success":false,"resultCode":"EMPTY_RPC_RESPONSE","resultDesc":"RPC返回为空"}"""
        )

        assertTrue(result is ParadiseMallHomeResult.RpcFailure)
    }

    @Test
    fun `成功响应空列表归类为无权益`() {
        val result = ParadiseMallHomeParser.parse(
            """{"success":true,"mallItemSimpleList":[]}"""
        )

        assertEquals(ParadiseMallHomeResult.Empty, result)
    }

    @Test
    fun `成功响应有商品时保留商品列表`() {
        val result = ParadiseMallHomeParser.parse(
            """{"resultCode":"SUCCESS","mallItemSimpleList":[{"spuId":"benefit-1"}]}"""
        )

        assertTrue(result is ParadiseMallHomeResult.Items)
        assertEquals(1, (result as ParadiseMallHomeResult.Items).items.length())
        assertEquals("benefit-1", result.items.getJSONObject(0).getString("spuId"))
    }

    @Test
    fun `成功响应缺少商品字段归类为协议变化`() {
        val result = ParadiseMallHomeParser.parse("""{"success":true}""")

        assertTrue(result is ParadiseMallHomeResult.SchemaChanged)
    }
}
