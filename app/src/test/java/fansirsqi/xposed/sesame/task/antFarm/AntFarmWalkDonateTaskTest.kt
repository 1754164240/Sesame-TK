package fansirsqi.xposed.sesame.task.antFarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import fansirsqi.xposed.sesame.entity.OtherEntityProvider

class AntFarmWalkDonateTaskTest {

    @Test
    fun `家庭选项包含捐步`() {
        val options = OtherEntityProvider.farmFamilyOption()

        assertTrue(options.any { it.id == "walkDonate" && it.name.contains("捐步") })
    }

    @Test
    fun `识别庄园捐步任务`() {
        assertTrue(AntFarmWalkDonateTask.isWalkDonateTask("ANY_KEY", "去捐步领饲料"))
        assertTrue(AntFarmWalkDonateTask.isWalkDonateTask("WALK_DONATE_TASK", "浏览任务"))
        assertTrue(AntFarmWalkDonateTask.isWalkDonateTask("STEP_DONATION_TASK", "浏览任务"))
        assertFalse(AntFarmWalkDonateTask.isWalkDonateTask("VIDEO_TASK", "看视频领饲料"))
    }

    @Test
    fun `超过1000步才允许捐步`() {
        assertFalse(AntFarmWalkDonateTask.isEligibleStepCount(1000))
        assertTrue(AntFarmWalkDonateTask.isEligibleStepCount(1001))
    }

    @Test
    fun `小于等于1000步需要先同步步数`() {
        assertTrue(AntFarmWalkDonateTask.shouldSyncStepBeforeDonate(999))
        assertTrue(AntFarmWalkDonateTask.shouldSyncStepBeforeDonate(1000))
        assertFalse(AntFarmWalkDonateTask.shouldSyncStepBeforeDonate(1001))
    }

    @Test
    fun `从新步数接口解析当日步数`() {
        val response = JSONObject(
            """
            {
              "resultCode": "SUCCESS",
              "success": true,
              "stepCount": 23804,
              "timeZone": "Asia/Shanghai"
            }
            """.trimIndent()
        )

        assertEquals(23804, AntFarmWalkDonateTask.extractStepCount(response))
    }

    @Test
    fun `从捐步首页解析token和活动id`() {
        val response = JSONObject(
            """
            {
              "isSuccess": true,
              "walkDonateHomeModel": {
                "donateToken": "2088912175797585_1781835883334",
                "walkCharityActivityModel": {
                  "activityId": "20160524001110000000000000001002"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(
            "2088912175797585_1781835883334",
            AntFarmWalkDonateTask.extractDonateToken(response)
        )
        assertEquals(
            "20160524001110000000000000001002",
            AntFarmWalkDonateTask.extractActivityId(response)
        )
    }

    @Test
    fun `首页缺少活动id时使用抓包默认活动id`() {
        val response = JSONObject(
            """
            {
              "walkDonateHomeModel": {
                "donateToken": "token"
              }
            }
            """.trimIndent()
        )

        assertEquals(
            "20160524001110000000000000001002",
            AntFarmWalkDonateTask.extractActivityId(response)
        )
    }

    @Test
    fun `兑换接口成功或已捐步都视为完成`() {
        assertTrue(AntFarmWalkDonateTask.isDonateSuccess(JSONObject("""{"isSuccess":true}""")))
        assertTrue(AntFarmWalkDonateTask.isDonateSuccess(JSONObject("""{"resultDesc":"今日已捐步"}""")))
        assertFalse(AntFarmWalkDonateTask.isDonateSuccess(JSONObject("""{"isSuccess":false,"resultDesc":"失败"}""")))
    }

    @Test
    fun `捐步动作后以首页已兑换状态确认`() {
        assertTrue(
            AntFarmWalkDonateTask.isAlreadyDonated(
                JSONObject(
                    """
                    {
                      "walkDonateHomeModel": {
                        "walkUserInfoModel": {
                          "exchangeFlag": "2",
                          "userExchangedSteps": 1000
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
        )
        assertFalse(
            AntFarmWalkDonateTask.isAlreadyDonated(
                JSONObject(
                    """
                    {
                      "walkDonateHomeModel": {
                        "walkUserInfoModel": {
                          "exchangeFlag": "1"
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
        )
    }
}
