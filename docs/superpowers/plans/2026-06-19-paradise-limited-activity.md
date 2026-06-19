# 乐园限时活动 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在蚂蚁庄园现有“开宝箱”流程中增加乐园限时活动签到和累计开 10 个宝箱奖励领取。

**Architecture:** 将限时活动 JSON 解析和领取决策拆到 `AntFarmParadiseLimitedActivity.kt`，保持可单测；RPC 请求仍由 `AntFarmRpcCall.java` 封装；`AntFarm.kt` 只负责编排查询、开宝箱、领奖。

**Tech Stack:** Kotlin、Java、Android Gradle Plugin、本地 JUnit4、org.json。

---

### Task 1: 限时活动领取决策

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmParadiseLimitedActivity.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmParadiseLimitedActivityTest.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Write the failing test**

测试活动期内只返回 `FINISHED` 的 `2026cc_lyqd` 和 `2026cc_GAME_ljkbx`，并跳过 `TODO`、`RECEIVED`、非本活动场景任务。

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antFarm.AntFarmParadiseLimitedActivityTest"`

Expected: FAIL，原因是生产类尚未实现。

- [ ] **Step 3: Write minimal implementation**

实现活动截止时间 `2026-12-31 23:00 Asia/Shanghai`、任务类型常量、`claimableTasks` 解析方法。

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antFarm.AntFarmParadiseLimitedActivityTest"`

Expected: PASS。

### Task 2: RPC 封装和流程接入

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRpcCall.java`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarm.kt`

- [ ] **Step 1: Add RPC methods**

新增 `queryParadiseLimitedActivity()` 调 `com.alipay.charitygamecenter.queryOptionalPlay`，新增 `receiveParadiseLimitedActivityAward(taskType, awardCount)` 调 `com.alipay.antieptask.receiveTaskAwardantfarm`。

- [ ] **Step 2: Integrate with treasure box flow**

在 `drawGameCenterAward()` 开始时查询并领取签到；完成现有开宝箱逻辑后再次查询并领取累计开宝箱奖励。

- [ ] **Step 3: Build verification**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac`

Expected: BUILD SUCCESSFUL。
