# 金豆夺宝与福气鱼池任务补全 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在农场模型中增加金豆夺宝安全任务闭环，并按真实抓包补全福气鱼池广告浏览任务的复查链路。

**Architecture:** 金豆夺宝使用独立的 RPC 网关、纯任务策略和有限轮状态工作流，并由农场模型负责开关与异常隔离。鱼池沿用现有网关和工作流，只补广告配置查询、尽力曝光、动态等待和任务列表复查。

**Tech Stack:** Kotlin、Java 17、Android Gradle Plugin、`org.json`、Kotlin 协程、JUnit 4。

## Global Constraints

- 所有新增设置默认关闭。
- 支付和余额宝 `TODO` 任务不得主动完成，只领取服务端已经完成的奖励。
- 订阅、游戏和金豆乐园任务允许完成，但必须同步确认后再领奖。
- 肥料兑换和未知任务不得执行。
- 单次 RPC 成功不能代替服务端任务状态复查。
- 所有文件保存为 UTF-8 无 BOM，代码注释使用中文。
- 不修改工作区中与本功能无关的未跟踪文件。

---

### Task 1: 金豆 RPC 协议与任务策略

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/GoldenBeanRpcCall.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/GoldenBeanPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/GoldenBeanRpcProtocolTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/GoldenBeanPolicyTest.kt`

**Interfaces:**
- Produces: `GoldenBeanGateway.index()`, `sync(syncTypes)`, `fortuneDraw()`, `finishTask(taskType, sceneCode)`, `receiveTaskAward(taskType, sceneCode)`.
- Produces: `GoldenBeanPolicy.decide(snapshot): GoldenBeanTaskDecision`.
- Produces: `GoldenBeanPolicy.isRpcSuccess(response): Boolean`.

- [ ] **Step 1: Write failing protocol tests**

验证首页、同步、财运签、完成任务和领奖参数包含抓包确认的
`bizType=MASTER`、`source=babafarm`、`version=20260723.01`，且完成与领奖分别调用
`com.alipay.antieptask.finishTaskantorchard` 和
`com.alipay.antieptask.receiveTaskAwardantorchard`。

```kotlin
@Test
fun `金豆任务协议使用抓包确认参数`() {
    val finish = JSONObject(
        JSONArray(GoldenBeanRpcCall.buildFinishTaskArgs("GAME_TASK", "SCENE", "out-1"))
            .getJSONObject(0).toString()
    )
    assertEquals("MASTER", finish.getString("bizType"))
    assertEquals("babafarm", finish.getString("source"))
    assertEquals("GAME_TASK", finish.getString("taskType"))
}
```

- [ ] **Step 2: Run protocol tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GoldenBeanRpcProtocolTest" --no-daemon
```

Expected: FAIL because `GoldenBeanRpcCall` does not exist.

- [ ] **Step 3: Implement the RPC gateway**

用 `JSONObject` 和 `JSONArray` 构造参数，避免字符串拼接。`finishTask` 的
`outBizNo` 使用当前毫秒与随机后缀生成；领奖参数包含 `ignoreLimit=true` 和
`bizInfo.bizType=MASTER`。

```kotlin
interface GoldenBeanGateway {
    fun index(): String
    fun sync(syncTypes: List<String>): String
    fun fortuneDraw(): String
    fun finishTask(taskType: String, sceneCode: String): String
    fun receiveTaskAward(taskType: String, sceneCode: String): String
}
```

- [ ] **Step 4: Write failing policy tests**

覆盖以下决策：

```kotlin
assertEquals(WAIT, decide(type = "GOLDEN_BEAN_TASK_XIANSHANGZHIFU", status = "TODO"))
assertEquals(CLAIM, decide(type = "GOLDEN_BEAN_TASK_YUEBAO", status = "FINISHED"))
assertEquals(COMPLETE, decide(actionType = "PUSH_SUBSCRIBE", status = "TODO"))
assertEquals(COMPLETE, decide(type = "GOLDENBEAN_GAME_ZH_CGNNC", actionType = "VISIT"))
assertEquals(COMPLETE, decide(actionType = "GAMECENTER_TRIGGER", status = "TODO"))
assertEquals(FORTUNE_DRAW, decide(type = "FORTUNE_DRAW", status = "TODO"))
assertEquals(SKIP, decide(type = "MANURE_EXCHANGE", status = "TODO"))
```

- [ ] **Step 5: Run policy tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GoldenBeanPolicyTest" --no-daemon
```

Expected: FAIL because `GoldenBeanPolicy` does not exist.

- [ ] **Step 6: Implement the conservative policy**

拒绝规则先匹配支付、余额宝和肥料兑换，再匹配财运签、订阅、游戏与金豆乐园。所有
`FINISHED`、`TO_RECEIVE` 状态统一返回 `CLAIM`，`RECEIVED` 返回 `WAIT`，未知内容返回
`SKIP`。

- [ ] **Step 7: Run Task 1 tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GoldenBeanRpcProtocolTest" --tests "*GoldenBeanPolicyTest" --no-daemon
```

Expected: PASS.

### Task 2: 金豆有限轮状态闭环

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/GoldenBeanWorkflow.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/GoldenBeanWorkflowTest.kt`

**Interfaces:**
- Consumes: Task 1 `GoldenBeanGateway` and `GoldenBeanPolicy`.
- Produces: `GoldenBeanWorkflow.run(): GoldenBeanRunResult`.
- Produces: `GoldenBeanRunResult(progressed: Boolean, retryNeeded: Boolean, claimedCount: Int)`.

- [ ] **Step 1: Write failing workflow tests**

使用内存假网关验证：

```kotlin
assertEquals(
    listOf(
        "finish:JINDOULEYUAN_TRIGGER",
        "sync:TASK_LIST",
        "claim:JINDOULEYUAN_TRIGGER",
        "sync:TASK_LIST"
    ),
    fake.events
)
```

另测财运签只调用专用接口；支付与余额宝 `TODO` 不产生写请求；完成后仍为 `TODO` 时不领奖
并返回 `retryNeeded=true`；单任务异常后继续处理其他任务。

- [ ] **Step 2: Run workflow tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GoldenBeanWorkflowTest" --no-daemon
```

Expected: FAIL because `GoldenBeanWorkflow` does not exist.

- [ ] **Step 3: Implement normalized task parsing**

从根节点或 `data` 节点读取 `taskList`，将 `taskId`、`sceneCode`、`taskStatus`、
`actionType` 和标题归一化为 `GoldenBeanTaskSnapshot`。

- [ ] **Step 4: Implement finite state processing**

最多运行三轮。每个动作后调用 `sync(listOf("JAR_INFO", "TASK_LIST", "FARM_TASK", "SIGN"))`
并重读任务；只有 `FINISHED` 才领奖，只有 `RECEIVED` 才确认闭环。使用
`sceneCode|taskId|status|decision` 去重。

- [ ] **Step 5: Run workflow tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GoldenBeanWorkflowTest" --no-daemon
```

Expected: PASS.

### Task 3: 农场设置与异常隔离接入

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/GoldenBeanTreasure.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchard.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/OrchardFishPondExecution.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFishPond/AntFishPondConfigTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/OrchardFishPondExecutionTest.kt`

**Interfaces:**
- Consumes: Task 2 `GoldenBeanWorkflow`.
- Produces: `GoldenBeanTreasureRunner.run(enabled: Boolean)`.
- Produces: `OrchardFishPondExecution.run(orchardBlock, fishPondBlock, goldenBeanBlock)`.

- [ ] **Step 1: Write failing config and isolation tests**

```kotlin
assertFalse(AntOrchard().fields["goldenBeanTreasure"]?.value as Boolean)
```

执行顺序测试覆盖农场抛错仍执行鱼池，鱼池抛错仍执行金豆：

```kotlin
assertEquals(listOf("orchard", "fishpond", "goldenBean"), events)
```

- [ ] **Step 2: Run integration tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AntFishPondConfigTest" --tests "*OrchardFishPondExecutionTest" --no-daemon
```

Expected: FAIL because the field and third block do not exist.

- [ ] **Step 3: Implement runner and model field**

在 `AntOrchard.getFields()` 添加：

```kotlin
BooleanModelField(
    "goldenBeanTreasure",
    "金豆夺宝 | 任务与领奖",
    false
)
```

Runner 仅在开关开启时创建真实网关并运行工作流，记录确认领取数和可重试状态。

- [ ] **Step 4: Implement three-stage isolation**

`OrchardFishPondExecution` 分别捕获农场和鱼池阶段异常，并始终执行金豆阶段。异常由调用方现有
日志边界记录，不吞掉协程取消异常。

- [ ] **Step 5: Run integration tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AntFishPondConfigTest" --tests "*OrchardFishPondExecutionTest" --no-daemon
```

Expected: PASS.

### Task 4: 福气鱼池广告任务动态等待与复查

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFishPond/AntFishPondRpcCall.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFishPond/FishPondPolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFishPond/FishPondWorkflow.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFishPond/AntFishPondRpcProtocolTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFishPond/FishPondPolicyTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFishPond/FishPondWorkflowTest.kt`

**Interfaces:**
- Extends: `FishPondGateway.queryAdTaskConfig(spaceCode): String`.
- Extends: `FishPondGateway.requestAdExposure(spaceCode, pageUrl): String`.
- Produces: `FishPondPolicy.extractAdConfig(task): FishPondAdConfig`.
- Produces: `FishPondPolicy.adDurationMillis(response, task): Long`.

- [ ] **Step 1: Write failing protocol and parsing tests**

验证 `renderConfigKey` 解码得到完整广告位，页面 URL 解码为抓包中的 fishing landing URL，
以及广告配置响应 `resultData.duration=15.0` 得到 `15000L`。

- [ ] **Step 2: Run fish protocol and policy tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AntFishPondRpcProtocolTest" --tests "*FishPondPolicyTest" --no-daemon
```

Expected: FAIL because ad config helpers do not exist.

- [ ] **Step 3: Implement ad request protocol**

广告配置调用 `com.alipay.adtask.biz.mobilegw.service.applayer.query`。曝光调用
`com.alipay.adexchange.ad.facade.xlightPlugin`，使用随机会话值和抓包确认的 SDK 页面字段；
其失败返回不作为工作流阻断条件。

- [ ] **Step 4: Write failing workflow sequence tests**

期望事件顺序：

```kotlin
listOf(
    "notice:ad-1",
    "queryAdConfig",
    "requestAdExposure",
    "wait:15000",
    "finish:GYG_XLIGHT_JX_BUSINEES",
    "sync:FISH_ACTIVITY,TASK_DISPLAY,TOMORROW_ROD,LOTTERY_PLUS",
    "listTask"
)
```

另测曝光失败仍继续；复查仍为 `TODO` 时 `retryNeeded=true` 且不能记录已完成。

- [ ] **Step 5: Run workflow tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FishPondWorkflowTest" --no-daemon
```

Expected: FAIL because the workflow lacks ad query, exposure and explicit re-list verification.

- [ ] **Step 6: Implement the captured sequence**

仅对存在 `adBizNo` 的任务执行广告链路。查询失败时回退到任务描述时长；曝光请求无论业务
返回是否成功都继续等待。完成后同步并立即重新查询任务，只有 `FINISHED` 或 `RECEIVED`
才标记进展，否则设置可重试。

- [ ] **Step 7: Run fish tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AntFishPond*" --tests "*FishPond*" --no-daemon
```

Expected: PASS.

### Task 5: 回归验证

**Files:**
- Verify only; no production edits unless a regression identifies a scoped defect.

- [ ] **Step 1: Run focused orchard and fish tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GoldenBean*" --tests "*AntFishPond*" --tests "*FishPond*" --tests "*OrchardFishPondExecutionTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run the complete unit test suite**

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Build the Debug APK**

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify repository state**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; unrelated pre-existing untracked files remain untouched.
