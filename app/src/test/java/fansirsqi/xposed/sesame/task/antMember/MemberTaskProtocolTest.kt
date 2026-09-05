package fansirsqi.xposed.sesame.task.antMember

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberTaskProtocolTest {
    @Test
    fun `游戏完成必须有明确终态而非成功空包装或未知状态`() {
        assertFalse(MemberTaskProtocol.isGameTaskCompleted(JSONObject("""{"success":true}""")))
        assertFalse(MemberTaskProtocol.isGameTaskCompleted(JSONObject("""{"success":true,"data":{"taskStatus":"PROCESSING"}}""")))
        assertFalse(MemberTaskProtocol.isGameTaskCompleted(JSONObject("""{"success":false,"data":{"taskStatus":"FINISHED"}}""")))
        assertTrue(MemberTaskProtocol.isGameTaskCompleted(JSONObject("""{"success":true,"data":{"taskStatus":"FINISHED"}}""")))
    }

    @Test
    fun `会员游戏同任务成功后当天不再执行且重建策略保留状态`() {
        val stored = mutableSetOf<String>()
        fun policy() = MemberGameDailyPolicy(stored::contains, { stored.add(it) }, { "account-a" }, { "2026-09-05" })
        val first = policy()
        val ticket = requireNotNull(first.tryStart("platform:game-a"))
        assertNull(policy().tryStart("platform:game-a"))
        first.finish(ticket, true)
        assertNull(policy().tryStart("platform:game-a"))
        val other = requireNotNull(policy().tryStart("platform:game-b"))
        policy().finish(other, true)
        assertEquals(2, stored.count { it.endsWith("::done") })
    }

    @Test
    fun `会员游戏失败保留当日尝试但不写成功状态`() {
        val stored = mutableSetOf<String>()
        val policy = MemberGameDailyPolicy(stored::contains, { stored.add(it) }, { "account-failure" }, { "2026-09-05" })
        val ticket = requireNotNull(policy.tryStart("game"))
        policy.finish(ticket, false)
        assertTrue(stored.single().endsWith("::attempted"))
        assertNull(policy.tryStart("game"))
        val other = requireNotNull(policy.tryStart("other-game"))
        policy.finish(other, false, skipToday = true)
        assertNull(policy.tryStart("other-game"))
        assertTrue(stored.any { it.endsWith("::skipped") })
        assertFalse(stored.any { it.endsWith("::done") })
    }

    @Test
    fun `会员游戏跨日及跨账号独立且旧请求不污染新日期`() {
        val stored = mutableSetOf<String>()
        var account = "account-day"
        var date = "2026-09-05"
        val policy = MemberGameDailyPolicy(stored::contains, { stored.add(it) }, { account }, { date })
        policy.finish(requireNotNull(policy.tryStart("game")), true)
        account = "account-other"
        policy.finish(requireNotNull(policy.tryStart("game")), true)
        val previousDay = requireNotNull(policy.tryStart("old-request"))
        date = "2026-09-06"
        stored.clear()
        policy.finish(previousDay, true)
        assertTrue(stored.isEmpty())
        policy.finish(requireNotNull(policy.tryStart("game")), true)
        assertEquals(2, stored.size)
        assertTrue(stored.all { it.contains("2026-09-06") })
    }

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

    @Test
    fun `广告任务过滤玩游戏进度任务但保留普通广告`() {
        val response = JSONObject(
            """
            {
              "resultData": {
                "adTaskList": [{
                  "adTask": true,
                  "lightsAdExtMap": {"adId": "game-ad", "bizId": "game-biz"},
                  "simpleTaskConfig": {"title": " 玩 游戏闯关得积分 "}
                }, {
                  "adTask": true,
                  "lightsAdExtMap": {"adId": "normal-ad", "bizId": "normal-biz"},
                  "simpleTaskConfig": {"title": "浏览会场得积分"}
                }]
              }
            }
            """.trimIndent()
        )

        val tasks = MemberTaskProtocol.parseAdTasks(response)

        assertEquals(1, tasks.size)
        assertEquals("normal-ad", tasks.single().adId)
    }

    @Test
    fun `解析宝箱查询响应中的动态任务`() {
        val response = JSONObject(
            """
            {
              "success": true,
              "taskType": "MULTIPLE_TIMER_TASK",
              "currentTaskInfo": {
                "awardNum": 1,
                "bizNo": "dynamic-box",
                "endDt": 1785191720354,
                "taskStatus": "PROCESSING"
              }
            }
            """.trimIndent()
        )

        val task = MemberTaskProtocol.parseTreasureBoxTask(response)

        assertEquals("dynamic-box", task?.bizNo)
        assertEquals("MULTIPLE_TIMER_TASK", task?.taskType)
        assertEquals(1785191720354L, task?.endTime)
        assertEquals(1, task?.awardNum)
    }

    @Test
    fun `宝箱领取请求透传动态编号和任务类型`() {
        val task = MemberTreasureBoxTask(
            bizNo = "dynamic-box",
            taskType = "MULTIPLE_TIMER_TASK",
            endTime = 1785191720354L,
            awardNum = 1
        )

        val request = MemberTaskProtocol.buildTriggerTreasureBoxArgs(task).getJSONObject(0)

        assertEquals("dynamic-box", request.getString("bizNo"))
        assertEquals("MULTIPLE_TIMER_TASK", request.getString("taskType"))
        assertEquals(0, request.getJSONObject("extMap").length())
        assertEquals(
            "ch_appcenter__chsub_9patch",
            request.getJSONObject("sourcePassMap").getString("source")
        )
    }

    @Test
    fun `动态游戏入口解码活动上下文`() {
        val response = JSONObject(
            """
            {
              "success": true,
              "actionUrl": "alipays://platformapi/startapp?appId=2021003125685383&url=https%3A%2F%2Frender.alipay.com%2Findex.html%3FchInfo%3Dzfbhy_mc_xgmqck81%26tab%3Dpromote%26channelTaskPassThrough%3D%252522%25257B%25255C%252522sceneId%25255C%252522%25253A%25255C%252522CY26_JULY%25255C%252522%25252C%25255C%252522taskId%25255C%252522%25253A%25255C%252522hyjmwf07%25255C%252522%25257D%252522"
            }
            """.trimIndent()
        )

        val context = MemberTaskProtocol.parseGameVisitContext(response)

        assertEquals("zfbhy_mc_xgmqck81", context?.source)
        assertEquals("promote", context?.tab)
        assertEquals("CY26_JULY", context?.sceneId)
        assertEquals("hyjmwf07", context?.taskId)
        assertEquals("limited:[\"CY26_JULY\",\"hyjmwf07\"]", context?.dailyTaskId)
    }

    @Test
    fun `新版游戏入口使用外层来源和内层场景且不编造旧任务参数`() {
        val response = JSONObject(
            """
            {
              "success": true,
              "resultCode": "SUCCESS",
              "critical": false,
              "retriable": false,
              "gameEntrancePointNum": 30,
              "actionUrl": "alipays://platformapi/startapp?appId=2021003125685383&url=https%3A%2F%2Frender.alipay.com%2Fp%2Fyuyan%2F180020010001206617%2FexternalGameCenter.html%3FcaprMode%3Dsync%26sceneId%3Dsample_scene&chInfo=sample_source&startMultApp=YES&appClearTop=false"
            }
            """.trimIndent()
        )

        val context = MemberTaskProtocol.parseGameVisitContext(response)

        assertEquals("sample_source", context?.source)
        assertEquals("sample_scene", context?.sceneId)
        assertEquals("", context?.tab)
        assertEquals("", context?.taskId)
        assertEquals("external:[\"sample_scene\",\"sample_source\"]", context?.dailyTaskId)
        val request = MemberTaskProtocol.buildGameHomeArgs(requireNotNull(context)).getJSONObject(0)
        assertEquals("sample_scene", request.getString("sceneId"))
        assertEquals("sample_source", request.getString("source"))
        assertEquals("", request.getString("channelTaskPassThrough"))
        assertEquals("", request.getString("guideType"))
        assertEquals("", request.getString("moduleId"))
        assertFalse(request.has("sourceTab"))
        assertFalse(request.has("__git"))
    }

    @Test
    fun `成功响应没有活动入口时正常跳过`() {
        assertNull(MemberTaskProtocol.parseGameVisitContext(JSONObject("""{"success":true}""")))
        assertNull(MemberTaskProtocol.parseGameVisitContext(JSONObject("""{"success":true,"actionUrl":""}""")))
        assertNull(MemberTaskProtocol.parseGameVisitContext(JSONObject("""{"success":true,"actionUrl":null}""")))
    }

    @Test
    fun `入口格式错误只提供缺失字段诊断且不泄露地址参数`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MemberTaskProtocol.parseGameVisitContext(
                JSONObject().put("actionUrl", "alipays://platformapi/startapp?secret=private-token")
            )
        }
        assertEquals("入口缺少 url", error.message)
    }

    @Test
    fun `新版入口缺少场景时拒绝猜测旧活动参数`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MemberTaskProtocol.parseGameVisitContext(JSONObject().put(
                "actionUrl",
                "alipays://platformapi/startapp?url=https%3A%2F%2Frender.alipay.com%2FexternalGameCenter.html%3FcaprMode%3Dsync&chInfo=sample_source"
            ))
        }
        assertEquals("新版入口缺少 sceneId", error.message)
    }

    @Test
    fun `旧版入口缺少任务透传时报告必要字段而非无活动`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MemberTaskProtocol.parseGameVisitContext(JSONObject().put(
                "actionUrl",
                "alipays://platformapi/startapp?url=https%3A%2F%2Frender.alipay.com%2Findex.html%3Ftab%3Dpromote&chInfo=sample_source"
            ))
        }
        assertEquals("旧版入口缺少 channelTaskPassThrough", error.message)
    }

    @Test
    fun `游戏访问请求使用动态活动参数且不携带前端版本戳`() {
        val context = MemberGameVisitContext(
            source = "dynamic-source",
            tab = "promote",
            sceneId = "dynamic-scene",
            taskId = "dynamic-task"
        )

        val home = MemberTaskProtocol.buildGameHomeArgs(context).getJSONObject(0)
        assertEquals("dynamic-source", home.getString("source"))
        assertEquals("promote", home.getString("sourceTab"))
        assertFalse(home.has("__git"))

        val module = MemberTaskProtocol.buildGameModuleArgs(context).getJSONObject(0)
        val passThrough = JSONObject(module.getString("channelTaskPassThrough"))
        assertEquals("dynamic-scene", passThrough.getString("sceneId"))
        assertEquals("dynamic-task", passThrough.getString("taskId"))
        assertFalse(module.has("__git"))

        val main = MemberTaskProtocol.buildWalkMainArgs(context).getJSONObject(0)
        assertFalse(main.getBoolean("cumulativeRechargePopupShown"))
        assertFalse(main.getBoolean("fallbackTaskPopupShownToday"))
        assertFalse(main.getBoolean("firstPayPopupShown"))
        assertEquals(module.getString("channelTaskPassThrough"), main.getString("channelTaskPassThrough"))
    }

    @Test
    fun `积分明细识别当天限时游戏访问奖励`() {
        val response = JSONObject(
            """
            {
              "summaries": [{
                "details": [
                  {"date": "2026-07-27", "memo": "限时游戏访问奖励", "point": "+1"},
                  {"date": "2026-07-28", "memo": "限时游戏访问奖励", "point": "+1"}
                ]
              }]
            }
            """.trimIndent()
        )

        assertTrue(
            MemberTaskProtocol.hasPointRecord(
                response,
                "2026-07-28",
                "限时游戏访问奖励",
                "+1"
            )
        )
        assertFalse(
            MemberTaskProtocol.hasPointRecord(
                response,
                "2026-07-29",
                "限时游戏访问奖励",
                "+1"
            )
        )
    }

    @Test
    fun `任务安全校验失败识别为预期业务拒绝`() {
        assertTrue(
            MemberTaskProtocol.isExpectedTaskRejection(
                JSONObject("""{"success":false,"resultDesc":"任务全性校验失败"}""")
            )
        )
        assertFalse(
            MemberTaskProtocol.isExpectedTaskRejection(
                JSONObject("""{"success":false,"resultDesc":"系统异常"}""")
            )
        )
    }
}
