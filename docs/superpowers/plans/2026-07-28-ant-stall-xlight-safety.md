# 新村 XLight 安全闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 严格识别 XLight 双码流量风控、跳过小游戏及危险任务，并在完成和领奖动作后重新查询任务状态。

**Architecture:** 新增新村专用任务策略、XLight 风控策略和任务快照解析器；`AntStall` 保持现有循环结构，但所有动作结果必须由新任务列表确认。RPC 参数仅独立更新协议版本。

**Tech Stack:** Kotlin/JVM、JUnit 4、org.json、Android Gradle Plugin

## Global Constraints

- 仅参考 Sesame-AG 提交 `82c3873d` 的协议字段与行为，禁止复制或 cherry-pick AGPL 源码。
- XLight 只有 `retCode == "217"` 且 `sspErrorCode == "61002"` 才是流量风控。
- 命中风控立即停止当前 XLight 链路，即使响应含 `playingResult` 也不得继续。
- 小游戏、真实游戏、广告伪完成、金融与未知任务不得调用 `finishTask`。
- 动作后任务状态未刷新、容器缺失或响应为空均不得记成功。
- 所有 Gradle 命令追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`。
- 保留 dirty 工作区，不暂存、不提交、不切换分支。

---

### Task 1: 新村任务与 XLight 风控策略

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/StallTaskSafetyPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/StallTaskProtocol.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antStall/StallTaskSafetyPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antStall/StallTaskProtocolTest.kt`

**Interfaces:**
- Produces: `StallTaskDecision` with `FINISH_RPC`, `HANDLE_QA`, `HANDLE_INVITE`, `HANDLE_XLIGHT`, `SKIP_GAME`, `SKIP_AD`, `SKIP_FINANCIAL`, `SKIP_UNKNOWN`
- Produces: `StallTaskSafetyPolicy.classify(taskType, title, actionType): StallTaskDecision`
- Produces: `StallTaskProtocol.isXlightTrafficLimited(JSONObject): Boolean`
- Produces: `StallTaskProtocol.statusOf(response, taskType): StallTaskState?`
- Produces: `StallTaskProtocol.isAdvanced(before, after): Boolean`

- [x] **Step 1: 写任务安全分类失败测试**

覆盖既有明确 `VISIT_AUTO_FINISH` 白名单、问答、邀请、XLight、小游戏/真实游戏、广告、金融/下单和未知类型。`GAME`、`MINI_GAME`、`LIGHT_AD` 等信号优先于完成态和通用动作类型。

- [x] **Step 2: 写 XLight 与状态失败测试**

分别测试只有 217、只有 61002、两码分散在不同对象均不命中；同一对象或 `resData` 同时含双码才命中。测试 TODO 到 FINISHED/RECEIVED、FINISHED 到 RECEIVED 为推进，状态不变和未知结构不推进。

- [x] **Step 3: 运行红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antStall.StallTaskSafetyPolicyTest" `
  --tests "fansirsqi.xposed.sesame.task.antStall.StallTaskProtocolTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：因策略和协议类不存在而编译失败。

- [x] **Step 4: 最小实现**

分类顺序固定为游戏、广告、金融、专用安全处理器、明确 RPC 白名单、未知；状态解析仅接受 `taskModels` 数组和非空 `taskType/taskStatus`。

### Task 2: RPC 版本和动作后回查

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/AntStallRpcCall.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/AntStall.kt`

**Interfaces:**
- Consumes: Task 1 的任务分类、双码风控和状态推进判定。

- [x] **Step 1: 统一 XLight 版本**

将 `adComponentVersion`、`xlightRuntimeSDKversion`、`xlightSDKVersion` 统一为协议值 `4.31.4`；不增加广告伪完成接口。

- [x] **Step 2: 任务循环接入安全分类**

TODO 任务先分类；游戏、广告、金融和未知任务只记录跳过。只有策略返回 `FINISH_RPC` 时允许调用 `finishTask`。

- [x] **Step 3: 动作后回查**

`finishTask`、XLight 事件 `finish` 和 `receiveTaskAward` ACK 成功后重新调用 `taskList()`；只有同一 `taskType` 状态推进或从目标容器消失且前态已可领奖时确认。TODO 未变继续保留重试。

- [x] **Step 4: XLight 风控短路**

解析 `playingResult` 前调用 `isXlightTrafficLimited`；命中双码立即返回，不遍历事件、不调用 `finish`。

- [x] **Step 5: 新村模块回归**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antStall.*" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：全部通过。
