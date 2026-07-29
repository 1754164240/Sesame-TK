# 持久调度实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 当前仓库禁止使用子代理、禁止未经授权暂存或提交。

**Goal:** 用可恢复的持久注册表和单一系统闹钟替代主轮询、每日零点和自定义唤醒的纯进程内等待，使进程死亡、设备重启、应用升级和系统时间变化后仍能恢复，同时保留进程内回退路径。

**Architecture:** clean-room 参考 Sesame-AG `e1fecd512e` 的持久调度行为，并吸收 `689ea0ac` 的去重重排、`5c41d6ba` 的前台拉起控制和 `4f058089` 的状态流转修正，不复制 AGPL 源码。模块进程独占注册表、文件存储和 `AlarmManager`；目标进程通过显式 Binder Service 提交、取消和确认调度，每次调用按 Binder UID 校验，仅允许模块自身与支付宝包。模块再通过包限定执行广播路由到目标进程；短延迟重试、重新登录和 `ModelTask` 的局部 WakeLock 不在第一批迁移范围。

**Tech Stack:** Kotlin、Android `AlarmManager` / `BroadcastReceiver`、Jackson、Coroutines、现有 `Files` / `SmartSchedulerManager`、JUnit 4、Gradle。

## Global Constraints

- 仅支持 libxposed API 102。
- 持久调度功能和“允许持久调度拉起目标应用”均使用独立开关，默认关闭。
- ACK、广播已发送或 Activity 已拉起都不代表业务完成；调度层只确认触发已路由，不改变业务终态判定。
- 注册表使用稳定 `dedupeKey`；同键更新替换旧时间，不累积重复记录或重复物理闹钟。
- 系统始终只维护一个固定 `PendingIntent` 的物理闹钟，指向注册表中最早到期时间。
- 同一触发窗口内的全部任务一次批量认领和路由。
- 认领必须持久化租约；进程在路由中死亡后，租约到期可恢复，避免永久丢失。
- 前台拉起独立总开关默认关闭；关闭时只保留待处理任务，等待目标应用自然启动后协调。
- Android 广播使用 `goAsync()` 和 IO 协程，禁止阻塞宿主或模块主线程。
- `AlarmManager` 权限缺失、系统 API 异常或文件写入失败时保留任务，并回退到进程内调度。
- 不迁移 `间隔重试`、`重新登录`、`WakeLock:<modelId>` 等短期进程内任务。
- 严格 TDD：先看到新增测试按预期失败，再写生产实现。
- Gradle 命令固定追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`。
- 文件使用 UTF-8 无 BOM；代码注释使用中文。
- 未经用户授权，不暂存、不创建 Git 提交。

---

## File Map

- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentSchedule.kt`：调度实体、类型、状态和路由结果。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentScheduleStorage.kt`：注册表文件存储接口和原子写适配。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentScheduleRegistry.kt`：去重、认领、租约、完成、重试和恢复。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentScheduleCoordinator.kt`：单闹钟协调和批量路由。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/SystemAlarmBackend.kt`：`AlarmManager` 适配。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentLaunchPolicy.kt`：默认关闭的前台拉起策略。
- Create: `app/src/main/aidl/fansirsqi/xposed/sesame/hook/keepalive/IPersistentSchedulerService.aidl`：跨进程调度接口。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentSchedulerService.kt`：模块进程 Binder Service 与 UID 校验。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/RemotePersistentScheduleGateway.kt`：目标进程后台 Binder 客户端和旧调度回退。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/ScheduledTaskRouter.kt`：模块进程到目标进程的安全路由。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/ScheduledTriggerReceiver.kt`：固定物理闹钟入口。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/ScheduleReconcileReceiver.kt`：重启、升级、时间变化恢复入口。
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/util/Files.kt`：增加共享注册表文件路径。
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/model/BaseModel.kt`：增加两个默认关闭开关。
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/ApplicationHook.kt`：注册执行广播、接入持久主调度并在初始化后协调。
- Modify: `app/src/main/AndroidManifest.xml`：声明系统广播权限和两个 Receiver。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentScheduleRegistryTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentScheduleCoordinatorTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentLaunchPolicyTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/hook/keepalive/ScheduledTaskRouterTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentSchedulerConfigTest.kt`。

## Task 1: 定义注册表实体、稳定键和状态机

**Interfaces:**

- Produces: `PersistentScheduleKind { GLOBAL_POLL, DAILY_MIDNIGHT, CUSTOM_WAKE }`。
- Produces: `PersistentScheduleState { PENDING, CLAIMED }`。
- Produces: `PersistentSchedule(dedupeKey, kind, triggerAtMillis, payloadJson, ownerUserId, state, generation, leaseUntilMillis, updatedAtMillis)`。
- Produces: `PersistentScheduleRegistry.upsert(schedule, nowMillis): PersistentSchedule`。
- Produces: `claimDue(nowMillis, windowMillis, leaseMillis): List<PersistentSchedule>`。
- Produces: `complete(dedupeKey, generation): Boolean`、`retry(dedupeKey, generation, triggerAtMillis): Boolean`、`cancel(dedupeKey): Boolean`、`nextPending(): PersistentSchedule?`。

- [x] **Step 1: 写去重与租约恢复失败测试**

```kotlin
@Test
fun sameDedupeKeyReplacesTimeAndIncrementsGeneration() {
    registry.upsert(schedule("global:poll", 1_000L), nowMillis = 100L)
    val updated = registry.upsert(schedule("global:poll", 2_000L), nowMillis = 200L)
    assertEquals(1, registry.all().size)
    assertEquals(2_000L, updated.triggerAtMillis)
    assertEquals(2L, updated.generation)
}

@Test
fun expiredClaimReturnsToPendingAfterProcessDeath() {
    registry.upsert(schedule("global:poll", 1_000L), 0L)
    registry.claimDue(1_000L, windowMillis = 500L, leaseMillis = 5_000L)
    registry.recoverExpiredClaims(6_001L)
    assertEquals(PersistentScheduleState.PENDING, registry.get("global:poll")?.state)
}
```

另覆盖：旧 generation 的 `complete` 不能删除新一代记录、取消不存在键幂等、空 `dedupeKey` 被拒绝、同窗任务一次认领且不重复认领。

- [x] **Step 2: 运行注册表测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentScheduleRegistryTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

Expected: 编译失败，调度实体和注册表尚不存在。

- [x] **Step 3: 实现线程安全的内存状态机**

所有变更在单一锁内完成并调用 `PersistentScheduleStorage.save(snapshot)`。`upsert` 将同键任务重置为 `PENDING`、清空租约并递增 generation；`claimDue` 只认领 `triggerAtMillis <= now + window` 的 `PENDING` 任务，并在返回前持久化 `CLAIMED` 和租约截止时间。

- [x] **Step 4: 实现存储边界和损坏文件收窄行为**

```kotlin
interface PersistentScheduleStorage {
    fun load(): List<PersistentSchedule>
    fun save(schedules: List<PersistentSchedule>): Boolean
}
```

生产实现写入 `Files.getPersistentScheduleFile()` 的同目录临时文件，再原子替换；读取空文件、未知 kind/state 或损坏 JSON 时返回空列表并记录错误，不删除原文件。测试只用内存存储。

- [x] **Step 5: 运行注册表测试，确认通过**

使用 Step 2 相同命令，Expected: `BUILD SUCCESSFUL`。

## Task 2: 实现单物理闹钟协调器和进程内回退

**Interfaces:**

- Produces: `ScheduleAlarmBackend.arm(triggerAtMillis: Long): Boolean`、`cancel(): Boolean`。
- Produces: `ScheduleFallback.schedule(delayMillis: Long, dedupeKey: String, callback: () -> Unit)`。
- Produces: `ScheduleTaskDispatcher.dispatch(schedule: PersistentSchedule): ScheduleDispatchResult`。
- Produces: `PersistentScheduleCoordinator.reconcile(nowMillis: Long): ReconcileResult`。
- Produces: `PersistentScheduleCoordinator.onAlarm(nowMillis: Long): DispatchBatchResult`。

- [x] **Step 1: 写单闹钟、批量和失败回退测试**

至少覆盖：

```kotlin
@Test
fun reconcileArmsOnlyEarliestPendingSchedule() {
    registry.upsert(schedule("custom:0900", 9_000L), 0L)
    registry.upsert(schedule("global:poll", 5_000L), 0L)
    coordinator.reconcile(1_000L)
    assertEquals(listOf(5_000L), alarmBackend.armedTimes)
}

@Test
fun oneAlarmDispatchesEveryTaskInDueWindow() {
    registry.upsert(schedule("a", 10_000L), 0L)
    registry.upsert(schedule("b", 10_500L), 0L)
    coordinator.onAlarm(10_000L)
    assertEquals(listOf("a", "b"), dispatcher.keys)
}
```

另覆盖：系统 arm 返回 false 时只安排一个名为 `persistent-schedule-fallback` 的进程内任务；重复 `reconcile` 不在最早时间未变化时重排；没有任务时取消物理闹钟。

- [x] **Step 2: 运行协调器测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentScheduleCoordinatorTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 实现协调算法**

固定参数：到期窗口 `1_000 ms`、认领租约 `30_000 ms`、失败重试最小延迟 `60_000 ms`。`onAlarm` 顺序必须是：恢复过期租约 -> 批量认领并持久化 -> 逐项路由 -> 按 generation 完成或重试 -> 再次只对最早待处理任务协调物理闹钟。

- [x] **Step 4: 接入进程内回退适配器**

`SmartSchedulerFallback` 复用现有：

```kotlin
SmartSchedulerManager.schedule(delayMillis, "persistent-schedule-fallback") {
    coordinator.onAlarm(System.currentTimeMillis())
}
```

回退只保证当前进程存活期间可执行，不能从注册表删除任务，也不能宣称系统持久调度成功。

- [x] **Step 5: 运行协调器和注册表测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentScheduleRegistryTest" `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentScheduleCoordinatorTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`。

## Task 3: 接入 Android AlarmManager 和系统恢复广播

**Interfaces:**

- Consumes: Task 2 `ScheduleAlarmBackend`。
- Produces: `SystemAlarmBackend`，固定 request code 和显式组件 `ScheduledTriggerReceiver`。
- Produces: `ScheduledTriggerReceiver`、`ScheduleReconcileReceiver`。

- [x] **Step 1: 写清单和配置契约失败测试**

`PersistentSchedulerConfigTest` 读取 `app/src/main/AndroidManifest.xml`，断言：

- 存在 `android.permission.RECEIVE_BOOT_COMPLETED`。
- `ScheduledTriggerReceiver` 为 `enabled=true`、`exported=false`。
- `ScheduleReconcileReceiver` 为 `directBootAware=true`、`exported=false`。
- 恢复 action 包含 `BOOT_COMPLETED`、`MY_PACKAGE_REPLACED`、`TIME_SET`、`TIMEZONE_CHANGED`、`SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`。

- [x] **Step 2: 运行契约测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentSchedulerConfigTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 实现固定 PendingIntent 的 Alarm 后端**

使用 `AlarmManager.RTC_WAKEUP` 和 `setExactAndAllowWhileIdle`。Android 12+ 先检查 `canScheduleExactAlarms()`；无权限、`PendingIntent` 创建失败或系统异常均返回 `false`。只允许一个固定 request code，不把 `dedupeKey` 变成 request code。

- [x] **Step 4: 实现异步 Receiver**

`ScheduledTriggerReceiver.onReceive` 只调用 `goAsync()`，在 `Dispatchers.IO + SupervisorJob` 中执行 `coordinator.onAlarm(now)`，使用最长 8 秒超时，`finally` 调用 `pendingResult.finish()`。`ScheduleReconcileReceiver` 对支持的系统 action 调用 `coordinator.reconcile(now)`；`LOCKED_BOOT_COMPLETED` 只记录并等待解锁后的 `BOOT_COMPLETED`，避免凭据存储不可用时误清空。

- [x] **Step 5: 更新清单并运行契约测试**

在 `AndroidManifest.xml` 增加权限和 Receiver 声明，然后使用 Step 2 命令确认通过。

## Task 4: 安全路由到目标进程，前台拉起默认关闭

**Interfaces:**

- Produces: `PersistentLaunchPolicy.shouldLaunchTarget(enabled: Boolean, schedule: PersistentSchedule): Boolean`。
- Produces: `ScheduledTaskRouter.route(context, schedule): ScheduleDispatchResult`。
- Result: `ROUTED`、`DEFERRED`、`RETRY`、`SKIPPED_ACCOUNT`、`UNSUPPORTED`。

- [x] **Step 1: 写拉起策略和路由失败测试**

必须覆盖：

- `allowPersistentForegroundLaunch=false` 时任何任务都不能调用 `startActivity`。
- owner 与当前账号不一致时返回 `SKIPPED_ACCOUNT`，不执行任务。
- 未知 kind 返回 `UNSUPPORTED` 并保留可诊断信息。
- 目标进程未运行且不允许拉起时返回 `DEFERRED`，注册表重试而非完成。
- `GLOBAL_POLL`、`DAILY_MIDNIGHT`、`CUSTOM_WAKE` 只发送包限定为 `General.PACKAGE_NAME` 的执行广播。

- [x] **Step 2: 运行策略和路由测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentLaunchPolicyTest" `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.ScheduledTaskRouterTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 在 BaseModel 增加独立默认关闭开关**

```kotlin
val persistentSchedulerEnabled = BooleanModelField(
    "persistentSchedulerEnabled",
    "启用持久调度",
    false,
)
val allowPersistentForegroundLaunch = BooleanModelField(
    "allowPersistentForegroundLaunch",
    "允许持久调度拉起目标应用",
    false,
)
```

不得复用或改变现有 `manualTriggerAutoSchedule` 的含义和默认值。

- [x] **Step 4: 实现路由策略**

模块 Receiver 进程只负责协调和向 `General.PACKAGE_NAME` 发送广播。只有 `allowPersistentForegroundLaunch=true` 时，才允许使用 `Intent.ACTION_VIEW + General.CURRENT_USING_ACTIVITY + FLAG_ACTIVITY_NEW_TASK` 拉起目标应用；拉起后仍返回 `DEFERRED`，等待目标进程实际接收和认领，不把 `startActivity` 当作完成。

- [x] **Step 5: 运行策略、路由和 BaseModel 默认值测试**

使用 Step 2 命令并追加 `--tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentSchedulerConfigTest"`，Expected: `BUILD SUCCESSFUL`。

## Task 5: 接入 ApplicationHook 的主轮询、零点和自定义唤醒

**Interfaces:**

- Stable keys: `global:poll`、`global:midnight`、`global:wakeup:<HH:mm>`。
- Action: 新增 `com.eg.android.AlipayGphone.sesame.execute`。
- Constraint: `间隔重试`、`重新登录`、`WakeLock:<modelId>` 继续使用 `SmartSchedulerManager`。

- [x] **Step 1: 写 ApplicationHook 源码契约失败测试**

测试读取源码并断言：

- `scheduleNextExecutionInternal` 在开关启用时注册 `global:poll`。
- `setWakenAtTimeAlarm` 注册 `global:midnight` 和稳定的自定义时间键。
- `BroadcastActions` 包含 `EXECUTE`，正式构建路径也注册执行广播，不再只位于 `BuildConfig.DEBUG` 块。
- `initHandler` 在配置和账号加载后调用持久协调器恢复待处理任务。

- [x] **Step 2: 运行契约测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.PersistentSchedulerConfigTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 把广播注册从 DEBUG 条件中拆出**

调试 HTTP 服务仍只在 `BuildConfig.DEBUG` 启动；核心广播接收器在主进程初始化时始终注册。Filter 新增 `EXECUTE`，接收后进入现有全局执行互斥入口，禁止直接并发调用 `execHandler()`。

- [x] **Step 4: 双路径接入三个长时任务**

当 `persistentSchedulerEnabled=false` 时保持现有 `SmartSchedulerManager.schedule` 行为；启用时注册持久任务并调用 `reconcile`。时间列表含 `-1` 时取消相关稳定键；配置删除某个自定义时间时取消对应 `global:wakeup:<HH:mm>`，防止旧闹钟残留。

- [x] **Step 5: 处理自然启动后的待路由任务**

在 `Config.load(userId)`、`UserMap.load(userId)` 和 RPC bridge 就绪后调用 `reconcile`；只路由 owner 为空或与当前 `userId` 相同的到期任务。账号不匹配的记录标记跳过或保留到对应账号，不允许跨账号执行。

- [x] **Step 6: 运行 keepalive 与现有调度回归测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.*" `
  --tests "fansirsqi.xposed.sesame.task.TaskRunnerPolicyTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`；现有短延迟任务仍走进程内调度。

## Task 6: 全链路回归和故障注入

- [x] **Step 1: 增加故障注入测试**

覆盖：注册表保存失败、精确闹钟权限缺失、AlarmManager 抛异常、路由过程中进程模拟中断、旧 generation 延迟回调、系统时间回拨、系统时间前跳、重复系统恢复广播。

- [x] **Step 2: 验证关键不变量**

每个故障测试必须证明：任务未静默删除；同键最多一条当前 generation；物理闹钟最多一个；旧回调不能完成新任务；主线程不执行文件 IO 或等待任务完成。

- [x] **Step 3: 运行持久调度全部测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.keepalive.*" `
  --tests "fansirsqi.xposed.sesame.task.TaskRunnerPolicyTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 4: 构建 Debug APK**

```powershell
.\gradlew.bat assembleDebug `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [ ] **Step 5: 真机验证矩阵**

依次验证：默认关闭无行为变化；启用持久调度但关闭前台拉起；杀死目标进程后自然启动恢复；设备重启；应用升级；手动改时间和时区；撤销精确闹钟权限后进程内回退；同一时窗多任务只触发一次物理闹钟。前台拉起只在单独开启后验证，任何验证码、2FA、风控或安全验证立即停止。

## Acceptance Gate

- `global:poll`、`global:midnight`、`global:wakeup:<HH:mm>` 均使用稳定键且不会重复重排。
- 注册表在进程死亡、设备重启、应用升级、时间和时区变化后可恢复。
- 系统物理闹钟始终只有一个；同窗任务批量路由。
- 保存失败、权限缺失和系统 API 异常不会删除任务，并有进程内回退。
- 前台拉起总开关默认关闭，`manualTriggerAutoSchedule` 语义不变。
- 广播和文件操作不阻塞宿主主线程。
- 业务任务是否完成仍由各模块动作后的 RPC 回查决定。
- 本计划不包含全模块子任务持久化、Git 暂存或提交。
