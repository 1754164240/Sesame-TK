# 运行时基础修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 ReferToken 多结构解析、福气鱼池每日单次映射、relationLocal 契约、日志跨天、通知隔离和并发配置化。

**Architecture:** 保留当前 `RequestManager`、`RpcBridge`、`BaseModel`、`TaskRunner` 和业务模块边界。新增的日期、通知和并发决策使用无 Android 依赖的小型策略类承载，以便先用 JVM 单元测试定义行为，再接入 Android 实现。

**Tech Stack:** Kotlin 2.3、Java 17、JUnit 4、org.json、Kotlin 协程、Logback Android。

## Global Constraints

- 仅支持 libxposed API 102。
- 不复制或 cherry-pick Sesame-AG 源码。
- 不引入通用 `TaskFlow`。
- 新配置保持现有默认行为。
- 所有完成状态必须由服务端回查确认。
- 文件使用 UTF-8 无 BOM，代码注释使用中文。
- 不创建 Git 提交。

---

### Task 1: ReferToken 多结构解析

**Files:**
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/hook/ReferTokenParserTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/ReferTokenParser.kt`

**Interfaces:**
- Consumes: `JSONObject params`
- Produces: `ReferTokenParser.parse(params: JSONObject): String?`

- [ ] **Step 1: 写入字符串数组、字符串对象和非法字符串失败测试**

```kotlin
@Test
fun `兼容字符串形式requestData`() {
    val array = JSONObject().put(
        "requestData",
        """[{"positionRequest":{"referInfo":{"referToken":"array-token"}}}]"""
    )
    val objectValue = JSONObject().put(
        "requestData",
        """{"positionRequest":{"referInfo":{"referToken":"object-token"}}}"""
    )

    assertEquals("array-token", ReferTokenParser.parse(array))
    assertEquals("object-token", ReferTokenParser.parse(objectValue))
    assertNull(ReferTokenParser.parse(JSONObject().put("requestData", "not-json")))
}
```

- [ ] **Step 2: 运行测试并确认因字符串结构未解析而失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.hook.ReferTokenParserTest" --no-daemon --console=plain`

Expected: `兼容字符串形式requestData` 失败，实际值为 `null`。

- [ ] **Step 3: 实现结构化 requestData 解包**

实现私有 `extractBusinessParams(value: Any?): JSONObject?`，分别处理 `JSONArray`、`JSONObject` 和以 `[` 或 `{` 开头的字符串；任何解析异常返回 `null`。

- [ ] **Step 4: 运行 ReferTokenParserTest 并确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.hook.ReferTokenParserTest" --no-daemon --console=plain`

Expected: PASS。

### Task 2: 福气鱼池每日单次运行映射

**Files:**
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFishPond/AntFishPondConfigTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/model/CustomSettings.kt`

**Interfaces:**
- Consumes: 模块显示名或模块 ID。
- Produces: `CustomSettings.getModuleId("福气鱼池") == "antFishPond"`。

- [ ] **Step 1: 写入模块映射和选项存在性测试**

```kotlin
@Test
fun `鱼池可加入每日只运行一次筛选`() {
    assertEquals("antFishPond", CustomSettings.getModuleId("福气鱼池"))
    val options = CustomSettings.onlyOnceDailyList.expandValue
    assertTrue(options.any { it.id == "antFishPond" })
}
```

- [ ] **Step 2: 运行测试并确认因缺少映射而失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.antFishPond.AntFishPondConfigTest" --no-daemon --console=plain`

Expected: 模块 ID 实际为 `null`。

- [ ] **Step 3: 在模块列表和模块 ID 解析中加入 antFishPond**

增加 `SimpleEntity("antFishPond", "福气鱼池")`，并在海洋判断之前识别“福气鱼池”或 `antFishPond`，避免被“鱼池”之外的名称误匹配。

- [ ] **Step 4: 运行配置测试并确认通过**

Run: 同 Step 2。

Expected: PASS。

### Task 3: relationLocal 请求契约

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/entity/RpcEntityContractTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/rpc/bridge/RpcBridge.java`（仅在测试暴露参数错位时修改）

**Interfaces:**
- Produces: `RpcEntity.rpcFullRequestData` 中 `relationLocal` 等于构造参数，`appName` 不受影响。

- [ ] **Step 1: 写入 relationLocal 序列化和 Bridge 默认方法契约测试**

构造 `RpcEntity("method", "[]", "[{\"pathList\":[\"a\"]}]")`，断言完整请求中的 `relationLocal` 值正确且 `appName` 为 JSON null；同时通过一个记录入参的测试 Bridge 调用三参数 `requestString`。

- [ ] **Step 2: 运行测试并确认当前行为**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.entity.RpcEntityContractTest" --no-daemon --console=plain`

Expected: 如果当前契约正确则测试直接通过；此任务属于防回归契约，不强制制造生产缺陷。

- [ ] **Step 3: 若发现参数错位，只修复 RpcBridge 构造参数**

三参数重载必须调用 `new RpcEntity(method, data, relation)`；五参数重载必须使用命名明确的完整构造调用，不能把 `appName` 写入 `requestRelation`。

- [ ] **Step 4: 运行 RequestManager 和 RpcEntity 契约测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.hook.RequestManagerTest" --tests "fansirsqi.xposed.sesame.entity.RpcEntityContractTest" --no-daemon --console=plain`

Expected: PASS。

### Task 4: 主任务和森林并发配置

**Files:**
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/TaskRunnerPolicyTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/ConcurrencyPolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/model/BaseModel.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/TaskRunner.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`

**Interfaces:**
- Produces: `ConcurrencyPolicy.task(value: Int): Int`，范围 1..8。
- Produces: `ConcurrencyPolicy.forest(value: Int): Int`，范围 1..100。
- Produces: `BaseModel.taskConcurrency`，默认 3。
- Produces: 森林字段 `friendConcurrency`，默认 60。

- [ ] **Step 1: 写入默认值、上下限和执行快照测试**

```kotlin
@Test
fun `并发配置使用安全边界`() {
    assertEquals(1, ConcurrencyPolicy.task(0))
    assertEquals(3, ConcurrencyPolicy.task(3))
    assertEquals(8, ConcurrencyPolicy.task(99))
    assertEquals(1, ConcurrencyPolicy.forest(0))
    assertEquals(60, ConcurrencyPolicy.forest(60))
    assertEquals(100, ConcurrencyPolicy.forest(999))
}
```

- [ ] **Step 2: 运行测试并确认 ConcurrencyPolicy 不存在**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.TaskRunnerPolicyTest" --no-daemon --console=plain`

Expected: 编译失败，缺少 `ConcurrencyPolicy`。

- [ ] **Step 3: 实现纯策略和配置字段**

使用 `coerceIn` 实现两个边界方法。将 `taskConcurrency` 加入基础配置字段；将 `friendConcurrency` 加入森林配置字段，默认值保持现状。

- [ ] **Step 4: 在每轮启动时读取一次并发快照**

`TaskRunner` 在 `run()` 开头读取 `ConcurrencyPolicy.task(BaseModel.taskConcurrency.value)`，整个本轮复用该值。森林每次批量好友处理开始时创建对应容量的 `Semaphore`，不保留固定 60 的类级信号量。

- [ ] **Step 5: 运行 TaskRunnerPolicyTest 和森林测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.task.TaskRunnerPolicyTest" --tests "fansirsqi.xposed.sesame.task.antForest.*" --no-daemon --console=plain`

Expected: PASS。

### Task 5: 日志跨天检测

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/util/LogDayTrackerTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/util/LogDayTracker.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/util/Logback.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/util/Log.kt`

**Interfaces:**
- Produces: `LogDayTracker.shouldRefresh(nowMillis: Long): Boolean`。
- Produces: `Logback.refreshIfCrossDay()`。

- [ ] **Step 1: 写入同日不刷新、跨日只刷新一次测试**

使用固定 GMT+8 时间戳，断言首次初始化为 `false`，同日为 `false`，跨日第一次为 `true`，同一新日期后续为 `false`。

- [ ] **Step 2: 运行测试并确认类型不存在**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.util.LogDayTrackerTest" --no-daemon --console=plain`

Expected: 编译失败，缺少 `LogDayTracker`。

- [ ] **Step 3: 实现线程安全日期跟踪器**

使用 `AtomicReference<String>` 保存 GMT+8 的 `yyyy-MM-dd`。`shouldRefresh` 通过 CAS 保证同一进程同一天最多返回一次 `true`。

- [ ] **Step 4: 接入 Logback 原子重建文件 Appender**

缓存应用 `Context` 和日志目录。跨天时在同步块内停止并移除所有 `FILE-*` Appender，再重新添加；Logcat Appender不重置。`Log` 的各文件频道写入前调用 `refreshIfCrossDay()`。

- [ ] **Step 5: 运行日志测试和完整单元测试**

Run: `.\gradlew.bat testDebugUnitTest --no-daemon --console=plain`

Expected: PASS。

### Task 6: 通知状态隔离

**Files:**
- Create: `app/src/test/java/fansirsqi/xposed/sesame/util/NotificationStateStoreTest.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/util/NotificationStateStore.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/util/Notify.kt`

**Interfaces:**
- Produces: `NotificationStateStore.updatePersistent(...)`。
- Produces: `NotificationStateStore.errorSnapshot(...)`，不修改常驻快照。

- [ ] **Step 1: 写入异常通知不覆盖常驻状态测试**

先写入常驻标题、正文和下一次执行时间，再生成异常快照，断言常驻快照完全不变且通知 ID 不同。

- [ ] **Step 2: 运行测试并确认类型不存在**

Run: `.\gradlew.bat testDebugUnitTest --tests "fansirsqi.xposed.sesame.util.NotificationStateStoreTest" --no-daemon --console=plain`

Expected: 编译失败，缺少 `NotificationStateStore`。

- [ ] **Step 3: 实现纯状态存储**

使用不可变 `NotificationSnapshot` 数据类；常驻更新返回新快照，错误快照不写回常驻引用。

- [ ] **Step 4: 重构 Notify 的 Builder 和 Manager 生命周期**

常驻 Builder 只由 `start`、`update*` 和 `stop` 管理。`sendNewNotification` 使用局部 Builder 和局部 Manager，不再覆盖共享字段。异常频道与常驻频道使用不同 channel ID。

- [ ] **Step 5: 运行通知测试和完整单元测试**

Run: `.\gradlew.bat testDebugUnitTest --no-daemon --console=plain`

Expected: PASS。

### Task 7: 基础阶段构建验收

**Files:**
- Modify: `README.md`（记录用户可见配置变化）

**Interfaces:**
- Produces: 可安装 Debug APK 和基础阶段验证记录。

- [ ] **Step 1: 更新 README 最近功能条目**

记录 ReferToken 多结构兼容、鱼池每日单次、日志跨天、通知隔离和并发配置，不宣称未执行的真机验证。

- [ ] **Step 2: 运行完整单元测试**

Run: `.\gradlew.bat --stop`

Run: `.\gradlew.bat testDebugUnitTest --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL` 且测试数不为 0。

- [ ] **Step 3: 构建 Debug APK**

Run: `.\gradlew.bat assembleDebug --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL`，`app/build/outputs/apk/debug` 下生成 APK。

- [ ] **Step 4: 检查工作区范围**

Run: `git status --short`

Run: `git diff --check`

Expected: 只有本计划涉及的文件发生变化，无空白错误，无 Git 提交。
