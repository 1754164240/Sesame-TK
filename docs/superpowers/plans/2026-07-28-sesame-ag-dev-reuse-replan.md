# Sesame-AG dev 近期提交 Clean-room 复用实施计划（最近提交复核版）

> **状态校正（2026-07-29）：** 森林巡护和余额宝体验金安全子集已完成；本计划记录的 382 个测试与 5 个 APK 早于这两项改动，不能作为最终验收。最新缺口审计与执行顺序见 `docs/superpowers/plans/2026-07-29-sesame-ag-recent-commit-replan.md`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. 当前项目禁止未经授权创建 Git 提交，所有步骤只执行本地编辑和验证。

**Goal:** 基于 Sesame-AG `dev` 2026-07-12 至 2026-07-26 的功能提交、Sesame-VN 最近八次本地提交和当前未提交实现，识别已有、部分具备、缺失和明确不复用的能力，并按实际完成度与风险优先级独立实现可复用部分。

**Architecture:** 不复制、移植或 cherry-pick AGPL 源码，只复用接口行为、响应状态和失败分类。继续使用 Sesame-VN 现有 `ModelTask`、`RequestManager`、策略对象和工作流对象，不引入上游通用 `TaskFlow`；所有动作响应只代表请求已提交，完成状态必须来自动作后的 RPC 回查。

**Tech Stack:** Kotlin、Java、Android、libxposed API 102、`org.json`、Kotlin Coroutines、JUnit 4、Gradle。

## Global Constraints

- 仅支持 libxposed API 102。
- 新动作默认关闭，已有配置键不得静默改名或丢失。
- 未知结构、空响应、查询失败、状态未刷新均保留重试。
- 禁止提现、借贷、充值、下单、付费兑换、广告伪完成、真实游戏和游戏改分。
- 运动动态价格“赎回自己”只查询，不购买。
- 不引入上游通用 `TaskFlow`。
- 文件保存为 UTF-8 无 BOM，代码注释使用中文。
- 未经用户明确授权，不创建 Git 提交。

---

## 零、2026-07-28 最新提交基线

- Sesame-AG `dev` 当前头提交为 `64d5c44b00`，仅更新 CI 版本号。
- Sesame-AG `dev` 最新业务提交仍为 `6bde6e840f`，提交时间为 2026-07-26；原计划没有漏掉 7 月 27 日或 28 日的新业务提交。
- Sesame-VN 当前分支为 `yang`，本地与 `origin/yang` 均停在 `509f626a`。
- 当前工作区包含大量未提交实现，禁止切换工作树、重置或覆盖；后续必须先完成现有半成品，再新增业务域。
- 当前执行点为 Task 9：Task 1-8 已完成，好友中心和持久调度的两份独立实施计划已建立但尚未执行。

最近八次本地提交对计划的影响：

| 本地提交 | 已落地能力 | 对计划的影响 |
| --- | --- | --- |
| `8f93d96c` | 会员新版任务协议基础解析 | Task 2 已补多查询源、动作后详情回查和安全策略接入，后续不再重写基础协议 |
| `ba967bdf` | 任务调度与 RPC 稳定性 | RPC 基础层不再整体替换，只审计剩余离线、验证和频率限制分类 |
| `0ab6e3df` | 会员宝箱与限时游戏访问奖励 | Task 2 已补浮球动作后回查；Task 7 只保留安全扩展 |
| `a09fbfba` | 任务重载与家庭捐步同步 | Task 9 不重复实现基础重载；持久调度仍需独立立项 |
| `fa73d2f1` | libxposed API 102 | API 102 迁移已完成，只保留全量回归 |
| `82fdb4ad`、`7ebaa1b4` | Android 发布流程调整 | 与业务复用无关，不纳入当前批次 |
| `509f626a` | 福气鱼池与森林 1V1 领奖 | 作为新增回归面，不改变 Sesame-AG 复用优先级 |

当前未提交实现状态：

| 范围 | 状态 | 下一验收点 |
| --- | --- | --- |
| 会员和游戏中心安全止损 | 已完成 | 已通过 `antMember.*` 与 `TaskBlacklistPolicyTest` |
| 会员多源协议和详情判定 | 已完成 | 三源查询、报名门禁、动作后详情和浮球回查已通过会员域全量测试 |
| 森林抽抽乐和青春特权 | 已完成 | 后续只做全量回归 |
| 运动健康岛与 SportsPlay 新路线 | 已完成 | `walkGo/joinPath/receiveEvent` 均已要求明确 ACK 和动作后服务端状态确认 |
| 庄园家庭流程和容量策略 | 已完成 | 容量策略、主领奖、大表鸽和乐园奖励回查已通过 `antFarm.*` |
| 福气鱼池和森林 1V1 | 已提交 | 保持功能边界不变，纳入每阶段回归 |
| 果园 | 已完成 | 最新任务容器、免费任务门禁和动作后奖励终态已通过果园域测试 |
| 芝麻信用 | 已完成 | 次日、时段、芝麻粒和任务终态已通过会员域测试 |
| 会员安全扩展 | 已完成 | 会员域 106 项测试通过；P2E 现金和拼贴世界仅保留只读查询 |
| 海洋、合种、新村与 RPC 风险 | 已完成 | 四个独立计划已完成，联合定向测试通过 |
| 好友中心 | 已规划 | 先落实体、仓库、动态解析、能力和旧配置适配，不批量迁移模块 |
| 持久调度 | 已规划 | 先落纯注册表和单闹钟协调器，再接 Android 广播和目标进程路由 |

## 一、重新核对后的提交清单

状态说明：

- **已完成**：当前工作区已经独立实现，并有定向测试证据。
- **部分完成**：已有解析器或动作，但缺少回查、白名单或完整状态机。
- **缺失**：当前项目没有对应能力。
- **不复用**：违反安全边界、许可边界或当前架构选择。

| 上游提交 | 上游行为 | 当前状态 | 复用决定 |
| --- | --- | --- | --- |
| `6bde6e840f` | 芝麻炼金续航、大表鸽芝麻粒确认 | 部分完成 | 复用次日奖励、时段奖励和芝麻粒回查；满级红包只查询 |
| `d4c462d797` | 庄园雇佣饲料任务、大表鸽奖励回查 | 部分完成 | 复用任务状态和资产回查 |
| `2d98d1736f` | 果园最新任务参数和限时任务收敛 | 缺失 | 复用参数、容器和免费任务白名单 |
| `5b9b09acde` | 移除未验证累计奖励，完善森林抽抽乐 | 已完成 | 保留当前 `ForestDrawTaskStatePolicy`，补全量回归 |
| `09d687e7f2` | 独立青春特权模块 | 已完成 | 保留当前 `YouthPrivilegePolicy/Workflow` |
| `76d8f4488a` | 健康岛新路线接口和状态闭环 | 部分完成 | 复用新路线状态机；不恢复旧路线 |
| `1f42aed364` | 无进展动作回查、新村不可闭环任务跳过 | 部分完成 | 只复用“无进展不完成”语义，不引入 `TaskFlow` |
| `b132edc765` | 运动多容器、动态价格赎回 | 部分完成 | 多容器已完成；赎回只保留价格查询 |
| `74c371b446` | 芝麻信用未确认状态保留重试 | 缺失 | 复用未知状态和未确认状态分类 |
| `b987b47728` | 赚现金、拼贴世界闭环 | 部分完成 | 复用查询、签到、免费抽取和领奖；提现不复用 |
| `9aaf251b02` | 果园领奖动作后回查 | 缺失 | 复用任务和乐园奖励回查 |
| `b98c8dc7fb` | 庄园容量、家庭和乐园奖励确认 | 部分完成 | 策略与家庭流程已验证；主领奖流程尚未接入 |
| `dbfc191d81` | 森林抽抽乐完成态收紧 | 已完成 | 保留当前独立策略；游戏任务继续跳过 |
| `a658588299` | 默认黑名单和延后终态 | 部分完成 | 默认安全规则已接入；延后终态继续按模块专用工作流实现 |
| `205a8318e3` | 森林巡护筛选和派遣排序 | 已完成 | 已实现常驻动物、图鉴缺片、地图和伙伴库存/收益排序，保留森林域回归 |
| `e1fecd512e` | 持久调度和系统唤醒 | 已规划 | 结合三个后续修正提交，复用注册表、去重键和恢复语义 |
| `580b15d62f` | 动态“全部好友”选择范围 | 已规划 | 结合最初好友中心提交，按当前好友模型 clean-room 重写 |
| `a4984dbe84` | 海洋复用森林自收过滤策略 | 已完成 | 已使用独立海洋阈值和默认关闭开关 |
| `89d9360042` | 海洋生态编码刷新、不可闭环任务收敛 | 已完成 | 已按服务端生态编码刷新并阻断游戏、广告、金融和未知任务 |
| `3fe2394580` | 余额宝体验金任务、风控止损拆分 | 安全子集已完成 | 只实现查询、签到、待使用券处理和动作后回查；全局风控继续使用更严格的 `RequestManager` 熔断 |
| `f58bd2573a` | 合种浇水流程与状态确认 | 已完成 | 普通、真爱、组队合种均按动作前后服务端状态确认 |
| `82c3873d3a` | 新村 XLight 风控和任务确认 | 已完成 | 已收紧双错误码风控并在签到、任务、邀请、事件和领奖后回查 |
| `88932c55ef` | RPC 反射调用和离线风控分类 | 已完成 | 保留当前 Bridge，已补离线、验证、频控、可重试和未知失败分类 |
| `64d5c44b00` | CI 版本号更新 | 不适用 | 当前仓库有独立发布流程，不复用 |

## 二、会员新版任务协议差异

| 维度 | Sesame-VN 当前实现 | 上游最新行为 | 计划 |
| --- | --- | --- | --- |
| 查询来源 | 只查询签到页任务墙 | 合并签到页和会员积分任务进度接口 | 增加第二查询源并按稳定键去重 |
| 容器 | `adTaskList/categoryTaskList/pureTaskList` | 兼容更多根对象和任务进度容器 | 解析结果返回 `recognized`，未知容器不判定完成 |
| 任务标识 | 普通任务主要使用 `configId/processId` | 按 `processId/configId/adBizId` 分层去重 | 统一稳定键，禁止标题作为唯一终态依据 |
| 目标业务 | 只读取第一个 `targetBusiness`，只支持 `BROWSE` | 扫描全部目标，支持 `BROWSE/CALL_APP` | `CALL_APP` 只允许回查，不主动拉起或伪完成 |
| 允许范围 | 广告仅按标题排除“玩游戏”，普通浏览没有协议白名单 | 配置 ID 白名单叠加模块黑名单 | 增加会员专用白名单和危险动作黑名单 |
| 动作终态 | `execute/taskFinish` 成功即记完成 | 动作后查单任务详情，区分确认、部分进度、待确认 | 本地统一改为回查后记成功 |
| 浮球宝箱 | 触发响应 `SUCCESS` 即记成功 | 识别倒计时、后续任务和未闭环广告 | 触发后重新查询浮球状态；广告后续保持禁用 |
| 游戏中心 | 对全部待做平台任务调用 `doTaskSend` | 真实通关/订单任务显式跳过 | 先落地游戏安全分类器，再保留签到和免费领奖 |
| P2E | 未实现 | 支持浏览任务、签到、抽金币，同时包含提现 | 只复用浏览、签到、免费抽取和已完成领奖 |
| 风控 | 局部识别“任务安全性校验失败” | 会员域统一止损 | 增加会员域本轮停止状态，不写每日完成标记 |

结论：上游不能完成真实游戏通关或订单任务。其最新实现明确跳过普通真实游戏任务，并将 P2E `GAME_TRAN_TASK`、广告任务加入自动跳过列表；能够自动处理的是平台浏览/访问任务、签到、免费抽取和已经完成后的领奖。当前项目的游戏中心逻辑反而需要先收紧。

---

### Task 1: 会员任务与游戏中心安全止损（已完成）

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskSafetyPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/GameCenterTaskPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskSafetyPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/GameCenterTaskPolicyTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskProtocol.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/util/TaskBlacklist.kt`

**Interfaces:**

- `MemberTaskSafetyPolicy.classify(task: MemberTaskCandidate): MemberTaskDecision`
- `MemberTaskSafetyPolicy.isConfirmed(before: MemberTaskState, after: MemberTaskState?): Boolean`
- `GameCenterTaskPolicy.classifyPlatformTask(task: JSONObject): GameCenterTaskDecision`
- `GameCenterTaskPolicy.classifyP2eTask(task: JSONObject): GameCenterTaskDecision`
- `MemberTaskProtocol.parseTaskSnapshot(response: JSONObject): MemberTaskSnapshot`

- [x] **Step 1: 写会员协议安全测试**

```kotlin
@Test
fun `未知任务配置不自动执行`() {
    assertEquals(
        MemberTaskDecision.SKIP_UNSUPPORTED,
        MemberTaskSafetyPolicy.classify(
            MemberTaskCandidate(
                configId = "unknown",
                title = "未知任务",
                targetBusiness = "BROWSE#15S#x"
            )
        )
    )
}

@Test
fun `CALL_APP只允许回查`() {
    assertEquals(
        MemberTaskDecision.VERIFY_ONLY,
        MemberTaskSafetyPolicy.classify(
            MemberTaskCandidate(
                configId = "allowed-call-app",
                title = "访问任务",
                targetBusiness = "CALL_APP#scene"
            )
        )
    )
}
```

- [x] **Step 2: 写游戏中心真实游戏阻断测试**

```kotlin
@Test
fun `真实游戏通关任务必须跳过`() {
    val task = JSONObject()
        .put("actionType", "NORMAL")
        .put("taskType", "GAME_TRAN_TASK")
        .put("buttonText", "去完成")
        .put("gameId", "game")
        .put("appId", "app")
        .put("jumpLink", "alipays://platformapi/startapp")
        .put("title", "玩游戏通过一关")

    assertEquals(
        GameCenterTaskDecision.SKIP_REAL_GAME,
        GameCenterTaskPolicy.classifyPlatformTask(task)
    )
}
```

- [x] **Step 3: 运行失败测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antMember.MemberTaskSafetyPolicyTest" `
  --tests "fansirsqi.xposed.sesame.task.antMember.GameCenterTaskPolicyTest" `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：因策略类尚不存在而失败。

- [x] **Step 4: 实现会员专用白名单和游戏分类器**

`MemberTaskDecision` 固定为：

```kotlin
enum class MemberTaskDecision {
    EXECUTE_BROWSE,
    FINISH_AD,
    VERIFY_ONLY,
    CLAIM_ONLY,
    SKIP_REAL_GAME,
    SKIP_AD,
    SKIP_FINANCIAL,
    SKIP_UNSUPPORTED
}
```

分类顺序必须为：金融/下单/提现 -> 真实游戏 -> 广告 -> 已完成领奖 -> 明确白名单浏览 -> `CALL_APP` 回查 -> 未知跳过。

- [x] **Step 5: 收紧 `enableGameCenter()`**

在任何 `doTaskSignup/doTaskSend` 前调用 `GameCenterTaskPolicy`。`SKIP_REAL_GAME/SKIP_AD/SKIP_FINANCIAL/SKIP_UNSUPPORTED` 不允许调用动作 RPC；平台动作后重新查询任务列表，只有任务进入服务端终态才打印成功。

- [x] **Step 6: 接入真正的默认安全规则**

当前 `TaskBlacklist` 内部默认集合为空，顶层 `defaultBlacklist` 没有被使用。改为显式注入模块安全规则，不再依赖一个全局标题模糊集合；已有用户自定义黑名单继续保留。

- [x] **Step 7: 运行会员定向测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antMember.*" `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：全部通过，且源码审计不存在真实游戏和提现调用路径。

### Task 2: 会员新版协议动作后回查（已完成）

**Files:**

- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskProtocol.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMemberRpcCall.java`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskProtocolTest.kt`

**Interfaces:**

- `MemberTaskSnapshot(recognized: Boolean, tasks: List<MemberTaskState>)`
- `MemberTaskState(stableKey: String, processId: String, configId: String, status: String, current: Int?, limit: Int?)`
- `MemberTaskProtocol.findAfterState(snapshot: MemberTaskSnapshot, before: MemberTaskState): MemberTaskState?`

- [x] **Step 1: 增加多查询源、多容器和去重失败测试**
- [x] **Step 2: 增加动作成功但状态未刷新不得确认的失败测试**
- [x] **Step 3: 增加重复任务部分进度测试，`current < limit` 返回待后续调度**
- [x] **Step 4: 实现任务快照，稳定键优先级为 `processId -> configId#adBizId -> configId`**
- [x] **Step 5: `apply/execute/taskFinish/triggerSignFloatingBall` 后重新查询**
- [x] **Step 6: 只有 `AWARDED/COMPLETE/RECEIVED/SUCCESS` 或明确计数推进才记成功**
- [x] **Step 7: 空响应、未知容器和风控响应返回可重试/本轮止损，不写每日完成标记**

Task 2 完成证据：

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antMember.*" `
  --tests "fansirsqi.xposed.sesame.util.TaskBlacklistPolicyTest" `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL`。`MemberTaskWorkflow` 与 `MemberTreasureBoxWorkflow` 已接入，旧的 ACK 即成功路径已删除；后续阶段只做回归，不再修改会员基础协议。

### Task 3: 完成庄园容量与奖励闭环（已完成）

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRewardWorkflow.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRewardWorkflowTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarm.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRewardPolicy.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRewardPolicyTest.kt`

**Interfaces:**

- `FarmRewardCandidate(id: String, amount: Int)`：容量选择输入。
- `FarmRewardOutcome`：至少区分 `CONFIRMED`、`RETRY`、`SKIPPED_CAPACITY` 和 `STOPPED_RISK`。
- `AntFarmRewardWorkflow`：封装“查询 -> 选择 -> 动作 -> 再查询”，不保存跨次运行状态。
- `AntFarmRewardPolicy.isTaskReceived(response, taskId)`：只有回查任务为 `RECEIVED` 才确认。
- `AntFarmRewardPolicy.isParadiseRewardConfirmed(response, taskType)`：只有原奖励终态或消失才确认。

**Current verified:** `AntFarmWalkDonateTaskTest`、`AntFarmFamilyTest`、`AntFarmRewardPolicyTest` 已于 2026-07-28 通过；主流程尚未消费容量策略，仍存在 20 点后溢出领取和 ACK 后直接增加本地库存的旧逻辑。

- [x] **Step 1: 先写 `AntFarmRewardWorkflowTest`，覆盖容量 120 时只领取 90+30、动作成功但回查仍为 `FINISHED` 返回 `RETRY`、回查为 `RECEIVED` 才返回 `CONFIRMED`**
- [x] **Step 2: 运行 `AntFarmRewardWorkflowTest`，确认因工作流不存在而失败**
- [x] **Step 3: 实现最小 `AntFarmRewardWorkflow`，每次动作后调用对应查询 RPC，空响应、未知容器和状态未刷新均返回 `RETRY`**
- [x] **Step 4: 在 `receiveFarmAwards()` 中从 `FINISHED` 任务构造 `FarmRewardCandidate`，以真实 `foodStockLimit - foodStock` 调用 `selectWithinCapacity`**
- [x] **Step 5: 删除“20 点后允许溢出领取”的推定；只有回查 `RECEIVED` 后才调用 `add2FoodStock(awardCount)` 并记录成功**
- [x] **Step 6: 大表鸽领奖后重新 `listZhimaNpcFarmTask()`；任务仍为 `FINISHED`、查询失败或结构未知时不得记录芝麻粒到账**
- [x] **Step 7: 乐园限时奖励后重新 `queryParadiseLimitedActivity()`；原 `taskType` 仍可领取时保留重试**
- [x] **Step 8: 捐蛋排位、商城购买、广告任务、庄园游戏和游戏改分不新增动作；既有 `recordFarmGame` 分支保持默认关闭且不进入新工作流**
- [x] **Step 9: 运行 `AntFarmRewardPolicyTest`、`AntFarmRewardWorkflowTest`、`AntFarmFamilyTest`、`AntFarmWalkDonateTaskTest`，再运行完整 `antFarm.*` 测试**

定向命令：

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antFarm.*" `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

### Task 4: 完成运动新路线闭环（已完成）

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRoutePolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRouteWorkflow.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRoutePolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRouteWorkflowTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSports.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRpcCall.kt`

**Interfaces:**

- `AntSportsRouteSnapshot(recognized, joinedPathId, pathId, forwardStepCount, remainStepCount, completion, claimableEventIds)`。
- `AntSportsRouteOutcome`：至少区分 `CONFIRMED`、`PARTIAL`、`RETRY` 和 `SKIPPED_UNSUPPORTED`。
- `AntSportsRouteWorkflow`：封装 `queryUser/queryPath/queryWorldMap/queryCityPath/joinPath/walkGo/receiveEvent` 的单轮状态推进。

**Current verified:** `NeverlandPolicy` 只负责健康岛任务中心、泡泡和签到；SportsPlay 已由独立 `AntSportsRoutePolicy/Workflow` 承载。空响应、未知前态、未推进步数、未切换路线和未消失宝箱均返回 `RETRY`，所有成功日志只来自动作后回查。

- [x] **Step 1: 先写路线策略测试，覆盖非法城市、缺失 ID、已完成路线、步数推进、宝箱消失和未知结构**
- [x] **Step 2: 先写工作流失败测试，证明 ACK 成功但步数未推进、路线未切换或宝箱未消失时返回 `RETRY`**
- [x] **Step 3: 实现 `AntSportsRoutePolicy/Workflow`，仅接受服务端明确的路线状态、待收事件和免费奖励**
- [x] **Step 4: `walkGo` 后回查同一 `pathId` 的 `forwardStepCount`；仅增加时确认，未达路线终点返回 `PARTIAL`**
- [x] **Step 5: `joinPath` 前后回查 `joinedPathId`；必须从其他路径切换到目标路径才确认**
- [x] **Step 6: `receiveEvent` 后回查 `treasureBoxList`；同一 `eventBillNo/boxNo` 消失才确认**
- [x] **Step 7: 删除 SportsPlay 旧路线选择和直接动作分支以及空 `pathId` 默认常量回退，保留工作流单一入口**
- [x] **Step 8: 运行 `AntSportsRoute*Test` 和完整 `antSports.*` 测试；动态价格“赎回自己”继续只查询**

### Task 5: 果园任务和限时奖励闭环（已完成）

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchardRewardPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchardRewardPolicyTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchard.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchardRpcCall.kt`

- [x] **Step 1: 写根对象、`data/result` 和多任务容器解析测试**
- [x] **Step 2: 写动作后仍为 `FINISHED` 不得确认的测试**
- [x] **Step 3: 写游戏、广告、充值和下单任务必须跳过的测试**
- [x] **Step 4: 按最新参数构造完成和领奖 RPC**
- [x] **Step 5: 完成、领奖、乐园限时奖励后重新查询**
- [x] **Step 6: 只有 `RECEIVED` 或目标奖励记录消失才记成功**
- [x] **Step 7: 保留当前果树/摇钱树独立施肥计数，不引入上游状态结构**

### Task 6: 芝麻信用、炼金与芝麻粒终态（已完成）

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/SesameCreditRewardPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/SesameCreditRewardPolicyTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMemberRpcCall.java`

- [x] **Step 1: 写次日奖励、时段奖励和芝麻粒回查测试**
- [x] **Step 2: 任务数组为空但容器未知时返回 `RETRY`**
- [x] **Step 3: `claimAward/alchemyCompleteTimeLimitedTask/collectCreditFeedback` 后重新查询**
- [x] **Step 4: 大表鸽任务只有服务端任务终态或资产增加时确认**
- [x] **Step 5: 芝麻炼金广告任务、跳转 APP、下单、充值和未知模板统一跳过，不调用广告完成上报**
- [x] **Step 6: 满级红包只输出查询状态，禁止调用提现接口**
- [x] **Step 7: 运行 `SesameCreditRewardPolicyTest` 和全部会员测试**

### Task 7: 会员安全扩展能力（已完成）

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/GameCenterRewardPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/GameCenterRewardPolicyTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMemberRpcCall.java`

- [x] **Step 1: 游戏中心签到动作后回查 `signInStatus=true`**
- [x] **Step 2: 玩乐豆领取后回查待收列表为空或资产增加**
- [x] **Step 3: P2E 只允许 `PLATFORM_TRAN_TASK + VIEW_TASK`**
- [x] **Step 4: P2E 签到、免费抽金币和已完成领奖动作后回查**
- [x] **Step 5: `GAME_TRAN_TASK/LIGHT_AD_TASK` 和未知任务不执行**
- [x] **Step 6: 账单贴纸、拼贴世界只处理查询、签到和明确免费领奖**
- [x] **Step 7: 赚现金档位可以查询，但不得调用现金兑换或提现 RPC**

### Task 8: 次级业务域安全闭环

本任务已按海洋、合种、新村和 RPC 风险四个批次完成，并通过联合定向测试。对应实施记录：

- `docs/superpowers/plans/2026-07-28-ant-ocean-safe-closure.md`
- `docs/superpowers/plans/2026-07-28-ant-cooperate-water-confirmation.md`
- `docs/superpowers/plans/2026-07-28-ant-stall-xlight-safety.md`
- `docs/superpowers/plans/2026-07-28-rpc-risk-classification.md`

**候选文件：**

- `app/src/main/java/fansirsqi/xposed/sesame/task/antOcean/AntOcean.java`
- `app/src/main/java/fansirsqi/xposed/sesame/task/antCooperate/AntCooperate.kt`
- `app/src/main/java/fansirsqi/xposed/sesame/task/antStall/AntStall.kt`
- `app/src/main/java/fansirsqi/xposed/sesame/hook/rpc/bridge/RpcBridge.java`

- [x] **Step 1: 海洋能量球复用森林阈值判断，但保留独立配置**
- [x] **Step 2: 海洋按服务端生态编码刷新保护地**
- [x] **Step 3: 合种浇水后回查项目进度或剩余次数**
- [x] **Step 4: 新村 XLight 风控停止当前链路，小游戏任务跳过**
- [x] **Step 5: RPC 离线、验证、频率限制和未知失败分类**
- [x] **Step 6: 每个模块独立定向测试，不新增通用任务引擎**

### Task 9: 好友中心和持久调度独立立项

这两项是独立子项目，已经分别完成实施计划，后续不能直接混入业务闭环修改。

#### 9A 好友中心

- [x] 计划：`docs/superpowers/plans/2026-07-28-friend-center-foundation.md`
- [x] 实施实体、仓库、`UserMap` 只读同步和动态“全部好友”解析。
- [x] 实施分组、黑名单、玩法能力和旧 `Set<String>` / `Map<String, Int>` 适配。
- [x] 第一阶段不迁移现有业务模块；后续按森林、庄园、合种、运动、新村分别立项。

#### 9B 持久调度

- [x] 计划：`docs/superpowers/plans/2026-07-28-persistent-scheduler.md`
- [x] 实施纯 Kotlin 注册表、generation、租约和单闹钟协调器。
- [x] 接入 Android Alarm、恢复广播、Binder UID 校验和目标进程安全路由。
- [x] 首批只迁移主轮询、每日零点和自定义唤醒；短延迟任务继续使用进程内调度。
- [x] 持久调度和前台拉起使用两个独立开关，均默认关闭。

### Task 10: 全量验收和禁用动作审计

- [x] **Step 1: 运行全部单元测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 2: 构建 Debug APK**

```powershell
.\gradlew.bat assembleDebug `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 审计禁止动作**

```powershell
rg -n "withdraw|cashExchange|buyMember|recordFarmGame|finishAdTask|GAME_TRAN_TASK|LIGHT_AD_TASK" `
  app/src/main/java/fansirsqi/xposed/sesame
```

每个命中必须归类为：无调用的 RPC 声明、明确阻断分支、现有遗留危险功能或允许的只读查询。遗留危险功能必须默认关闭，并在当前复用入口中不可达。

2026-07-29 审计结论：

- `buyMember` 仅保留 RPC 声明，生产代码无调用者，并由运动路线测试约束。
- `withdrawable`、`cashExchangeModule` 和 `withdrawPreConsult` 仅用于只读状态或资格查询，不执行提现、现金兑换或购买。
- 会员 `finishAdTask` 注入仍保留兼容接口，但安全策略对任何非空 `adBizId` 固定返回 `SKIP_AD`，不会产生 `FINISH_AD` 决策。
- 庄园抽抽乐的旧私有广告实现保留但入口已阻断 `SHANGYEHUA_DAILY_DRAW_TIMES` 与 `IP_SHANGYEHUA_TASK`，不再调用广告伪完成 RPC。
- `GAME_TRAN_TASK`、`LIGHT_AD_TASK` 和其他广告动作均由策略明确跳过。
- `recordFarmGame` 是既有危险功能，配置默认 `false`，当前复用没有新增可达路径；真机验收不得开启。

- [x] **Step 4: 检查文本和编码**

```powershell
git diff --check
```

2026-07-29 自动化证据：382 个测试、80 个测试套件，`failures=0`、`errors=0`、`skipped=0`；`assembleDebug` 生成 universal、arm64-v8a、armeabi-v7a、x86、x86_64 共 5 个 APK；`git diff --check` 退出码为 0；修改和新增文本文件均为 UTF-8 无 BOM。

- [ ] **Step 5: 真机验收**

验证 API 102 Hook、会员任务回查、游戏任务跳过、森林抽抽乐、森林 1V1、青春特权、福气鱼池、运动路线、庄园容量、果园和芝麻信用。遇到验证码、风控或安全验证立即停止当前业务链路。

## 执行顺序

1. Task 1-2 已完成：会员安全止损、三源协议、普通/广告详情回查和浮球宝箱回查只保留回归。
2. Task 3 已完成：庄园容量策略、主领奖、大表鸽和乐园奖励均已动作后回查。
3. Task 4 已完成：SportsPlay 新路线已使用独立策略和工作流，`walkGo/joinPath/receiveEvent` 均完成动作前后状态比较。
4. Task 5 已完成：果园最新任务容器、参数和免费奖励终态已接入，新链路不执行游戏/广告动作。
5. Task 6 已完成：芝麻信用、炼金和芝麻粒终态已接入；广告任务跳过，满级红包只查询。
6. Task 7 已完成：会员签到、玩乐豆、P2E 安全任务和贴纸均动作后回查；现金档位与拼贴世界仅查询。
7. Task 8 已完成：海洋、合种、新村和 RPC 风险四个独立批次均完成动作后回查与风险收窄。
8. Task 9 计划已完成：先实施好友中心基础，再实施持久调度；两者独立验收，不与业务闭环混合。
9. Task 10：全量单元测试、Debug 构建、禁止动作审计和真机验收，并覆盖 `509f626a` 的福气鱼池与森林 1V1。

每个任务必须遵守同一验收门槛：先看到新增测试失败，再写生产实现；动作响应不能作为完成依据；定向测试通过后运行受影响模块全量测试；未经授权不创建 Git 提交。
