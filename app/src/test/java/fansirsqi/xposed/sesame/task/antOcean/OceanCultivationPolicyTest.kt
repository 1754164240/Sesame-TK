package fansirsqi.xposed.sesame.task.antOcean

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OceanCultivationPolicyTest {
    @Test
    fun `生态身份使用 cultivationCode 和项目编码`() {
        val item = JSONObject()
            .put("cultivationCode", "CUL_100")
            .put("templateCode", "DISPLAY_ONLY")
            .put("cultivationName", "蓝碳保护地")
            .put("energy", 260)
            .put("templateSubType", "BEACH")
            .put("applyAction", "AVAILABLE")
            .put("projectConfigVO", JSONObject().put("code", "PROJECT_9"))

        assertEquals(
            OceanCultivation(
                cultivationCode = "CUL_100",
                projectCode = "PROJECT_9",
                name = "蓝碳保护地",
                energy = 260,
                templateSubType = "BEACH",
                available = true
            ),
            OceanCultivationPolicy.parse(item)
        )
    }

    @Test
    fun `缺失服务端身份字段时拒绝候选`() {
        val withoutCultivationCode = JSONObject()
            .put("templateCode", "DISPLAY_ONLY")
            .put("projectConfigVO", JSONObject().put("code", "PROJECT_9"))
        val withoutProjectCode = JSONObject()
            .put("cultivationCode", "CUL_100")
            .put("projectConfigVO", JSONObject())

        assertNull(OceanCultivationPolicy.parse(withoutCultivationCode))
        assertNull(OceanCultivationPolicy.parse(withoutProjectCode))
    }
}
