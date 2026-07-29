# 森林与青春特权闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐森林浇水控制、保护罩策略、抽抽乐确认和青春特权签到/免费道具的服务端回查闭环。

**Architecture:** 将可判定逻辑放入无 Android 依赖的策略类，`AntForest` 只负责读取配置和编排 RPC。青春特权从旧 `Privilege` 对象迁移为独立 `ModelTask`，旧森林入口只调用新模块的免费道具领取接口，不引入通用任务流。

**Tech Stack:** Kotlin、Java、JUnit 4、org.json、现有 ModelField 与 ModelTask。

## Global Constraints

- 新动作默认关闭；既有浇水行为的总开关默认开启以保持兼容。
- 不执行真实森林游戏、广告任务、兑换或付费动作。
- 签到和领奖必须重新查询确认后才能写每日标记。
- 不复制 Sesame-AG 源码或引入其 `TaskFlow`。
- 不创建 Git 提交。

---

### Task 1: 森林浇水与保护罩纯策略

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestAutomationPolicyTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestAutomationPolicy.kt`

**Interfaces:**
- Produces: `ForestWateringPolicy.shouldRunBeforeCollect(enabled, prioritized, executed): Boolean`
- Produces: `ForestWateringPolicy.shouldNotify(configuredWatering, configuredNotify, randomNotify): Boolean`
- Produces: `ForestShieldPolicy.shouldRenew(endTime, now, thresholdHours): Boolean`
- Produces: `ForestShieldPolicy.isShield(propGroup, propType): Boolean`

- [x] 先写失败测试，覆盖总开关、优先执行去重、名单/随机通知分离、阈值边界、过旧异常时间和活动保护罩类型。
- [x] 运行 `ForestAutomationPolicyTest`，确认因类型不存在而失败。
- [x] 使用纯布尔与时间计算实现最小策略。
- [x] 重新运行测试并确认通过。

### Task 2: 浇水配置与执行顺序

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestAutomationPolicyTest.kt`

**Interfaces:**
- Produces fields: `wateringEnabled=true`、`waterFriendEnergyFirst=false`、`notifyRandomWatering=false`。

- [x] 写配置默认值测试。
- [x] 将三个字段加入森林配置。
- [x] 在好友能量收取前按策略执行一次名单浇水，并在后置阶段跳过重复执行。
- [x] 总开关关闭时跳过名单浇水、浇水金球和收能量后的返水。
- [x] 名单浇水使用 `notifyFriend`，随机返水使用 `notifyRandomWatering`。
- [x] 运行全部森林测试。

### Task 3: 保护罩配置和动态识别

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestAutomationPolicyTest.kt`

**Interfaces:**
- Produces field: `shieldRenewThresholdHours=24`，范围 `0..168`。

- [x] 写配置默认值和源代码不再含固定保护罩列表的失败测试。
- [x] 使用 `ForestShieldPolicy.shouldRenew` 替换固定 `2359` 判断。
- [x] 使用 `propGroup=shield` 或 `propType` 包含 `ENERGY_SHIELD` 收集保护罩。
- [x] 青春特权领取和兑换后的重新扫描复用同一个收集函数。
- [x] 运行森林测试。

### Task 4: 青春特权响应策略

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/youthPrivilege/YouthPrivilegePolicyTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/youthPrivilege/YouthPrivilegePolicy.kt`

**Interfaces:**
- Produces: `checkInDecision(response): QUERY_DONE | EXECUTE | RETRY`
- Produces: `rewardDecision(response, taskType): CLAIM | CONFIRMED | RETRY`

- [x] 使用脱敏响应样本写签到、已签到、可领奖、已领取、未知结构测试。
- [x] 运行测试确认类型不存在。
- [x] 实现兼容 `resultCode/code/success` 和嵌套数据的解析策略。
- [x] 未知结构一律返回 `RETRY`。

### Task 5: 独立青春特权模块

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/youthPrivilege/YouthPrivilege.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/youthPrivilege/YouthPrivilegeWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/model/ModelOrder.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/Privilege.kt`

**Interfaces:**
- Fields: `youthPrivilegeCheckIn=false`、`youthPrivilegeForestProps=false`、`youthPrivilegeTasks=false`。
- Produces: `YouthPrivilege.claimForestPropsFromForest(): Boolean`。

- [x] 写模块注册、默认关闭和工作流回查测试。
- [x] 注册独立会员分组模块。
- [x] 复用现有青春特权 RPC，签到后重新查询模型，道具领取后重新查询任务列表。
- [x] 只有三个白名单免费森林道具允许领取；青春任务开关首版只查询并记录，不自动完成未知任务。
- [x] 森林旧字段读取时兼容调用新模块，旧配置值不丢失。
- [x] 运行青春特权和森林测试。

### Task 6: 森林抽抽乐终态收紧

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestChouChouLe.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestChouChouLeTest.kt`

- [x] 增加动作成功但列表未刷新、服务端已领取、未知累计奖励结构测试。
- [x] 动作后重新查询任务列表；没有终态时保留重试。
- [x] 删除没有服务端字段证明的累计奖励推定。
- [x] 运行 `ForestChouChouLeTest`。

### Task 7: 批次验收

- [x] 运行 `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antForest.*" --tests "fansirsqi.xposed.sesame.task.youthPrivilege.*" --no-daemon --console=plain`。
- [x] 运行完整 `testDebugUnitTest`。
- [x] 运行 `assembleDebug`。
- [x] 检查 `git diff --check`，不创建 Git 提交。
