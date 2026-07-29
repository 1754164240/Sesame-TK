package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YebExpGoldPolicyTest {

    @Test
    fun `今日签到状态只在明确字段存在时判定`() {
        assertEquals(
            YebExpGoldSignState.PENDING,
            YebExpGoldPolicy.signState(mainResponse("TO_SIGNED"))
        )
        assertEquals(
            YebExpGoldSignState.PENDING,
            YebExpGoldPolicy.signState(mainResponse("UNSIGNED"))
        )
        assertEquals(
            YebExpGoldSignState.SIGNED,
            YebExpGoldPolicy.signState(mainResponse("SIGNED"))
        )
        assertEquals(
            YebExpGoldSignState.UNKNOWN,
            YebExpGoldPolicy.signState("""{"success":true}""")
        )
        assertEquals(
            YebExpGoldSignState.UNKNOWN,
            YebExpGoldPolicy.signState("{broken")
        )
    }

    @Test
    fun `待使用券计数兼容嵌套容器且未知结构不推定为空`() {
        val response = """
            {
              "success": true,
              "result": {
                "equityList": [
                  {"equityStatus": "CAN_USE"},
                  {"equityVoucherStatus": "can_use"},
                  {"finEquityStatus": "USED"}
                ]
              }
            }
        """.trimIndent()

        assertEquals(2, YebExpGoldPolicy.pendingVoucherCount(response))
        assertNull(YebExpGoldPolicy.pendingVoucherCount("""{"success":true}"""))
        assertNull(
            YebExpGoldPolicy.pendingVoucherCount(
                """{"success":false,"result":{"equityList":[]}}"""
            )
        )
    }

    @Test
    fun `动作响应必须包含明确成功标记`() {
        assertTrue(
            YebExpGoldPolicy.isActionAccepted(
                """{"result":{"resultCode":"SUCCESS"}}"""
            )
        )
        assertTrue(
            YebExpGoldPolicy.isActionAccepted(
                """{"data":{"code":"200"}}"""
            )
        )
        assertFalse(YebExpGoldPolicy.isActionAccepted("""{"result":{}}"""))
        assertFalse(
            YebExpGoldPolicy.isActionAccepted(
                """{"success":false,"resultCode":"SUCCESS"}"""
            )
        )
    }

    private fun mainResponse(status: String): String = """
        {
          "success": true,
          "resultData": {
            "signInData": {
              "list": [
                {
                  "displayDate": "今天",
                  "signInfo": {
                    "signDateDesc": "TODAY",
                    "signStatus": "$status"
                  }
                }
              ]
            }
          }
        }
    """.trimIndent()
}
