# 能量雨安全验证重试实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 安全验证只结束当前能量雨调用，后续调用不再因当天标记而跳过。

**Architecture:** 保留现有安全验证响应识别与各调用点的 `break`/`return` 控制流。移除 `EnergyRainCoroutine` 对 `Status` 当天安全验证标记的写入和读取，使每次调用都重新查询服务端状态。

**Tech Stack:** Kotlin 2.2、Android Gradle Plugin、JUnit 4、org.json

## Global Constraints

- 安全验证响应仍立即结束当前能量雨流程。
- 后续定时或手动调用可以重新尝试。
- 保留现有 30 秒执行冷却。
- 文件使用 UTF-8 无 BOM，代码注释使用中文。

---

### Task 1: 移除能量雨当天验证暂停

**Files:**
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/EnergyRainCoroutineTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/EnergyRainCoroutine.kt`

**Interfaces:**
- Consumes: `EnergyRainCoroutine.isVerificationRequiredResult(JSONObject): Boolean`
- Produces: 安全验证只终止当前控制流且不持久化暂停状态的 `EnergyRainCoroutine`

- [x] **Step 1: 写入失败测试**

将验证处理回归测试改为期望源码不再引用能量雨当天暂停标记和“跳过执行”日志，同时继续包含本次流程结束的验证处理函数。文件内用于游戏任务防重复的 `Status` 标记保持不变。

```kotlin
@Test
fun `能量雨安全验证只结束当前流程且后续可重试`() {
    val sourceText = File("src/main/java/fansirsqi/xposed/sesame/task/antForest/EnergyRainCoroutine.kt").readText()

    assertTrue(sourceText.contains("pauseForVerification"))
    assertTrue(sourceText.contains("本次流程结束，后续可再次执行"))
    assertFalse(sourceText.contains("ENERGY_RAIN_VERIFICATION_FLAG"))
    assertFalse(sourceText.contains("今日能量雨已触发安全验证，跳过执行"))
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antForest.EnergyRainCoroutineTest"`

Expected: FAIL，原因是当前源码仍包含 `ENERGY_RAIN_VERIFICATION_FLAG` 或 `Status` 标记调用。

- [x] **Step 3: 写入最小实现**

在 `EnergyRainCoroutine.kt` 中删除 `ENERGY_RAIN_VERIFICATION_FLAG`、入口及循环内的安全验证标记检查。保留游戏任务防重复所需的 `Status` 导入和标记调用。把 `pauseForVerification` 改为只记录本次结束：

```kotlin
private fun pauseForVerification(stage: String, result: JSONObject) {
    Log.record(TAG, "能量雨${stage}触发安全验证，本次流程结束，后续可再次执行: $result")
}
```

`startEnergyRain()` 返回当前主循环是否可以继续；开始或结算触发验证时返回 `false`，外层立即 `break`。赠送分支增加本次循环内布尔值，识别验证后跳出好友循环，并立即结束能量雨主循环，不再通过当天标记传递控制状态。

- [x] **Step 4: 运行定向测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antForest.EnergyRainCoroutineTest"`

Expected: PASS。

- [x] **Step 5: 运行编译与差异检查**

Run: `./gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL。

Run: `git diff --check`

Expected: 无错误输出。

- [x] **Step 6: 提交实现**

```powershell
git add -- app/src/main/java/fansirsqi/xposed/sesame/task/antForest/EnergyRainCoroutine.kt app/src/test/java/fansirsqi/xposed/sesame/task/antForest/EnergyRainCoroutineTest.kt docs/superpowers/plans/2026-08-01-energy-rain-verification-retry.md
git commit -m "移除能量雨安全验证当天跳过"
```
