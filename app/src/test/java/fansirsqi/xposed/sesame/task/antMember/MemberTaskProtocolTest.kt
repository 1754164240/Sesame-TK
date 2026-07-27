package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberTaskProtocolTest {

    @Test
    fun `签到页任务查询使用真实任务墙参数`() {
        val request = MemberTaskProtocol.buildSignPageTaskListArgs().getJSONObject(0)

        assertEquals("antmember", request.getString("source"))
        assertEquals("ant_member_xlight_task", request.getString("spaceCode"))
        assertEquals("", request.getString("taskTopConfigId"))
        assertTrue(request.getBoolean("switchNormal"))
        assertEquals(1, request.getInt("pageNo"))
        assertEquals(8, request.getInt("pageSize"))
        assertEquals(
            "ch_appcenter__chsub_9patch",
            request.getJSONObject("sourcePassMap").getString("source")
        )
    }

    @Test
    fun `从签到页广告列表解析动态上下文和阶段奖励`() {
        val response = JSONObject(
            """
            {
              "success": true,
              "resultData": {
                "adTaskList": [{
                  "status": "PROCESSING",
                  "adTask": true,
                  "targetBusiness": ["BROWSE#15S#ad-param"],
                  "lightsAdExtMap": {
                    "adId": "32002001",
                    "bizId": "dynamic-biz-id",
                    "spaceCode": "ant_member_xlight_task",
                    "clickId": "dynamic-click-id",
                    "title": "逛精选好物15秒"
                  },
                  "simpleTaskConfig": {
                    "configId": "config-id",
                    "title": "逛精选好物15秒",
                    "browseSeconds": 15,
                    "taskStage": 1,
                    "stageVOList": [
                      {"awardParam": {"awardParamPoint": 1}},
                      {"awardParam": {"awardParamPoint": 5}}
                    ]
                  }
                }]
              }
            }
            """.trimIndent()
        )

        val task = MemberTaskProtocol.parseAdTasks(response).single()

        assertEquals("32002001", task.adId)
        assertEquals("dynamic-biz-id", task.adBizId)
        assertEquals("逛精选好物15秒", task.title)
        assertEquals(5, task.awardNum)
        assertEquals(15, task.browseSeconds)
        assertEquals("dynamic-click-id", task.extMap.getString("clickId"))
        assertEquals(16_000L, task.waitMillis)
    }

    @Test
    fun `广告任务也可从纯任务列表解析且缺少动态编号时跳过`() {
        val response = JSONObject(
            """
            {
              "resultData": {
                "adTaskList": [{
                  "adTask": true,
                  "lightsAdExtMap": {"adId": "invalid"},
                  "simpleTaskConfig": {"title": "无效任务"}
                }],
                "pureTaskList": [{
                  "adVideoTask": true,
                  "lightsAdExtMap": {
                    "adId": "valid-ad",
                    "bizId": "valid-biz",
                    "spaceCode": "ant_member_xlight_task"
                  },
                  "simpleTaskConfig": {
                    "title": "逛淘宝视频15秒",
                    "browseSeconds": 15,
                    "taskStage": 0,
                    "stageVOList": [{"awardParam": {"awardParamPoint": 1}}]
                  }
                }]
              }
            }
            """.trimIndent()
        )

        val tasks = MemberTaskProtocol.parseAdTasks(response)

        assertEquals(1, tasks.size)
        assertEquals("valid-ad", tasks.single().adId)
    }

    @Test
    fun `领取请求原样透传灯火广告上下文`() {
        val task = MemberAdTask(
            adId = "32002001",
            adBizId = "dynamic-biz-id",
            title = "逛精选好物15秒",
            awardNum = 5,
            browseSeconds = 15,
            extMap = JSONObject()
                .put("bizId", "dynamic-biz-id")
                .put("adId", "32002001")
                .put("clickId", "dynamic-click-id")
                .put("spaceCode", "ant_member_xlight_task")
        )

        val request = MemberTaskProtocol.buildApplyAdTaskArgs(task).getJSONObject(0)

        assertEquals("dynamic-biz-id", request.getString("adBizId"))
        assertEquals("dynamic-biz-id", request.getString("bizNo"))
        assertEquals("32002001", request.getString("adId"))
        assertEquals(5, request.getInt("awardNum"))
        assertEquals("TASK_WALL", request.getString("scene"))
        assertEquals("ant_member_xlight_task", request.getString("spaceCode"))
        assertEquals(
            "dynamic-click-id",
            request.getJSONObject("extMap").getString("clickId")
        )
    }

    @Test
    fun `分类和纯任务列表只解析可执行浏览任务`() {
        val response = JSONObject(
            """
            {
              "resultData": {
                "adTaskList": [{
                  "status": "PROCESSING",
                  "targetBusiness": ["BROWSE#15S#ad-param"],
                  "simpleTaskConfig": {
                    "configId": "must-not-be-normal",
                    "title": "广告任务"
                  }
                }],
                "categoryTaskList": [{
                  "type": "BROWSE",
                  "taskProcessVOList": [{
                    "processId": "process-1",
                    "status": "PROCESSING",
                    "targetBusiness": ["BROWSE#15S#biz-param-1"],
                    "simpleTaskConfig": {
                      "configId": "config-1",
                      "title": "浏览15秒",
                      "browseSeconds": 15
                    }
                  }, {
                    "status": "AWARDED",
                    "targetBusiness": ["BROWSE#15S#done"],
                    "simpleTaskConfig": {"configId": "done", "title": "已完成"}
                  }]
                }],
                "pureTaskList": [{
                  "status": "INIT",
                  "targetBusiness": ["BROWSE#ANY#biz-param-2"],
                  "simpleTaskConfig": {
                    "configId": "config-2",
                    "title": "再逛一逛",
                    "browseSeconds": 12
                  }
                }, {
                  "status": "INIT",
                  "targetBusiness": ["FOLLOW#NONE#follow-param"],
                  "simpleTaskConfig": {"configId": "follow", "title": "关注任务"}
                }]
              }
            }
            """.trimIndent()
        )

        val tasks = MemberTaskProtocol.parseBrowseTasks(response)

        assertEquals(2, tasks.size)
        assertEquals("process-1", tasks[0].processId)
        assertEquals("15S", tasks[0].bizSubType)
        assertFalse(tasks[0].needsApply)
        assertEquals("config-2", tasks[1].configId)
        assertEquals("UNLIMITED", tasks[1].bizSubType)
        assertTrue(tasks[1].needsApply)
        assertEquals(16_000L, tasks[1].waitMillis)
    }

    @Test
    fun `汇总目标尚未下发时不限制本次执行数量`() {
        val progress = MemberTaskProgress(
            currentCount = 0,
            targetCount = 0,
            totalAwardPoint = 0,
            receivedAwardPoint = 0,
            status = ""
        )

        assertEquals(Int.MAX_VALUE, progress.remainingCount)
    }

    @Test
    fun `普通任务领取和执行请求匹配新版接口`() {
        val task = MemberBrowseTask(
            configId = "config-2",
            processId = "",
            title = "再逛一逛",
            status = "INIT",
            browseSeconds = 12,
            bizType = "BROWSE",
            bizSubType = "UNLIMITED",
            bizParam = "biz-param-2"
        )

        val applyRequest = MemberTaskProtocol.buildApplyTaskArgs(task).getJSONObject(0)
        assertEquals("config-2", applyRequest.getString("taskConfigId"))

        val executeRequest = MemberTaskProtocol
            .buildExecuteTaskArgs(task, 123456789L)
            .getJSONObject(0)
        assertEquals("BROWSE", executeRequest.getString("bizType"))
        assertEquals("UNLIMITED", executeRequest.getString("bizSubType"))
        assertEquals("biz-param-2", executeRequest.getString("bizParam"))
        assertEquals("123456789", executeRequest.getString("outBizNo"))
    }

    @Test
    fun `解析会员累计任务进度和已领取奖励`() {
        val response = JSONObject(
            """
            {
              "success": true,
              "availableTaskProcessList": [{
                "currentCount": 2,
                "targetCount": 8,
                "status": "PROCESSING",
                "stageProcessList": [
                  {"awardPoint": 5, "stageStatus": "COMPLETE"},
                  {"awardPoint": 10, "stageStatus": "PROCESSING"},
                  {"awardPoint": 15, "stageStatus": "PROCESSING"}
                ],
                "taskConfig": {"taskStyle": "WELFARE_TASK"}
              }]
            }
            """.trimIndent()
        )

        val progress = MemberTaskProtocol.parseProgress(response)

        assertEquals(2, progress.currentCount)
        assertEquals(8, progress.targetCount)
        assertEquals(30, progress.totalAwardPoint)
        assertEquals(5, progress.receivedAwardPoint)
        assertFalse(progress.completed)
    }

    @Test
    fun `结算响应兼容标准成功和零错误码`() {
        assertTrue(MemberTaskProtocol.isFinishSuccess(JSONObject("""{"success":true}""")))
        assertTrue(MemberTaskProtocol.isFinishSuccess(JSONObject("""{"errCode":"0"}""")))
        assertFalse(MemberTaskProtocol.isFinishSuccess(JSONObject("""{"errCode":"500"}""")))
    }
}
