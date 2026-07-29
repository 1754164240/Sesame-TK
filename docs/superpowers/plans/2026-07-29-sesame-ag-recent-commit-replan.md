# Sesame-AG 最近提交复核后的复用实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking。当前仓库禁止使用子代理，禁止未经授权暂存或提交。

**Goal:** 以 Sesame-AG `dev@64d5c44b00758035957d4e2a62de1e0f9d3d4b58`、Sesame-VN `yang@509f626a9e5b1deb9b42225a27d081e7b56fb296` 和当前未提交实现为基线，补齐仍可安全复用的业务能力，拒绝上游不安全或不适用实现，并完成自动化与真机验收。

**Architecture:** 不复制、迁移或 cherry-pick 上游 AGPL 源码，只根据 RPC 行为、状态字段和失败语义 clean-room 重写。每个业务域使用独立的纯 Kotlin 策略和工作流，RPC ACK 只代表请求已提交，业务完成必须由动作后的服务端状态回查确认。未知结构一律收窄执行范围，不引入上游通用 `TaskFlow`。

**Tech Stack:** Kotlin、Java、Android、libxposed API 102、`org.json`、Kotlin Coroutines、JUnit 4、Gradle、Jetpack Compose、Android WebView。

## Global Constraints

- 仅支持 libxposed API 102。
- 新功能和新入口默认关闭；已有配置键不得静默改名或丢失。
- 禁止提现、借贷、充值、下单、付费兑换、广告伪完成、真实游戏和游戏改分。
- 广播、Activity 拉起、Binder 返回和 RPC ACK 均不代表业务终态。
- 空响应、未知容器、字段缺失和回查未推进均返回重试或保护性跳过，不写每日完成标记。
- 余额宝不实现 `task.complete`、`task.trigger`、`exchangeYebExpGold` 及任何资金操作。
- Gradle 命令固定追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`，超时 300000 ms。
- 文件使用 UTF-8 无 BOM，代码注释使用中文。
- 保留当前工作区全部既有未提交状态；2026-07-29 本次重新复核时为 166 项（46 个已跟踪修改、120 个未跟踪文件、0 个暂存项），禁止 reset、checkout、切换工作树、暂存和提交。

---

## 一、提交基线

2026-07-29 已通过 `git ls-remote` 和全新浅克隆重新核对上游 `dev`；远端头仍为 `64d5c44b00758035957d4e2a62de1e0f9d3d4b58`。上游没有 2026-07-26 之后的新业务提交，最新提交 `64d5c44` 仅更新 CI 版本号。

最近八次 Sesame-VN 提交对本计划的影响：

| 本地提交 | 已落地能力 | 计划影响 |
| --- | --- | --- |
| `509f626a` | 福气鱼池、森林 1V1 领奖 | 保留为最终回归面，不替代森林通用任务闭环 |
| `7ebaa1b4` | Android CI YAML 修复 | 不进入业务复用批次 |
| `82fdb4ad` | 发布工作流精简 | 不进入业务复用批次 |
| `fa73d2f1` | libxposed API 102 | 已完成，仅做构建和真机 Hook 回归 |
| `a09fbfba` | 任务重载、家庭捐步同步 | 已完成；持久调度继续使用当前独立实现 |
| `0ab6e3df` | 会员宝箱、限时游戏访问奖励 | 已由会员安全工作流收紧，真实游戏仍阻断 |
| `ba967bdf` | 调度和 RPC 稳定性 | 继续使用当前全局离线/验证熔断，不采用上游局部放行 |
| `8f93d96c` | 会员新版任务协议 | 已补多容器、去重和动作后回查，不重写基础协议 |

会员新版任务协议结论：

- 当前项目的新版协议覆盖多任务容器、任务去重、申请/执行参数、单任务详情回查、宝箱状态和限时游戏访问奖励；任务 ACK 后必须重新查询服务端状态，不能仅凭请求成功写完成标记。
- 当前项目只访问已识别的限时游戏入口并通过积分流水回查访问奖励，不执行真实游戏、改分、拼贴放置/合并或现金兑换。
- 上游能够自动处理部分已知游戏活动，但不是通用的“所有游戏任务完成器”；其中买回自己、拼贴世界、赚现金提现等链路会消耗游戏资源、改变玩法状态或涉及资金，因此不进入复用计划。

上游最近十四次业务提交对本计划的影响：

| 上游提交 | 当前覆盖状态 | 重新规划结论 |
| --- | --- | --- |
| `6bde6e8` | 安全子集已覆盖 | 芝麻炼金领奖续航和大表鸽芝麻粒确认已有 `SesameCreditRewardPolicy/Workflow` 与 RPC 协议测试；资金、兑换和未知任务仍阻断，不新增任务 |
| `d4c462d` | 已覆盖 | `AntFarmRewardPolicy/Workflow` 已闭合任务领奖，`AntFarmNpcPolicy/Workflow` 已完成雇佣、遣返、领取产出和重雇的动作后回查；Task 3 已完成 |
| `2d98d17` | 奖励闭环已覆盖，浏览链路仍缺 | `AntOrchardRewardPolicy/Workflow` 已收敛奖励终态；上游浏览任务放宽不能直接复用，执行 Task 5 的专用白名单 |
| `5b9b09a` | 已覆盖 | 未验证累计奖励链已移除，`ForestChouChouLe` 已按动作后状态回查；不新增任务 |
| `09d687e` | 已覆盖 | 青春特权已拆为独立模块，签到、道具和任务均有纯策略/工作流测试；不再并入森林 `Privilege` |
| `76d8f44` | 部分覆盖 | 新路线的加入、行走、领奖回查已落地；见闻详情与待收碎片路线映射仍缺，执行 Task 4 |
| `1f42aed` | 安全子集已覆盖 | 新村无闭环任务已由专用安全策略跳过；不引入上游通用 `TaskFlow` |
| `b132edc` | 安全子集已覆盖 | `NeverlandPolicy` 已兼容多任务容器和任务别名；“买回自己”会消耗游戏能量并改变真实玩法状态，不复用；见闻详情 RPC 只在 Task 4 的只读筛选链路中 clean-room 实现 |
| `74c371b` | 已覆盖 | 芝麻信用未确认状态继续重试、明确终态才记完成的语义已由 `SesameCreditRewardPolicy/Workflow` 覆盖，不引入上游通用任务流 |
| `b987b47` | 拒绝危险子集 | 赚现金提现、现金兑换、拼贴世界放置和合并均不复用；现有 P2E 仅保留签到、免费抽取、领奖和动作后回查 |
| `9aaf251` | 已覆盖 | 果园 `finishTask`、乐园奖励和动作后回查已由 `AntOrchardRewardPolicy/Workflow` 覆盖；浏览任务仍只进入 Task 5 白名单 |
| `b98c8dc` | 已覆盖 | 庄园领奖容量、家庭/乐园奖励确认和限时任务保护已有定向策略；NPC 生命周期也已由独立策略和工作流闭合 |
| `dbfc191` | 已覆盖 | 森林抽抽乐终态、能量雨游戏阻断和完成标记复核已覆盖；真实游戏链路继续禁用 |
| `a658588` | 架构不复用 | 延后语义和默认黑名单按各模块独立策略复用，不迁移上游通用 `TaskFlow`；未知任务保持保护性跳过 |

本次复核新增验证证据：

- 2026-07-29 重新运行 `ForestMultiplier*Test`、`ForestGoldBallWorkflowTest`、`StatusWateredFriendTest` 和 `ForestAutomationPolicyTest`，共 6 个测试类、25 项，0 失败、0 错误、0 跳过，Gradle `BUILD SUCCESSFUL`。
- 芝麻炼金、庄园领奖、果园领奖、抽抽乐、青春特权、运动路线、新村任务共 15 个测试类、83 项，0 失败、0 错误、0 跳过。
- 以上均为定向回归，不替代 Task 8 的全量测试、APK 构建和真机验收。

## 二、此前漏列提交审计

| 上游提交 | 当前状态 | 结论 |
| --- | --- | --- |
| `a87b9f3` | 已完成 | 大表鸽任务领奖保持独立回查；NPC 雇佣、遣返、领取产出和重雇已全部改为动作后服务端状态确认 |
| `46a1102` | 缺失 | 森林多任务源、签到日标记和签到后刷新未落地；当前通用森林任务仍有直接完成游戏任务链路，执行 Task 1 |
| `058fc1d` | 部分覆盖 | 保护罩 24 小时阈值和测试已落地；金球结果、通用任务和道具终态仍需收紧，执行 Task 1、Task 2 |
| `bf15be4` | 不适用 | 当前项目没有捐蛋排位赛业务链，不能为不存在的链路增加“活动不可用”日标记 |
| `b6d2d44` | 架构已规避 | 当前捕获 Hook 在 `Application.attach` 中完成 `Log.init` 之后安装，不存在上游“配置加载前关闭 capture Appender”的同构开关 |
| `8b3c0d9` | 部分覆盖 | 持久调度和前台拉起策略已落地，Compose 状态卡未展示实际账户配置，执行 Task 6 |
| `ce6d7fd` | 缺失 | 当前运动路线只扫描城市路线，没有见闻详情和待收碎片映射，执行 Task 4 |
| `6ee8ab9` | 缺失安全子集 | 当前策略将所有 `VISIT` 任务直接跳过；只复用严格白名单浏览链路，执行 Task 5 |
| `a1ec0de` | 安全子集已完成 | 拼贴世界只读、P2E 签到/免费抽取/领奖已落地；方块放置合并、赚现金兑换和提现不复用 |
| `217def1` | 已完成 | 持久拉起、找能量、收能量、健康岛奖励等相关新增默认值均保持关闭 |
| `d3241d7` | 部分缺失 | 可见能量开关边界已明确；N 倍卡状态确认和缺卡补兑仍缺失，执行 Task 2 |
| `12ba459` | 缺失 | 当前仍使用旧 `semi_index.html`，缺时间编辑、列表筛选、好友范围预览和输入草稿落盘，执行 Task 7 |

补充状态：

| 上游提交 | 状态 | 证据 |
| --- | --- | --- |
| `205a8318e3` | 已完成 | `ForestPatrolPolicy` 已覆盖常驻动物、图鉴、地图和伙伴库存/收益排序 |
| `3fe2394580` | 安全子集已完成 | `YebExpGoldPolicy/Workflow/RpcCall` 只包含查询、签到和待使用券处理，开关默认关闭 |
| `3fe2394580` 风控拆分 | 不照搬 | 当前 `RequestManager` 对真实离线和安全验证执行全局熔断，比上游局部继续执行更严格 |

## Task 1: 森林通用任务协议与签到闭环

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestTaskPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestTaskProtocol.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestTaskWorkflow.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestTaskPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestTaskProtocolTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestTaskWorkflowTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForestRpcCall.java`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/data/StatusFlags.java`

**Interfaces:**

```kotlin
enum class ForestTaskDecision {
    SIGN,
    COMPLETE_SAFE,
    CLAIM,
    TERMINAL,
    SKIP_UNSAFE,
    RETRY
}

data class ForestTaskSnapshot(
    val recognized: Boolean,
    val signs: List<ForestSignState>,
    val tasks: List<ForestTaskState>
)

enum class ForestTaskOutcome {
    CONFIRMED,
    NO_ACTION,
    RETRY
}
```

- [x] **Step 1: 写策略失败测试**

覆盖根级 `taskInfoList`、`forestTasksNew[*].taskInfoList`、`taskGroupInfoList[*].taskInfoList`、递归子任务、重复任务去重、未知容器，以及游戏、广告、下单、充值和付费任务固定返回 `SKIP_UNSAFE`。

- [x] **Step 2: 写工作流失败测试**

覆盖签到 ACK 后仍为未签到、领奖 ACK 后仍为 `FINISHED`、安全任务动作后无进度、查询失败、任务消失、状态变为 `RECEIVED`，并断言 `mokuai_senlin_hlz` 不调用等待、Activity 拉起或 `finishTask`。

- [x] **Step 3: 运行红灯测试**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antForest.ForestTaskPolicyTest" `
  --tests "fansirsqi.xposed.sesame.task.antForest.ForestTaskWorkflowTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 4: 实现纯策略和工作流**

`ForestTaskPolicy` 只解析、分类和比较前后快照；`ForestTaskWorkflow` 通过注入的查询、签到、完成和领奖函数执行动作。签到只有回查 `signed=true` 才确认；领奖只有目标任务变为 `RECEIVED/COMPLETED` 或从已识别列表消失才确认。

- [x] **Step 5: 接入多查询源**

使用 `popupTask`、`home_leaves_task_list`、`take_look_end_task_list`、`home_task_list` 和带明确 `extend` 的 `ANTFOREST_VITALITY_TASK`。任一来源结构未知时只忽略该来源，不把整轮记为完成；已确认签到后才使用日标记减少重复查询。

- [x] **Step 6: 删除旧游戏直达链路**

移除通用森林任务中的 30 秒等待、游戏 URL 日志和二次 `finishTask`。真实游戏、广告任务和未知任务仅记录跳过原因。

- [x] **Step 7: 运行森林域回归**

运行 `ForestTask*Test`、`ForestChouChouLeTest`、`ForestPatrolPolicyTest`、`ForestAutomationPolicyTest` 和全部 `antForest.*` 测试。

## Task 2: 森林金球结果与 N 倍卡补兑

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierWorkflow.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestGoldBallWorkflow.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierSchedulePolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierWorkflowTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierConfigTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestGoldBallWorkflowTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierSchedulePolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestMultiplierIntegrationTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/data/StatusWateredFriendTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/data/Status.kt`

**Interfaces:**

```kotlin
enum class ActiveMultiplierState {
    ACTIVE,
    CONFIRMED_NONE,
    INCONCLUSIVE
}

data class CollectedEnergyResult(
    val recognized: Boolean,
    val collected: Int
)

data class ActiveMultiplierSnapshot(
    val state: ActiveMultiplierState,
    val factor: Double,
    val endTime: Long,
    val propType: String
)

enum class MultiplierOutcome {
    CONFIRMED,
    NO_ACTION,
    RETRY
}
```

- [x] **Step 1: 写金球结果失败测试**

覆盖空响应、无 `bubbles`、`collectedEnergy=0`、成功正数和异常 JSON。只有明确正数返回成功，好友浇水日状态只能在该结果下更新。

- [x] **Step 2: 写 N 倍卡策略失败测试**

覆盖主页缺少 `usingUserProps`、已有生效卡、背包有卡但不满足替换阈值、背包无卡且允许补兑、补兑后仍无卡、使用 ACK 后主页未出现目标倍率。

- [x] **Step 3: 实现保护性决策并完成定向绿灯**

`ForestMultiplierPolicy` 已实现主页生效卡、背包候选、倍率和限时卡解析；`ForestMultiplierWorkflow` 已实现 `INCONCLUSIVE` 保护、补兑后查包和使用后查主页。金球解析已在 Step 4 拆分到独立 `ForestGoldBallWorkflow`；本次 6 个相关测试类共 25 项全部通过。

- [x] **Step 4: 接入金球结果和专用被浇水状态**

新增 `ForestGoldBallWorkflow`，汇总全部 `bubbles[*].collectedEnergy`；只有协议结构可识别且合计为正数时返回 `CONFIRMED`。`collectWater()` 已读取 `userId` 并传入工作流，空好友 ID、零收益、负收益和异常结构均不写成功状态。

`Status.kt` 已增加与现有 `waterFriendLogList` 分离的 `wateredFriendLogList`、`wateredFriendToday(friendId)` 和测试辅助入口；前者表示“我给好友浇水”，新字段只表示“好友给我浇水”，没有迁移或复用已有计数。

- [x] **Step 5: 补齐 N 倍卡配置和纯决策**

复用现有 `robExpandCard` 和 `robExpandCardTime`，入口继续默认关闭。已新增并保存 `robExpandCardReplaceRemainDays` 字段引用，默认 `0`、范围 `0..365`；现有 `robExpandCardLimt` 继续只表示待收翻倍能量阈值。

`ForestMultiplierPolicy` 已支持普通卡时间授权、限时卡独立选择、倍率和剩余天数替换阈值、严格 SKU 名称筛选及 `ROB_EXPAND` 可续用类型；`ForestMultiplierWorkflow` 已返回服务端确认的 `confirmedActive`，不再由调用方猜测结束时间。

- [x] **Step 6: 替换旧 N 倍卡入口并接入真实工作流**

已删除 `userobExpandCard()` 和两种卡硬编码，也不再写入 5 分钟假结束时间。新入口通过 `ForestMultiplierWorkflow` 使用 `queryHomePage()` 原始响应、`queryPropList(true)` 强制背包查询和 `usePropBag(candidate.raw, needRefreshHome=false)`，随后重新查询主页确认倍率和结束时间。单点时间使用独立 `ROB_EXPAND` 子任务，范围配置支持普通窗口与跨午夜窗口。

只有同时满足以下条件才允许活力值补兑：

1. `robExpandCard` 已显式开启。
2. 主页明确确认无生效卡。
3. 强制刷新背包 `queryPropList(true)` 后明确缺卡。
4. `vitalityExchange=true`。
5. `vitalityExchangeList` 中用户明确选中了数量大于零、名称同时匹配倍率/收能量/卡类语义的 SKU。
6. `Status.canVitalityExchangeToday(skuId, count)` 明确允许本次数量。

补兑 ACK 后必须再次强制刷新背包；主页确认成功后仅使用 `confirmedActive.endTime` 更新 `robExpandCardEndTime`。`isRenewableProp()` 委托 `ForestMultiplierPolicy.isRenewablePropType()`。普通卡仅在明确的时间授权下使用，限时卡按 `ONLY_LIMIT_TIME` 规则独立处理；不得把单点时间直接传给只支持 `HHmm-HHmm` 的 `TimeUtil.checkInTimeRange()`，也不得自行发明时间窗口。

- [x] **Step 7: 运行定向和森林域回归**

2026-07-29 已先运行 `ForestMultiplier*Test`、`ForestGoldBallWorkflowTest`、`StatusWateredFriendTest` 和 `ForestAutomationPolicyTest`，再运行全部 `antForest.*`。森林域共 16 个测试类、83 项，0 失败、0 错误、0 跳过，Gradle `BUILD SUCCESSFUL`。

## Task 3: 庄园 NPC 生命周期闭环

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmNpcPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmNpcWorkflow.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmNpcPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmNpcWorkflowTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmNpcIntegrationTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarm.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmRpcCall.java`

- [x] **Step 1: 写 NPC 快照和工作流失败测试**

覆盖无 NPC、目标 NPC、其他 NPC、奖励未满、奖励已满、雇佣 ACK 后目标未出现、遣返 ACK 后旧 NPC 仍存在、领取产出后奖励未归零、重雇后目标未出现。

- [x] **Step 2: 实现 `AntFarmNpcPolicy`**

从 `subFarmVO.animals` 提取稳定快照；大表鸽 88、黄金鸡 2888 只作为“允许尝试领取”的阈值，不直接作为“已领取”证据。

- [x] **Step 3: 实现 `AntFarmNpcWorkflow`**

雇佣后回查目标 NPC 已出现；遣返后回查旧 NPC 已消失；领取产出后回查 `npcBizReward` 下降或目标 NPC 消失；重雇后再次确认目标 NPC。任一步未推进立即停止当前链路。

- [x] **Step 4: 保留现有任务领奖工作流**

大表鸽 `farmTaskList` 的 `FINISHED -> RECEIVED` 继续由 `AntFarmRewardWorkflow` 处理，不合并到 NPC 生命周期策略中。

- [x] **Step 5: 运行完整庄园回归**

2026-07-29 已依次运行 `AntFarmNpc*Test`、`AntFarmReward*Test` 和全部 `antFarm.*` 测试。庄园域共 9 个测试类、48 项，0 失败、0 错误、0 跳过，Gradle `BUILD SUCCESSFUL`。

## Task 4: 运动见闻碎片路线筛选

**Files:**

- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRoutePolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRouteWorkflow.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRpcCall.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antSports/AntSports.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRoutePolicyTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRouteWorkflowTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antSports/AntSportsRouteIntegrationTest.kt`

```kotlin
data class RouteKnowledgeEntry(
    val knowledgeId: String,
    val pathId: String,
    val name: String,
    val status: String
)

data class RouteKnowledgeSnapshot(
    val recognized: Boolean,
    val entries: List<RouteKnowledgeEntry>
)
```

`AntSportsRouteWorkflow` 增加 `queryCityKnowledgeDetail: suspend (String) -> String` 依赖。路线发现只允许使用“在线城市 + 未完成城市路线 + `NOT_RECEIVE` 见闻条目”三者的交集，交集为空时返回已识别但无候选，任一容器未知时返回不可识别。

- [x] **Step 1: 写见闻详情解析测试**

覆盖根节点、`data.cityKnowledgeList`、`result.data.cityKnowledgeList`、`NOT_RECEIVE`、已领取、空 `pathId`、重复路线和未知容器；解析结果必须保留 `recognized`，不能把未知结构解释成空列表。

- [x] **Step 2: 写路线选择测试**

只从 `NOT_RECEIVE`、所属城市在线且仍存在于未完成 `cityPathList` 的路线中选择；按世界地图城市顺序和城市路线顺序稳定去重。详情查询失败、结构未知或只有已领取碎片时不切换路线，不采用上游的扩大回退扫描。

- [x] **Step 3: 增加 `queryCityKnowledgeDetail(cityId)` RPC**

RPC 方法固定为 `com.alipay.sportsplay.biz.rpc.walk.queryCityKnowledgeDetail`。请求参数由纯 JSON 构造方法生成，字段固定为 `chInfo=medical_health`、`cityId`、`clientOS=android` 和现有 `features`，并增加协议测试，禁止字符串拼接遗漏转义。

- [x] **Step 4: 接入现有路线发现工作流**

在 `AntSports.kt#createSportsPlayWorkflow()` 注入 `queryCityKnowledgeDetail`，并通过 `AntSportsRouteIntegrationTest` 锁定生产源码确实使用新依赖。保持 `joinPath`、`walkGo` 和 `receiveEvent` 的动作后回查；见闻详情只影响候选选择，不改变步数和领奖终态规则。

- [x] **Step 5: 运行全部运动测试**

先运行 `AntSportsRoute*Test`，再运行全部 `antSports.*` 测试：

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antSports.AntSportsRoute*Test" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain

.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antSports.*" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

完成证据：2026-07-29 运动域共 6 个测试类、38 项，0 失败、0 错误、0 跳过，Gradle `BUILD SUCCESSFUL`。路线发现只返回在线城市、未完成路线与 `NOT_RECEIVE` 见闻条目的交集，未知详情容器不再回退扫描全部路线。

## Task 5: 果园浏览任务安全白名单

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/OrchardBrowseTaskPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/OrchardBrowseTaskWorkflow.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/OrchardBrowseTaskPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antOrchard/OrchardBrowseTaskWorkflowTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchard.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antOrchard/AntOrchardRewardPolicy.kt`

- [x] **Step 1: 写白名单测试**

必须同时满足已识别 `groupId`、`sceneCode`、非空 `targetUrl.source`、浏览语义和无游戏/广告/下单/充值/支付信号。未知来源、嵌套跳转缺失来源和任一风险信号均跳过。

- [x] **Step 2: 写动作后回查测试**

浏览开始和完成 ACK 后重新查询任务；只有状态推进到 `FINISHED/RECEIVED` 或任务从已识别列表消失才确认。计时结束但状态未推进返回重试。

- [x] **Step 3: 实现独立浏览工作流**

不把 `VISIT` 加入全局 `safeCompleteActions`，仅由 `OrchardBrowseTaskPolicy` 对已验证结构放行，避免扩大其他模块和未知访问任务的执行范围。

- [x] **Step 4: 运行完整果园回归**

运行 `OrchardBrowseTask*Test`、`AntOrchardReward*Test` 和全部 `antOrchard.*` 测试。

完成证据：2026-07-29 果园域共 5 个测试类、24 项，0 失败、0 错误、0 跳过，Gradle `BUILD SUCCESSFUL`。`VISIT` 未加入全局安全动作集合，只有 `groupId=12172`、`sceneCode=972`、`taskPlantType=TAOBAO`、浏览语义明确、来源可解析且无风险信号的任务才进入专用工作流；浏览触发和完成后均由已识别任务列表确认终态。

## Task 6: 持久拉起状态展示

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/ui/PersistentLaunchUiStateResolver.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/ui/PersistentLaunchUiStateResolverTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/keepalive/PersistentLaunchPolicy.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/ui/screen/card/ModuleStatusCard.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/ui/screen/content/HomeContent.kt`

- [x] **Step 1: 写账户配置解析测试**

覆盖无账户、配置文件不存在、字段缺失、显式 `false`、显式 `true` 和损坏 JSON。未知状态展示“未确认”，不得按启用展示。

- [x] **Step 2: 实现只读 UI 状态解析器**

读取当前账户配置中的 `BaseModel.allowPersistentForegroundLaunch`，不修改配置、不触发调度、不拉起目标应用。

- [x] **Step 3: 接入 `ModuleStatusCard`**

展示“持久调度前台拉起：已开启/已关闭/未确认”，并明确关闭只影响系统持久调度主动拉起，不影响用户手动打开目标应用后的流程。

- [x] **Step 4: 运行 UI 状态单元测试和编译**

运行 `PersistentLaunchUiStateResolverTest`，随后执行 `compileDebugKotlin`。

完成证据：2026-07-29 `PersistentLaunchUiStateResolverTest` 与 `PersistentLaunchPolicyTest` 通过，`compileDebugKotlin` 为 `BUILD SUCCESSFUL`。UI 直接只读当前账户 `config/<userId>/config_v2.json`，不调用带迁移写入的配置路径；无账户、文件不存在、字段缺失、非布尔值和损坏 JSON 均显示“未确认”。

## Task 7: Web 设置页 clean-room 增强

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/ui/web/SettingsUiContract.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/ui/web/FriendCenterUiProjector.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/ui/web/SettingsUiContractTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/ui/web/FriendCenterUiProjectorTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/ui/WebSettingsActivity.java`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/ui/dto/ModelFieldShowDto.java`
- Modify: `app/src/main/assets/web/semi_index.html`

- [x] **Step 1: 先定义 Android/WebView JSON 合同**

合同只暴露字段类型、允许禁用、默认时间、列表项安全标签、好友分组、关系、能力状态和预览结果。JavaScript 不直接读取内部存储文件。

- [x] **Step 2: 写合同和投影失败测试**

覆盖时间点、时间窗口、禁用值 `-1`、普通列表、计数列表、动态全部好友、分组包含/排除、黑名单、未知能力和旧配置适配。

- [x] **Step 3: 扩展 `HOOK` 只读接口**

新增列表元数据和好友范围预览接口；配置保存继续通过现有统一保存入口，禁止为单个控件增加绕过校验的写接口。

- [x] **Step 4: 重写时间和列表编辑器**

实现时间点/时间窗口结构化编辑、安全标签筛选、搜索、批量选择和计数编辑。所有输入在切换模块、关闭抽屉和保存前统一 flush，避免最后一次输入丢失。

- [x] **Step 5: 接入好友中心预览**

展示动态全部好友、分组、排除、关系和能力筛选后的有效/失效数量；未知能力默认排除，不迁移现有业务模块的配置所有权。

- [ ] **Step 6: 静态和设备验收**

执行 HTML/JavaScript 语法检查、DTO 合同单元测试和 `assembleDebug`。真机 WebView 验证搜索、批量选择、输入落盘、重开页面回显和旧配置兼容。

自动化证据：2026-07-29 使用项目内 Babel 对 `semi_index.html` 的 JSX 完成静态转译检查；设置页合同、好友中心投影和相关好友域测试均包含在全量 492 项单元测试中且无失败；新增好友预览前端合同测试覆盖预览范围、分组、关系、能力、未知能力和 Semi UI Checkbox 事件对象，共 4 项通过；390x844 浏览器视口下 SideSheet、底部操作栏和页面宽度均为 390px，未发现横向溢出；`assembleDebug` 为 `BUILD SUCCESSFUL`。当前 ADB 无连接设备，真机 WebView 验收仍待完成，因此 Step 6 保持未勾选。

## Task 8: 最终自动化与真机验收

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

- [x] **Step 3: 审计危险 RPC 和不可达路径**

扫描 `withdraw`、`cashExchange`、`task.complete`、`task.trigger`、`exchangeYebExpGold`、`finishAdTask`、`recordFarmGame`、`GAME_TRAN_TASK`、`LIGHT_AD_TASK`、下单和充值关键字。每个命中必须属于只读查询、无调用声明、明确阻断分支或默认关闭且本批次不可达的遗留功能。

- [x] **Step 4: 检查工作区质量**

运行 `git diff --check`、UTF-8 BOM 扫描、测试 XML 汇总和 APK 数量/ABI 核对。禁止把 CRLF 提示误判为 `git diff --check` 失败。

- [ ] **Step 5: 真机验收**

验证 API 102 Hook、Binder 跨进程、进程重启、设备重启、应用升级、时间变化、权限变化、森林任务回查、N 倍卡保护性补兑、NPC 状态回查、见闻路线、果园浏览白名单、设置页落盘和持久拉起状态展示。遇到验证码、风控或安全验证立即停止当前业务链路。

自动化证据：2026-07-29 全量 `testDebugUnitTest` 共 105 个测试类、492 项，0 失败、0 错误、0 跳过；`assembleDebug` 成功生成 arm64-v8a、armeabi-v7a、x86、x86_64 和 universal 共 5 个 APK，ABI 与文件名一致。危险 RPC 全仓扫描确认本批未新增调用路径，果园新增的 `WITHDRAW` 仅为阻断关键词；仓库既有命中属于只读预咨询、调试入口、策略阻断或默认关闭的遗留功能。`git diff --check` 返回 0，33 个变更文件均无 UTF-8 BOM。ADB 未连接设备，Step 5 保持未勾选。

## 三、执行顺序

1. 已完成 Task 1：森林通用任务协议、签到闭环、游戏/广告阻断和森林域回归，不重复实施。
2. 已完成 Task 2：金球正数终态、专用被浇水状态、N 倍卡补兑、强制查包、使用后主页回查和服务端结束时间均已闭合。
3. 已完成 Task 3：庄园 NPC 雇佣、遣返、领取和重雇均通过动作后状态回查；大表鸽任务领奖继续由独立工作流处理。
4. 已完成 Task 4：运动见闻详情、待收碎片路线交集、未知容器保护和生产入口接入均已闭合。
5. 已完成 Task 5：果园浏览任务使用专用白名单，不扩大通用 `VISIT`。
6. 已完成 Task 6：持久拉起状态按当前账户显示“已开启、已关闭、未确认”三态。
7. Task 7 已完成合同、实现和自动化验收；真机 WebView 验收待连接设备后执行。
8. Task 8 已完成全量自动化、APK 构建和安全审计；真机验收待连接设备后执行。

每个业务任务严格执行红灯、最小绿灯、模块全量回归。未经用户授权，不执行 `git add`、`git commit` 或 `git push`。
