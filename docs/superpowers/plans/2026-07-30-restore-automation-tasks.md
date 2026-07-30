# 7 月 29 日自动任务恢复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢复庄园小游戏、新村 XLight、文体中心普通任务和游戏中心真实游戏/广告任务，并以服务端回查确认每个动作。

**Architecture:** 每个模块使用独立策略和工作流，将 RPC、等待、页面启动等外部动作作为回调注入。工作流只根据动作前后快照判定成功，缺字段、无进展或达到步骤上限时停止。

**Tech Stack:** Kotlin、Java、JUnit 4、org.json、Android Intent、现有 RequestManager 与任务模型框架。

## Global Constraints

- 文件使用 UTF-8 无 BOM，代码注释使用中文。
- 购买、充值、提现、兑换、借贷、投资、现金和捐赠任务继续阻断。
- 游戏与广告动态参数只取自当前服务端响应。
- RPC 成功必须经过任务列表回查才能确认。
- 不修改或提交两份 2026-07-28 fishpond 未跟踪文档。

---

### Task 1: 恢复庄园小游戏

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/FarmGameWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRpcCall.java`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarm.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/FarmGameWorkflowTest.kt`

**Interfaces:**
- Consumes: `AntFarmRpcCall.initFarmGame(gameType)` 和恢复后的 `recordFarmGame(gameType)`。
- Produces: `FarmGameWorkflow.play(gameType): FarmGameRunResult`，仅在剩余次数下降或奖励状态推进时返回确认。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `成绩提交后剩余次数下降才确认`() {
    val queries = ArrayDeque(listOf(snapshot(2, false), snapshot(1, false)))
    val result = FarmGameWorkflow(
        queryGame = { queries.removeFirst() },
        submitScore = { """{"success":true}""" },
        pauseAfterAction = {}
    ).play("starGame")
    assertEquals(FarmGameOutcome.CONFIRMED, result.outcome)
}
```

同时覆盖状态不变、字段缺失、提交失败和最大步骤限制。

- [ ] **Step 2: 运行测试确认红灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FarmGameWorkflowTest" --no-daemon`

Expected: FAIL，因为 `FarmGameWorkflow` 尚不存在。

- [ ] **Step 3: 实现最小工作流和 RPC**

```kotlin
class FarmGameWorkflow(
    private val queryGame: (String) -> String,
    private val submitScore: (String) -> String,
    private val pauseAfterAction: () -> Unit,
    private val maxSteps: Int = 20
) {
    fun play(gameType: String): FarmGameRunResult
}
```

恢复 UUID、MD5 和各游戏分数范围；`AntFarm` 在用户原有 `recordFarmGame` 开关开启时执行四类游戏。

- [ ] **Step 4: 运行定向测试确认绿灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FarmGameWorkflowTest" --tests "*FarmGameReadOnlyWorkflowTest" --no-daemon`

Expected: PASS。

---

### Task 2: 恢复新村 XLight

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/StallXlightWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/StallTaskSafetyPolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/AntStallRpcCall.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/AntStall.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antStall/StallXlightWorkflowTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antStall/StallTaskSafetyPolicyTest.kt`

**Interfaces:**
- Consumes: XLight 查询响应中的 `playingBizId` 与 `eventRewardInfoList`。
- Produces: `StallXlightWorkflow.run(before): StallXlightResult`。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `广告事件全部完成且任务状态推进后确认`() {
    val result = workflow(
        queryAd = { xlightResponse("play-1", event("event-1")) },
        finishEvent = { _, _ -> """{"success":true}""" },
        refreshTask = { StallTaskState(XLIGHT_TYPE, "FINISHED") }
    ).run(StallTaskState(XLIGHT_TYPE, "TODO"))
    assertEquals(StallXlightOutcome.CONFIRMED, result.outcome)
}
```

同时覆盖双码流量限制、缺播放 ID、空事件、完成失败和状态无进展。

- [ ] **Step 2: 运行测试确认红灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*StallXlightWorkflowTest" --no-daemon`

Expected: FAIL，因为工作流和 `HANDLE_XLIGHT` 尚不存在。

- [ ] **Step 3: 实现动态广告工作流**

```kotlin
class StallXlightWorkflow(
    private val queryAd: () -> String,
    private val finishEvent: (String, JSONObject) -> String,
    private val refreshTask: () -> StallTaskState?,
    private val pauseAfterAction: (Long) -> Unit
) {
    fun run(before: StallTaskState): StallXlightResult
}
```

RPC 使用旧接口结构，但 `playingBizId` 和事件对象必须来自本次查询响应。`AntStall.taskList()` 仅在状态推进后领奖。

- [ ] **Step 4: 运行定向测试确认绿灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*StallXlightWorkflowTest" --tests "*StallTaskSafetyPolicyTest" --tests "*StallTaskProtocolTest" --no-daemon`

Expected: PASS。

---

### Task 3: 恢复文体中心普通任务

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/SportsTaskPolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/SportsTaskWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSports.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/SportsTaskPolicyTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/SportsTaskWorkflowTest.kt`

**Interfaces:**
- Consumes: `userTaskComplete(bizType, taskId)` 与任务组查询。
- Produces: 新动作 `SportsTaskAction.COMPLETE_TASK`。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `普通日常任务完成并回查到COMPLETED才确认`() {
    val result = workflow(
        queryGroup = responses(todoTask("browse"), completedTask("browse")),
        completeTask = { _, _ -> """{"success":true}""" }
    ).run("SPORTS_DAILY_GROUP")
    assertEquals(SportsTaskOutcome.CONFIRMED, result.outcomes.single())
}
```

继续验证广告、游戏和资金任务返回 `SKIPPED_UNSAFE`，状态不变返回 `RETRY`。

- [ ] **Step 2: 运行测试确认红灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SportsTaskPolicyTest" --tests "*SportsTaskWorkflowTest" --no-daemon`

Expected: FAIL，因为普通任务仍被策略跳过。

- [ ] **Step 3: 最小扩展策略和工作流**

```kotlin
enum class SportsTaskAction {
    COMPLETE_SIGN_IN,
    COMPLETE_TASK,
    CLAIM_REWARD,
    SKIP_UNSAFE,
    NONE
}
```

仅 `SPORTS_DAILY_GROUP` 的无风险 `TODO` 任务进入 `COMPLETE_TASK`；完成后重新查询相同组和任务 ID。

- [ ] **Step 4: 运行定向测试确认绿灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SportsTaskPolicyTest" --tests "*SportsTaskWorkflowTest" --no-daemon`

Expected: PASS。

---

### Task 4: 恢复游戏中心真实游戏与广告

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/GameCenterInteractiveTaskWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/GameCenterTaskPolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/GameCenterPlatformWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/GameCenterP2eTaskWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMemberRpcCall.java`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/GameCenterInteractiveTaskWorkflowTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/GameCenterTaskPolicyTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/GameCenterPlatformWorkflowTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/GameCenterP2eTaskWorkflowTest.kt`

**Interfaces:**
- Consumes: 任务对象中的 `jumpLink`、游戏/应用 ID、等待时长、广告位置参数与实时广告事件。
- Produces: `GameCenterInteractiveTaskWorkflow.execute(task): GameCenterInteractiveResult`。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `真实游戏按报名启动模拟完成和回查顺序执行`() {
    val calls = mutableListOf<String>()
    val result = workflow(calls).execute(realGameTask())
    assertEquals(
        listOf("signup", "refresh", "launch", "simulate", "complete", "refresh"),
        calls
    )
    assertEquals(GameCenterInteractiveOutcome.CONFIRMED, result.outcome)
}
```

另测支付宝深链白名单、缺字段跳过、广告查询/事件/完成顺序、状态无进展和步骤上限。

- [ ] **Step 2: 运行测试确认红灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*GameCenterInteractiveTaskWorkflowTest" --no-daemon`

Expected: FAIL，因为交互任务工作流尚不存在。

- [ ] **Step 3: 实现交互动作与动态参数校验**

```kotlin
class GameCenterInteractiveTaskWorkflow(
    private val signup: (JSONObject) -> String,
    private val launch: (String) -> Boolean,
    private val simulateGame: (JSONObject) -> String,
    private val queryAd: (JSONObject) -> String,
    private val finishAdEvent: (String, JSONObject) -> String,
    private val complete: (JSONObject) -> String,
    private val refresh: (String) -> JSONObject?,
    private val pause: (Long) -> Unit,
    private val maxSteps: Int = 20
) {
    fun execute(task: JSONObject): GameCenterInteractiveResult
}
```

页面启动使用支付宝上下文的 `Intent.ACTION_VIEW`。只允许 `alipays`、`alipay` 和 HTTPS；参数缺失时不启动、不提交。

- [ ] **Step 4: 接入平台与 P2E 工作流**

把 `GAME_TRAN_TASK` 和 `LIGHT_AD_TASK` 分类为交互任务，在现有报名、完成、领奖和回查流程中调用新工作流；金融任务仍优先阻断。

- [ ] **Step 5: 运行游戏中心定向测试确认绿灯**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*GameCenter*Test" --no-daemon`

Expected: PASS。

---

### Task 5: 全量验证和文档更新

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: 四个已通过定向测试的工作流。
- Produces: 可发布的完整变更。

- [ ] **Step 1: 更新最近功能记录**

在 README 顶部记录四类任务恢复、动态参数要求、服务端回查和资金/捐赠阻断。

- [ ] **Step 2: 运行完整验证**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --rerun-tasks`

Run: `uv run python -m unittest discover -v`，工作目录 `serve-debug`。

Run: `git diff --check`

Expected: Android 与 Python 测试全部通过，差异检查无错误。

- [ ] **Step 3: 核对提交范围**

Run: `git status --short`

Expected: 两份 2026-07-28 fishpond 文档保持未跟踪，未进入暂存区。

- [ ] **Step 4: 使用中文提交**

```powershell
git add -- README.md app/src/main/java/fansirsqi/xposed/sesame/task app/src/test/java/fansirsqi/xposed/sesame/task docs/superpowers/plans/2026-07-30-restore-automation-tasks.md
git commit -m "功能：恢复安全闭环中的自动任务"
```
