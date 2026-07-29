# 运动、庄园、果园与芝麻信用闭环 Implementation Plan

**Goal:** 按 Sesame-AG `dev` 近期协议行为重新实现四个现有业务域的服务端确认闭环，同时移除当前仓库中动态价格购买和错误完成标记。

**Architecture:** 每个业务域新增无 Android 依赖的响应策略或工作流，现有 `AntSports`、`AntFarm`、`AntOrchard`、`AntMember` 只保留配置读取、RPC 调用和日志编排。动作响应只作为“已提交”，最终状态必须来自重新查询。

**Upstream evidence:** `76d8f4488a`、`b132edc765`、`d4c462d797`、`b98c8dc7fb`、`2d98d1736f`、`9aaf251b02`、`6bde6e840f`、`74c371b446`。仅参考行为和协议，不复制 AGPL 源码。

## Global Constraints

- 新动作默认关闭，保留现有配置键。
- 不调用运动动态价格购买接口。
- 不执行真实游戏、广告伪完成、改分、付费兑换、提现或下单。
- 空响应、未知容器、状态未刷新和风控响应均保留重试。
- 不创建 Git 提交。

---

### Task 1: 健康岛解析和签到闭环

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/NeverlandPolicyTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/NeverlandPolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSports.kt`

- [x] 先写失败测试，兼容根对象、`data/result`、多个任务容器和 `id/taskId/taskRecordId`。
- [x] 签到查询明确已签到才写每日标记。
- [x] 签到动作后重新查询；动作失败或状态未刷新不写标记。
- [x] 任务中心失败达到上限只停止本轮，不写完成标记。

### Task 2: 健康岛任务、泡泡和免费奖励闭环

**Files:**
- Modify: `NeverlandPolicyTest.kt`
- Modify: `NeverlandPolicy.kt`
- Modify: `AntSports.kt`
- Modify: `AntSportsRpcCall.kt`

- [x] 任务发送、浏览完成和领奖后重新查询同一任务状态。
- [x] 泡泡兼容多种记录 ID 和奖励记录容器；领取后重新查询确认记录消失或已领取。
- [ ] 城市见闻和新路线只处理服务端明确的签到、问答、待收碎片和免费奖励。
- [x] 动态价格赎回只查询和记录状态，删除运行时购买调用。

### Task 3: 庄园延迟奖励与容量策略

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRewardPolicyTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRewardPolicy.kt`
- Modify: `AntFarm.kt`
- Modify: `AntFarmFamily.kt`

- [ ] 雇佣小鸡饲料任务和大表鸽奖励动作后回查任务或资产。
- [ ] 多项领奖前按剩余饲料容量选择可安全领取数量。
- [ ] 家庭签到、家庭奖励和乐园限时免费奖励动作后重新查询。
- [ ] 捐蛋排位、装扮商城只保留查询和明确免费领奖，不新增捐蛋或兑换动作。

### Task 4: 果园任务和限时奖励闭环

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchardRewardPolicyTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchardRewardPolicy.kt`
- Modify: `AntOrchard.kt`
- Modify: `AntOrchardRpcCall.kt`

- [ ] 兼容最新任务参数和任务容器。
- [ ] 完成任务和领奖后重新查询，只有 `RECEIVED` 或奖励记录消失才确认。
- [ ] 限时任务使用免费动作白名单；游戏、广告和付费任务跳过。
- [ ] 施肥与摇钱树计数继续使用当前项目的独立状态，不引入上游结构。

### Task 5: 芝麻炼金和芝麻粒终态

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/SesameCreditRewardPolicyTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/SesameCreditRewardPolicy.kt`
- Modify: `AntMember.kt`

- [ ] 芝麻炼金次日奖励和时段奖励领取后重新查询。
- [ ] 大表鸽芝麻粒只在资产或任务状态回查确认后记成功。
- [ ] 任务列表未知状态不写“全部完成”，保留后续重试。
- [ ] 满级红包只查询状态，不实现提现。

### Task 6: 批次验收

- [ ] 运行四个业务域的定向单元测试。
- [ ] 运行完整 `testDebugUnitTest`。
- [ ] 运行 `assembleDebug`。
- [ ] 运行 `git diff --check` 并审计禁用动作调用点。
