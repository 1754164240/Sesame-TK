# 合种浇水状态确认实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 三种合种浇水均以动作后 RPC 状态推进为成功依据，本地能量和每日标记只在确认后更新。

**Architecture:** 新增合种专用快照解析和确认策略，不复用通用任务流。`AntCooperate` 负责 RPC 编排，策略只比较前后状态并返回明确结果。

**Tech Stack:** Kotlin/JVM、JUnit 4、org.json、Android Gradle Plugin

## Global Constraints

- 仅参考 Sesame-AG 提交 `f58bd257` 的协议字段与行为，禁止复制或 cherry-pick AGPL 源码。
- ACK 不代表成功；空响应、未知结构、状态未刷新均保留后续重试。
- 本地能量、`Status.cooperateWaterToday`、真爱和组队每日标记只能在回查确认后写入。
- 不引入通用任务引擎；保留三个模块专用流程。
- 所有 Gradle 命令追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`。
- 保留 dirty 工作区，不暂存、不提交、不切换分支。

---

### Task 1: 合种浇水确认策略

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antCooperate/CooperateWaterPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antCooperate/CooperateWaterPolicyTest.kt`

**Interfaces:**
- Produces: `CooperateWaterOutcome` with `CONFIRMED`, `RETRY`, `REJECTED`
- Produces: `CooperateWaterPolicy.normalRemaining(JSONObject, cooperationId): Int?`
- Produces: `CooperateWaterPolicy.loveTodayAmount(JSONObject, userId): Int?`
- Produces: `CooperateWaterPolicy.teamRemaining(JSONObject): Int?`
- Produces: three `confirm*` methods comparing before and after values.

- [x] **Step 1: 写失败测试**

普通合种只在同一 `cooperationId` 的 `waterDayLimit` 降低时确认；真爱合种只在 `todayWaterMap[userId]` 增加时确认；组队合种只在 `combineHandlerVOMap.teamCanWaterCount.waterCount` 降低时确认。容器缺失、值不变或反向变化均返回 `RETRY`。

- [x] **Step 2: 运行红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antCooperate.CooperateWaterPolicyTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：因策略类不存在而编译失败。

- [x] **Step 3: 最小实现**

使用 `JSONObject.opt*` 安全解析，任何身份不匹配、路径缺失或数值无法确认都返回 `RETRY`，不从 ACK 推导结果。

- [x] **Step 4: 运行绿灯**

重复 Step 2 命令，预期通过。

### Task 2: 三条浇水链路接入回查

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antCooperate/AntCooperate.kt`
- Test: `app/src/test/java/fansirsqi/xposed/sesame/task/antCooperate/CooperateWaterPolicyTest.kt`

**Interfaces:**
- Consumes: Task 1 的三类快照解析与确认函数。

- [x] **Step 1: 普通合种动作后回查**

保存动作前 `waterDayLimit`；ACK 成功后调用 `queryCooperatePlant(cooperationId)`，仅确认剩余额度降低后返回实际确认量、扣减当前运行内能量并调用 `Status.cooperateWaterToday`。

- [x] **Step 2: 真爱合种动作后回查**

保存动作前 `todayWaterMap[currentUid]`；ACK 成功后调用 `queryLoveHome()`，仅确认数值增加后记录成功并设置 `FLAG_ANTCOOPERATE_LOVE_TEAM_WATER`。

- [x] **Step 3: 组队合种动作后回查**

保存动作前 `teamCanWaterCount.waterCount`；ACK 成功后重新调用 `queryMiscInfo("teamCanWaterCount", teamId)`，仅确认剩余额度降低后把服务端实际差值累加到 `FLAG_TEAM_WATER_DAILY_COUNT`。

- [x] **Step 4: 合种模块回归**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antCooperate.*" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：全部通过，状态未刷新样本不产生本地成功写入。
