# 倍率卡能量收取后复查实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 森林完成好友能量收取后再次检查倍率卡待领取能量，并在达到设置阈值时自动领取。

**Architecture:** 在现有 `AntForestResponsePolicy` 中增加纯函数承载阈值判断，由 `AntForest` 的现有倍率卡处理逻辑调用。好友收取协程完成后复用 `updateSelfHomePage()` 获取服务端最新余额，避免自行累计推算。

**Tech Stack:** Kotlin、JUnit 4、Android Gradle Plugin

## Global Constraints

- 文件使用 UTF-8 无 BOM。
- 新增代码注释使用中文。
- 不创建 Git 提交。
- 不改变现有倍率卡 RPC 方法和请求参数。

---

### Task 1: 倍率卡领取策略

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForestResponsePolicy.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/AntForestResponsePolicyTest.kt`

**Interfaces:**
- Consumes: `leftEnergy: Double`、`threshold: Int`、`overLimitToday: Boolean`
- Produces: `AntForestResponsePolicy.shouldCollectRobExpandEnergy(Double, Int, Boolean): Boolean`

- [x] **Step 1: 写入阈值边界的失败测试**

```kotlin
assertFalse(AntForestResponsePolicy.shouldCollectRobExpandEnergy(199.0, 200, false))
assertTrue(AntForestResponsePolicy.shouldCollectRobExpandEnergy(200.0, 200, false))
assertTrue(AntForestResponsePolicy.shouldCollectRobExpandEnergy(201.0, 200, false))
assertTrue(AntForestResponsePolicy.shouldCollectRobExpandEnergy(1.0, 200, true))
assertFalse(AntForestResponsePolicy.shouldCollectRobExpandEnergy(0.0, 200, true))
```

- [x] **Step 2: 运行测试并确认因方法缺失而失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antForest.AntForestResponsePolicyTest" --no-daemon`

Expected: FAIL，提示 `shouldCollectRobExpandEnergy` 未定义。

- [x] **Step 3: 写入最小策略实现**

```kotlin
fun shouldCollectRobExpandEnergy(leftEnergy: Double, threshold: Int, overLimitToday: Boolean): Boolean {
    return leftEnergy >= threshold || (overLimitToday && leftEnergy >= 1.0)
}
```

- [x] **Step 4: 运行测试并确认通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antForest.AntForestResponsePolicyTest" --no-daemon`

Expected: PASS。

### Task 2: 好友收取完成后复查

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`

**Interfaces:**
- Consumes: Task 1 的 `shouldCollectRobExpandEnergy`。
- Produces: 好友收取完成后的一次主页刷新与倍率卡自动领取。

- [x] **Step 1: 用策略函数替换内联判断**

将 `leftEnergy > robExpandCardLimt!!.value` 替换为 `AntForestResponsePolicy.shouldCollectRobExpandEnergy(...)`，并传入解析后的 `overLimitToday`。

- [x] **Step 2: 在好友收取协程后补一次主页刷新**

```kotlin
collectFriendEnergyCoroutine()
updateSelfHomePage()
```

- [x] **Step 3: 运行森林策略测试和 Kotlin 编译检查**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antForest.AntForestResponsePolicyTest" :app:compileDebugKotlin --no-daemon`

Expected: BUILD SUCCESSFUL。
