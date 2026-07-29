# 好友中心基础实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 当前仓库禁止使用子代理、禁止未经授权暂存或提交。

**Goal:** 在不破坏现有好友配置的前提下，建立可持久化、可动态解析“全部好友”、支持分组、黑名单、关系和玩法能力过滤的好友中心基础，并提供旧 `Set<String>` / `Map<String, Int>` 的显式迁移适配器。

**Architecture:** 采用 clean-room 实现，只参考 Sesame-AG `98d4aba14e`、`580b15d62f` 暴露的数据语义和行为，不复制 AGPL 源码。实体、存储、`UserMap` 只读同步、选择解析和旧配置适配相互分离；第一阶段不改现有业务模块字段，也不建设完整好友中心 UI，后续按模块逐个迁移。

**Tech Stack:** Kotlin、Jackson、现有 `DataStore`、现有 `UserMap`、JUnit 4、Gradle。

## Global Constraints

- 仅支持 libxposed API 102。
- 新能力默认关闭；未迁移模块继续读取原 `SelectModelField` / `SelectAndCountModelField`。
- “全部好友”必须在每次执行时基于当前仓库快照解析，禁止把当时的好友 ID 固化进模块配置。
- 未知 `selectionScope` 必须安全回退 `EXPLICIT`，禁止意外扩大执行范围。
- 同步 `UserMap` 只能读取快照，不能反向修改、清空或接管现有好友文件。
- 全局黑名单、排除用户和排除分组的优先级高于任何包含规则。
- 第一阶段不批量迁移森林、庄园、合种、运动、新村等业务模块。
- 严格 TDD：先运行新增测试看到预期失败，再写最小生产代码。
- Gradle 命令固定追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`。
- 文件使用 UTF-8 无 BOM；代码注释使用中文。
- 未经用户授权，不暂存、不创建 Git 提交。

---

## File Map

- Create: `app/src/main/java/fansirsqi/xposed/sesame/entity/friend/FriendCenterEntities.kt`：好友关系、能力、分组、仓库和选择规则实体。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/util/friend/FriendCenterStorage.kt`：持久化接口及 `DataStore` 适配器。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/util/friend/FriendRepository.kt`：按当前账号加载、同步、更新好友中心数据。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/util/friend/FriendSelectionResolver.kt`：纯函数解析 ID 和次数映射。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/util/friend/FriendCapabilityRecorder.kt`：记录玩法能力状态。
- Create: `app/src/main/java/fansirsqi/xposed/sesame/util/friend/LegacyFriendSelectionAdapter.kt`：旧配置到新规则的无损显式适配。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/entity/friend/FriendCenterEntitiesTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/util/friend/FriendRepositoryTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/util/friend/FriendSelectionResolverTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/util/friend/FriendCapabilityRecorderTest.kt`。
- Create: `app/src/test/java/fansirsqi/xposed/sesame/util/friend/LegacyFriendSelectionAdapterTest.kt`。

## Task 1: 定义安全的好友中心实体

**Interfaces:**

- Produces: `FriendRelation`、`FriendRelationFilter`、`FriendSelectionScope.fromJson(String?)`。
- Produces: `FriendProfile`、`FriendGroup`、`FriendCenterConfig`、`FriendSelectionSpec`、`FriendSelectionCountSpec`。
- Constraint: 所有持久化实体使用默认值并忽略未知字段，便于升级和降级读取。

- [x] **Step 1: 写未知范围和默认值的失败测试**

```kotlin
@Test
fun unknownScopeFallsBackToExplicit() {
    assertEquals(FriendSelectionScope.EXPLICIT, FriendSelectionScope.fromJson("FUTURE_SCOPE"))
    assertEquals(FriendSelectionScope.EXPLICIT, FriendSelectionScope.fromJson(null))
}

@Test
fun selectionDefaultsDoNotSelectAnyone() {
    val spec = FriendSelectionSpec()
    assertEquals(FriendSelectionScope.EXPLICIT, spec.selectionScope)
    assertTrue(spec.includeUserIds.isEmpty())
    assertEquals(FriendRelationFilter.MUTUAL_ONLY, spec.relationFilter)
}
```

- [x] **Step 2: 运行测试，确认因实体不存在而失败**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.entity.friend.FriendCenterEntitiesTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

Expected: 编译失败，缺少 `FriendSelectionScope`、`FriendSelectionSpec` 等类型。

- [x] **Step 3: 实现最小实体集合**

固定枚举值：

```kotlin
enum class FriendRelation { SELF, MUTUAL, ONE_WAY, REMOVED, UNKNOWN }
enum class FriendRelationFilter { MUTUAL_ONLY, ALL_KNOWN, INCLUDE_SELF }
enum class FriendSelectionScope { EXPLICIT, ALL_FRIENDS }
enum class FriendCapabilityState { UNKNOWN, OPEN, NOT_OPEN, UNAVAILABLE }
```

固定选择结构：

```kotlin
data class FriendSelectionSpec(
    var selectionScope: FriendSelectionScope = FriendSelectionScope.EXPLICIT,
    var includeUserIds: LinkedHashSet<String> = linkedSetOf(),
    var includeGroupIds: LinkedHashSet<String> = linkedSetOf(),
    var excludeUserIds: LinkedHashSet<String> = linkedSetOf(),
    var excludeGroupIds: LinkedHashSet<String> = linkedSetOf(),
    var relationFilter: FriendRelationFilter = FriendRelationFilter.MUTUAL_ONLY,
    var capabilityFilter: FriendCapabilityFilter? = null,
)
```

`FriendSelectionScope.fromJson` 对大小写不敏感，空值、空字符串和未知值均返回 `EXPLICIT`。

- [x] **Step 4: 运行实体测试，确认通过**

使用 Step 2 相同命令，Expected: `BUILD SUCCESSFUL`。

## Task 2: 建立按账号隔离的仓库与 UserMap 只读同步

**Interfaces:**

- Consumes: Task 1 实体。
- Produces: `FriendCenterStorage.load(ownerUserId: String): FriendCenterConfig?`。
- Produces: `FriendCenterStorage.save(ownerUserId: String, config: FriendCenterConfig): Boolean`。
- Produces: `FriendRepository.loadAndSync(ownerUserId: String, users: Map<String, UserEntity>): FriendCenterConfig`。
- Produces: `FriendRepository.current(ownerUserId: String): FriendCenterConfig`。
- Produces: `FriendRepository.update(ownerUserId: String, transform: (FriendCenterConfig) -> Unit): FriendCenterConfig`。

- [x] **Step 1: 写同步保留用户规则的失败测试**

测试至少覆盖：

```kotlin
@Test
fun syncPreservesRulesAndMarksMissingProfileRemoved() {
    val stored = configOf(
        profile("100", globalBlocked = true, capability = "forest"),
        profile("200")
    )
    val repository = repositoryWith(stored)

    val synced = repository.loadAndSync("owner", mapOf("100" to user("100", 1), "300" to user("300", 0)))

    assertTrue(synced.profiles.getValue("100").globalBlocked)
    assertEquals(FriendRelation.MUTUAL, synced.profiles.getValue("100").relation)
    assertEquals(FriendRelation.REMOVED, synced.profiles.getValue("200").relation)
    assertEquals(FriendRelation.ONE_WAY, synced.profiles.getValue("300").relation)
}
```

另写账号隔离测试，证明 `friendCenter:ownerA` 与 `friendCenter:ownerB` 不共享数据。

- [x] **Step 2: 运行仓库测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.util.friend.FriendRepositoryTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

Expected: 编译失败，仓库与存储接口尚不存在。

- [x] **Step 3: 实现内存可测试的存储边界**

`DataStoreFriendCenterStorage` 使用键 `friendCenter:<ownerUserId>`；账号为空时拒绝读取和写入。测试使用 `InMemoryFriendCenterStorage`，不初始化 Android 或真实文件。

- [x] **Step 4: 实现合并同步规则**

同步规则必须固定为：

1. 从存储读取旧 `FriendCenterConfig`，不存在时创建空配置。
2. 对当前 `UserMap` 快照中的每个非空 `userId` 更新显示名、`friendStatus` 和关系。
3. 当前账号为 `SELF`；`friendStatus == 1` 为 `MUTUAL`；其他非空好友为 `ONE_WAY`。
4. 保留既有 `globalBlocked`、`globalPinned`、分组和 `capabilities`。
5. 已不在快照中的旧档案不删除，只设置 `removed=true`、`relation=REMOVED`。
6. 同步完成后一次性保存，保存失败返回内存结果但记录失败，不修改 `UserMap`。

- [x] **Step 5: 增加生产入口但不接管业务模块**

在 `FriendRepository` companion 中提供：

```kotlin
fun syncCurrentUserMap(ownerUserId: String = UserMap.currentUid.orEmpty()): FriendCenterConfig
```

该方法只把 `UserMap.getUserMap().toMap()` 作为输入传给仓库，不在 `UserMap.add/load/save` 中插入双向耦合。

- [x] **Step 6: 运行仓库测试，确认通过**

使用 Step 2 相同命令，Expected: `BUILD SUCCESSFUL`。

## Task 3: 动态解析全部好友、分组、关系和黑名单

**Interfaces:**

- Consumes: `FriendCenterConfig`、`FriendSelectionSpec`。
- Produces: `FriendSelectionResolver.resolveIds(spec: FriendSelectionSpec?, config: FriendCenterConfig): Set<String>`。
- Produces: `FriendSelectionResolver.contains(spec: FriendSelectionSpec?, userId: String?, config: FriendCenterConfig): Boolean`。

- [x] **Step 1: 写动态全部好友和优先级失败测试**

```kotlin
@Test
fun allFriendsIsResolvedFromEveryCurrentSnapshot() {
    val spec = FriendSelectionSpec(selectionScope = FriendSelectionScope.ALL_FRIENDS)
    assertEquals(setOf("100"), resolver.resolveIds(spec, configOf(profile("100"))))
    assertEquals(setOf("100", "200"), resolver.resolveIds(spec, configOf(profile("100"), profile("200"))))
    assertTrue(spec.includeUserIds.isEmpty())
}

@Test
fun exclusionsAndGlobalBlockOverrideAllIncludes() {
    val spec = selection(all = true, includeGroup = "g1", excludeUser = "200")
    val config = configWithGroup("g1", "100", "200", blocked = setOf("100"))
    assertTrue(resolver.resolveIds(spec, config).isEmpty())
}
```

另覆盖 `MUTUAL_ONLY`、`ALL_KNOWN`、`INCLUDE_SELF`、`REMOVED` 和空白 ID。

- [x] **Step 2: 运行解析器测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.util.friend.FriendSelectionResolverTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 实现确定性的解析顺序**

固定顺序为：选择范围/显式用户/包含分组并集 -> 排除用户/排除分组 -> 关系过滤 -> 能力过滤 -> 全局黑名单。结果使用 `LinkedHashSet`，按 `config.profiles` 的稳定顺序输出；未知档案、空 ID、已移除档案默认不返回。

- [x] **Step 4: 增加生产便捷入口**

```kotlin
fun resolveCurrentIds(
    spec: FriendSelectionSpec?,
    ownerUserId: String = UserMap.currentUid.orEmpty(),
): Set<String>
```

每次调用都先 `FriendRepository.syncCurrentUserMap(ownerUserId)`，禁止缓存 `ALL_FRIENDS` 的解析结果跨任务轮次使用。

- [x] **Step 5: 运行解析器和仓库测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.util.friend.FriendRepositoryTest" `
  --tests "fansirsqi.xposed.sesame.util.friend.FriendSelectionResolverTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`。

## Task 4: 玩法能力记录和次数解析

**Interfaces:**

- Produces: `FriendCapabilityRecorder.record(ownerUserId, friendUserId, moduleKey, state, source, reason, observedAt): Boolean`。
- Produces: `FriendSelectionResolver.resolveCountMap(spec: FriendSelectionCountSpec?, config: FriendCenterConfig): Map<String, Int>`。

- [x] **Step 1: 写能力过滤和次数优先级失败测试**

覆盖以下断言：

- `requiredStates={OPEN}` 时只返回模块能力为 `OPEN` 的好友。
- `includeUnknown=true` 时没有观测记录的好友可通过；`false` 时不可通过。
- 次数优先级严格为“用户覆盖 > 第一个命中的包含分组覆盖 > 默认次数”。
- 最终次数 `<= 0` 的好友从结果移除。
- 空 `moduleKey`、空好友 ID 和不存在档案的能力记录返回 `false`，不创建脏档案。

- [x] **Step 2: 运行能力与解析器测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.util.friend.FriendCapabilityRecorderTest" `
  --tests "fansirsqi.xposed.sesame.util.friend.FriendSelectionResolverTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 实现能力记录器和过滤器**

能力记录只更新目标档案的 `capabilities[moduleKey]`，保留其他模块状态；`observedAt` 默认使用当前时间，但测试必须注入固定值。多个模块键采用“全部满足”语义。

- [x] **Step 4: 实现次数解析**

先复用 `resolveIds` 得到有效好友，再按固定优先级计算次数。不得因为分组覆盖而绕过全局黑名单、关系或能力过滤。

- [x] **Step 5: 运行定向测试，确认通过**

使用 Step 2 相同命令，Expected: `BUILD SUCCESSFUL`。

## Task 5: 旧选择字段的无损迁移适配

**Interfaces:**

- Produces: `LegacyFriendSelectionAdapter.fromIds(ids: Set<String>?): FriendSelectionSpec`。
- Produces: `LegacyFriendSelectionAdapter.fromCounts(counts: Map<String, Int>?, defaultCount: Int = 1): FriendSelectionCountSpec`。
- Constraint: 适配仅在模块明确迁移时调用，第一阶段不自动重写现有配置文件。

- [x] **Step 1: 写旧配置适配失败测试**

```kotlin
@Test
fun legacyIdsRemainExplicitAndNeverBecomeAllFriends() {
    val spec = LegacyFriendSelectionAdapter.fromIds(linkedSetOf("100", "200"))
    assertEquals(FriendSelectionScope.EXPLICIT, spec.selectionScope)
    assertEquals(linkedSetOf("100", "200"), spec.includeUserIds)
}

@Test
fun legacyCountsKeepPerUserValues() {
    val spec = LegacyFriendSelectionAdapter.fromCounts(linkedMapOf("100" to 3, "200" to 0))
    assertEquals(3, spec.userCountOverrides["100"])
    assertEquals(0, spec.userCountOverrides["200"])
}
```

- [x] **Step 2: 运行适配器测试，确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.util.friend.LegacyFriendSelectionAdapterTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 实现纯转换适配器**

空值转为空 `EXPLICIT` 规则；ID 去除首尾空白和空字符串并保留原顺序；次数原值保留到 `userCountOverrides`，不把 `0` 静默改成默认值。

- [x] **Step 4: 记录下一阶段模块迁移顺序**

只在本计划末尾记录后续顺序，不修改生产模块：森林 -> 庄园 -> 合种 -> 运动 -> 新村。每个模块迁移必须独立建计划、保留原配置键、提供旧值一次性读取适配和模块级回归测试。

- [x] **Step 5: 运行好友中心全部测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.entity.friend.*" `
  --tests "fansirsqi.xposed.sesame.util.friend.*" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`；现有业务模块文件无变更。

## Acceptance Gate

- 动态 `ALL_FRIENDS` 在仓库快照新增好友后自动包含新好友，配置本身不新增 ID。
- 未知范围、损坏数据和空账号均收窄到“不选择任何好友”。
- 排除规则和全局黑名单绝不被显式用户、分组或全部好友覆盖。
- 同步不删除旧档案，不丢失黑名单、分组和能力历史，也不修改 `UserMap`。
- 旧字段适配默认保持 `EXPLICIT`，不改变现有模块运行行为。
- 本计划不包含 UI、全模块迁移、Git 暂存或提交。
