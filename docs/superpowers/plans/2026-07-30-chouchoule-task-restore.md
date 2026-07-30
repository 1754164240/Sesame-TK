# 抽抽乐任务执行恢复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢复庄园抽抽乐所有非捐赠任务的执行、状态回查和领奖闭环。

**Architecture:** 在现有 `ChouChouLeRewardWorkflow` 中注入任务执行函数，使状态机可测试；`ChouChouLe` 负责把任务路由到浏览任务或普通任务 RPC。每个动作后重新查询服务端状态，捐赠任务明确跳过且不阻塞完成。

**Tech Stack:** Kotlin 2.2、JUnit 4、`org.json`、Android Gradle Plugin

## Global Constraints

- 捐赠任务不得发起完成或领奖请求。
- 只有状态离开 `TODO` 后才能进入领奖阶段。
- 只有领奖后不再处于 `FINISHED` 才能确认完成。
- 修改文件使用 UTF-8 无 BOM，新增代码注释使用中文。

---

### Task 1: 恢复任务工作流状态机

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLeRewardWorkflow.kt`
- Test: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLeRewardWorkflowTest.kt`

**Interfaces:**
- Consumes: `queryTasks(drawType: String): String`、`receiveReward(drawType: String, taskId: String): String`
- Produces: `executeTask(drawType: String, task: ChouChouLeRewardTask): Boolean`

- [x] **Step 1: 写普通任务执行、捐赠跳过和状态未推进测试**

```kotlin
@Test
fun `普通TODO任务执行并回查后领取奖励`() {
    var queryCalls = 0
    var executeCalls = 0
    var receiveCalls = 0
    val workflow = ChouChouLeRewardWorkflow(
        queryTasks = {
            queryCalls++
            when (queryCalls) {
                1 -> taskResponse(taskJson("NORMAL", "普通任务", "TODO", ""))
                2 -> taskResponse(taskJson("NORMAL", "普通任务", "FINISHED", ""))
                else -> taskResponse(taskJson("NORMAL", "普通任务", "RECEIVED", ""))
            }
        },
        executeTask = { _, _ -> executeCalls++; true },
        receiveReward = { _, _ -> receiveCalls++; """{"success":true}""" }
    )
    val result = workflow.process("dailyDraw")
    assertEquals(1, executeCalls)
    assertEquals(1, receiveCalls)
    assertTrue(result.finished)
}
```

新增捐赠任务断言 `executeCalls == 0`、`receiveCalls == 0`、`result.finished == true`；新增执行 ACK 成功但回查仍为 `TODO` 时不领奖的断言。

- [x] **Step 2: 运行测试并确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ChouChouLeRewardWorkflowTest"`

Expected: FAIL，构造函数尚无 `executeTask` 参数，或普通 `TODO` 未执行。

- [x] **Step 3: 实现最小状态机**

扩展任务模型并注入执行函数：

```kotlin
data class ChouChouLeRewardTask(
    val taskId: String,
    val title: String,
    val status: String,
    val innerAction: String,
    val rightsTimes: Int,
    val rightsTimesLimit: Int
) {
    fun hasRemainingTimes(): Boolean =
        rightsTimes < rightsTimesLimit
}

class ChouChouLeRewardWorkflow(
    private val queryTasks: (String) -> String,
    private val executeTask: (String, ChouChouLeRewardTask) -> Boolean,
    private val receiveReward: (String, String) -> String
)
```

处理 `TODO` 时跳过 `innerAction == "DONATION"`；其他有剩余次数的任务调用 `executeTask`，成功后重新查询。重新查询变为 `FINISHED` 后进入领奖循环；领奖后通过 `alreadyReceiveStageAwardCount` 增加确认成功。同一任务在仍有剩余次数时继续执行，状态未推进则停止，单次最多执行 100 个动作。

- [x] **Step 4: 运行定向测试并确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ChouChouLeRewardWorkflowTest"`

Expected: PASS。

### Task 2: 接入浏览任务和普通任务执行器

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLe.kt`
- Test: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLeTaskPolicyTest.kt`

**Interfaces:**
- Consumes: `ChouChouLeRewardTask`、`AntFarmRpcCall.finishTask(...)`、`AntFarmRpcCall.chouchouleDoFarmTask(...)`
- Produces: `ChouChouLeTaskPolicy.route(taskId: String): ChouChouLeTaskRoute`

- [x] **Step 1: 写任务路由失败测试**

```kotlin
@Test
fun `两类杂货铺浏览任务使用浏览完成接口`() {
    assertEquals(
        ChouChouLeTaskRoute.BROWSE,
        ChouChouLeTaskPolicy.route("SHANGYEHUA_DAILY_DRAW_TIMES")
    )
    assertEquals(
        ChouChouLeTaskRoute.BROWSE,
        ChouChouLeTaskPolicy.route("IP_SHANGYEHUA_TASK")
    )
    assertEquals(
        ChouChouLeTaskRoute.FARM,
        ChouChouLeTaskPolicy.route("NORMAL_TASK")
    )
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ChouChouLeTaskPolicyTest"`

Expected: FAIL，策略类型尚不存在。

- [x] **Step 3: 实现路由策略和 RPC 接入**

```kotlin
enum class ChouChouLeTaskRoute { BROWSE, FARM }

object ChouChouLeTaskPolicy {
    private val browseTaskIds = setOf(
        "SHANGYEHUA_DAILY_DRAW_TIMES",
        "IP_SHANGYEHUA_TASK"
    )

    fun route(taskId: String): ChouChouLeTaskRoute =
        if (taskId in browseTaskIds) {
            ChouChouLeTaskRoute.BROWSE
        } else {
            ChouChouLeTaskRoute.FARM
        }
}
```

`ChouChouLe` 中的浏览任务先调用抓包中的 `applayer.query`，按返回的 `duration` 等待后调用 `finishTask`；其他任务调用 `chouchouleDoFarmTask`。两条链路都只把成功响应返回给工作流，捐赠任务由 Task 1 提前拦截。

- [x] **Step 4: 运行路由和工作流测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ChouChouLe*"`

Expected: PASS。

### Task 3: 完整验证

**Files:**
- Verify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLe.kt`
- Verify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLeRewardWorkflow.kt`
- Verify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLeRewardWorkflowTest.kt`
- Verify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/ChouChouLeTaskPolicyTest.kt`

- [x] **Step 1: 运行抽抽乐相关测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ChouChouLe*" --tests "*AntFarmRewardPolicyTest"`

Expected: PASS。

- [x] **Step 2: 编译 Debug Kotlin**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL。

- [x] **Step 3: 检查差异和编码**

Run: `git diff --check`

Expected: 无空白错误；所有新增或修改文件均为 UTF-8 无 BOM。
