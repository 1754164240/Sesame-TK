# 神奇海洋安全闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为海洋自收、生态候选和每日任务补齐独立配置、服务端身份字段与安全分类，避免低于阈值收取和不可闭环任务被伪完成。

**Architecture:** 新增海洋专用纯策略类，负责能量字段、生态身份和任务类型判定；`AntOcean` 只消费判定结果。保留现有 RPC 封装，不复制上游实现，不引入通用任务引擎。

**Tech Stack:** Kotlin/JVM、Java、JUnit 4、org.json、Android Gradle Plugin

## Global Constraints

- 仅参考 Sesame-AG 提交 `a4984dbe`、`89d93600` 的协议字段与行为，禁止复制或 cherry-pick AGPL 源码。
- 仅支持 libxposed API 102。
- 新增自动动作开关默认关闭。
- 空响应、未知结构和动作后状态未刷新均不得记成功。
- 禁止真实游戏、广告伪完成、金融、提现、下单和付费动作。
- 所有 Gradle 命令追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`。
- 保留 dirty 工作区，不暂存、不提交、不切换分支。

---

### Task 1: 海洋能量阈值策略

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOcean/OceanSelfCollectPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOcean/OceanSelfCollectPolicyTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOcean/AntOcean.java`

**Interfaces:**
- Produces: `OceanSelfCollectPolicy.energyOf(JSONObject): Int?`
- Produces: `OceanSelfCollectPolicy.shouldCollect(JSONObject, Int): Boolean`

- [x] **Step 1: 写失败测试**

覆盖 `fullEnergy` 优先于 `energy`、缺失/非数字能量返回 `null`、非 `ocean` 或非 `AVAILABLE` 跳过、能量等于阈值允许收取、低于阈值跳过。

- [x] **Step 2: 运行红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antOcean.OceanSelfCollectPolicyTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：因 `OceanSelfCollectPolicy` 不存在而编译失败。

- [x] **Step 3: 最小实现并接入**

实现纯函数策略；在 `AntOcean` 增加默认关闭的 `BooleanModelField("collectOceanEnergy", ...)` 和独立 `IntegerModelField("oceanSelfCollectEnergyThreshold", ..., 0)`。只有开关开启且策略允许时调用既有 `AntForestRpcCall.collectEnergy`。

- [x] **Step 4: 运行绿灯**

重复 Step 2 命令，预期通过。

### Task 2: 服务端生态身份与安全任务分类

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOcean/OceanCultivationPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOcean/OceanTaskSafetyPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOcean/OceanCultivationPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOcean/OceanTaskSafetyPolicyTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOcean/AntOcean.java`

**Interfaces:**
- Produces: `OceanCultivation(cultivationCode: String, projectCode: String, name: String, energy: Int, templateSubType: String)`
- Produces: `OceanCultivationPolicy.parse(JSONObject): OceanCultivation?`
- Produces: `OceanTaskDecision` with `ANSWER`, `FINISH_RPC`, `SKIP_GAME`, `SKIP_AD`, `SKIP_FINANCIAL`, `SKIP_BUSINESS_ACTION`, `SKIP_UNKNOWN`
- Produces: `OceanTaskSafetyPolicy.classify(taskType: String, title: String, actionType: String): OceanTaskDecision`

- [x] **Step 1: 写生态身份失败测试**

测试只接受 `cultivationCode` 与 `projectConfigVO.code` 均非空的候选；确认 `templateCode` 不再作为兑换身份；`AVAILABLE` 候选使用 `cultivationCode` 作为 `BeachMap` 键。

- [x] **Step 2: 写任务分类失败测试**

用字面量样本覆盖答题、明确 RPC 浏览任务、真实游戏/小游戏、广告、金融/下单、清理海域等需真实业务状态推进的任务，以及未知任务。除答题和明确 RPC 浏览白名单外均不得调用 `finishTask`。

- [x] **Step 3: 运行红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antOcean.OceanCultivationPolicyTest" `
  --tests "fansirsqi.xposed.sesame.task.antOcean.OceanTaskSafetyPolicyTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：因两个策略类不存在而编译失败。

- [x] **Step 4: 最小实现并接入**

`initBeach()` 在查询成功且容器存在时先清空旧候选，再仅保存可用且身份完整的生态；查询失败或缺少容器时保留原映射。`receiveTaskAward()` 对 TODO 任务先分类，危险、业务动作和未知类型只记录跳过，不调用 `finishTask`。

- [x] **Step 5: 海洋模块回归**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antOcean.*" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：全部通过。
