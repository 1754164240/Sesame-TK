# Persistent Binding Circuit Breaker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 持久调度服务绑定失败后立即熔断并回退进程内调度，避免支付宝卡在启动状态。

**Architecture:** 使用进程级纯 Kotlin 熔断器协调所有远程网关实例。控制器识别持久服务不可用异常并执行既有 legacy 回调，Android 绑定工作切换到 I/O 调度器。

**Tech Stack:** Kotlin、Android Service/Binder、Kotlin Coroutines、JUnit 4

## Global Constraints

- 不修改用户的持久调度和前台拉起配置。
- 不打印 Android 用户 ID。
- 文件使用 UTF-8 无 BOM，代码注释使用中文。
- 提交信息遵循 Conventional Commits，描述使用中文。

---

### Task 1: 绑定熔断状态

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentBindingCircuitBreaker.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentBindingCircuitBreakerTest.kt`

**Interfaces:**
- Produces: `tryAcquireBinding()`、`onConnected()`、`onBindingFailed()`、`isOpen()`

- [ ] **Step 1: Write the failing test**

验证同一时间只允许一个绑定调用，失败后不再放行，成功后允许后续重连。

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentBindingCircuitBreakerTest" --daemon --configuration-cache --console=plain`

Expected: FAIL，因为熔断器尚不存在。

- [ ] **Step 3: Write minimal implementation**

使用原子状态 `READY`、`CONNECTING`、`OPEN` 实现进程内绑定门禁。

- [ ] **Step 4: Run test to verify it passes**

运行 Task 1 Step 2 的命令，预期 PASS。

### Task 2: 网关熔断与控制器回退

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/RemotePersistentScheduleGateway.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentSchedulerRuntime.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentSchedulerController.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentSchedulerControllerTest.kt`

**Interfaces:**
- Consumes: `PersistentBindingCircuitBreaker`
- Produces: `PersistentScheduleUnavailableException`

- [ ] **Step 1: Write the failing test**

构造始终抛出 `PersistentScheduleUnavailableException` 的网关，验证轮询、唤醒和验证探针立即执行 legacy 回调。

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentSchedulerControllerTest" --daemon --configuration-cache --console=plain`

Expected: FAIL，因为控制器尚未处理持久服务不可用。

- [ ] **Step 3: Write minimal implementation**

远程网关在绑定前获取门禁，终态失败后打开熔断；控制器捕获专用异常并执行 legacy 回调。

- [ ] **Step 4: Run test to verify it passes**

运行 Task 2 Step 2 的命令，预期 PASS。

### Task 3: 调度线程与发布验证

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/ApplicationHook.kt`

**Interfaces:**
- Consumes: `Dispatchers.IO`

- [ ] **Step 1: Move persistent scheduling calls to I/O**

为持久轮询、验证探针、启动 reconcile 和唤醒注册指定 `Dispatchers.IO`。

- [ ] **Step 2: Run verification**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --daemon --configuration-cache --console=plain`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Validate and publish**

执行 `git diff --check`、UTF-8 无 BOM 检查，提交 `fix: 熔断失败的持久调度绑定`，使用完整 `yang` refspec 推送并核对 SHA。
