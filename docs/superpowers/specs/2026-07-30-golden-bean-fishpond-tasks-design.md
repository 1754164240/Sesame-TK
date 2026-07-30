# 金豆夺宝与福气鱼池任务补全设计

## 目标

在现有“农场”模型中增加默认关闭的“金豆夺宝”开关，并依据
`serve-debug/webhook.db` 中的真实请求时序实现可复查的任务闭环。同时补全福气鱼池
“逛好物得钓竿”任务链路，解决部分任务调用后未推进的问题。

所有写操作必须经过服务端状态复查。单次 RPC 返回成功只表示请求被受理，不能直接视为
任务已经完成或奖励已经领取。

## 范围

### 金豆夺宝

包含：

- 查询金豆夺宝首页，读取签到、财运签、任务列表和待领取奖励。
- 完成“抽今日财运签”，随后同步并确认任务进入 `RECEIVED`。
- 对订阅类、游戏类和金豆乐园类待办任务调用任务完成接口。
- 完成任务后同步确认任务进入 `FINISHED`，再领取奖励。
- 领取奖励后再次同步，确认任务进入 `RECEIVED`。
- 支付和余额宝类任务只在服务端已经返回 `FINISHED` 时领取，不主动执行。
- 对其他已经 `FINISHED` 的任务领取奖励。

不包含：

- 自动发起线上支付、到店支付或余额宝资金操作。
- 自动兑换肥料为金豆。
- 对未知动作类型直接调用任务完成接口。
- 自动兑换金豆商城商品。

### 福气鱼池

包含：

- 识别带 `adBizNo` 的浏览广告任务。
- 调用鱼池广告通知接口。
- 查询广告任务配置，并优先使用响应中的 `duration` 作为等待时长。
- 广告内容拉取仅作为尽力而为的曝光步骤；抓包中该接口返回广告流量错误，但后续任务仍然
  成功，因此其失败不能中断任务闭环。
- 等待完成后调用 `com.alipay.antiep.finishTask`。
- 同步鱼池首页并重新查询任务列表，以服务端返回 `FINISHED` 或 `RECEIVED` 为准。
- `FINISHED` 状态继续领取奖励，`RECEIVED` 状态视为闭环完成。

不扩大现有鱼池其他任务的执行范围。

## 抓包依据

### 金豆夺宝

进入页面：

- `com.alipay.goldenbean.index`
- 请求固定业务参数为 `bizType=MASTER`、`source=babafarm`、
  `version=20260723.01`。

财运签：

1. `com.alipay.goldenbean.fortuneDraw`
2. `com.alipay.goldenbean.sync`
3. 同步后 `FORTUNE_DRAW` 从 `TODO` 进入 `RECEIVED`

金豆乐园任务：

1. `com.alipay.antieptask.finishTaskantorchard`
2. `com.alipay.goldenbean.sync`
3. 同步后 `JINDOULEYUAN_TRIGGER` 从 `TODO` 进入 `FINISHED`
4. `com.alipay.antieptask.receiveTaskAwardantorchard`
5. `com.alipay.goldenbean.sync`
6. 同步后任务进入 `RECEIVED`

### 福气鱼池

“逛好物得钓竿”的抓包顺序：

1. `com.alipay.antfishpond.listTask`
2. `com.alipay.antfishpond.fishpondAdNotice`
3. `com.alipay.adtask.biz.mobilegw.service.applayer.query`
4. `com.alipay.adexchange.ad.facade.xlightPlugin`
5. 等待任务要求的 15 秒
6. `com.alipay.antiep.finishTask`
7. `com.alipay.antfishpond.fishpondSyncIndex`
8. `com.alipay.antfishpond.listTask`
9. 任务由 `TODO` 进入 `RECEIVED`

第 4 步在本次抓包中返回 `retCode=217` 和“广告请求错误”，但第 6 至第 9 步仍然完成，
因此该步骤只能作为非阻断的尽力请求。

## 架构

### 金豆 RPC 网关

新增独立的金豆夺宝 RPC 封装，负责构造以下请求：

- 首页查询。
- 状态同步。
- 财运签。
- 完成任务。
- 领取任务奖励。

RPC 封装只构造协议，不包含任务决策。

### 金豆任务策略

新增不依赖 Android 环境的纯策略组件，将任务归一化后输出以下决策：

- `FORTUNE_DRAW`：财运签任务，调用专用接口。
- `COMPLETE`：订阅、游戏或金豆乐园类 `TODO` 任务。
- `CLAIM`：所有 `FINISHED` 或 `TO_RECEIVE` 任务。
- `WAIT`：支付、余额宝类 `TODO` 任务，以及已经 `RECEIVED` 的任务。
- `SKIP`：肥料兑换和未知任务。

分类优先使用 `actionType`，并结合任务 ID、标题做保守兜底。支付、余额宝和肥料兑换的
拒绝规则优先于允许规则，避免标题或动作字段变化时误操作。

### 金豆工作流

工作流从首页快照开始，最多执行有限轮闭环：

1. 遍历任务并执行策略决策。
2. 每次完成任务或领取奖励后调用同步接口。
3. 重新读取服务端任务状态。
4. 只有状态推进时进入下一轮。
5. 连续一轮无状态变化、响应结构未知或达到轮数上限时停止。

任务去重键由场景码、任务 ID、决策和当前状态组成，防止同一轮重复提交。

### 农场接入

在 `AntOrchard` 的设置中增加：

- `goldenBeanTreasure`：`金豆夺宝 | 任务与领奖`，默认 `false`。

`AntOrchard.runSuspend()` 保持现有农场和鱼池调度顺序，并在开关开启时运行金豆工作流。
金豆工作流异常不能阻断农场或鱼池流程。

### 鱼池广告任务

扩展现有 `FishPondGateway` 和 `FishPondWorkflow`：

- 广告配置查询从任务 `targetUrl` 的 `renderConfigKey` 提取 `spaceCode`；无法解析时使用
  抓包确认的鱼池默认广告位。
- 等待时长优先取广告配置响应中的 `resultData.duration`，其次取任务浮球配置或描述，
  最后使用现有 15 秒默认值。
- 广告内容拉取失败只记录，不阻断广告通知、等待和完成任务。
- 完成接口返回成功后必须同步并重新查询任务状态；未推进时保留后续重试。

## 错误处理与安全边界

- 新开关默认关闭，升级后不会新增 RPC 行为。
- 空响应、解析失败和未知状态都不视为成功。
- 支付、余额宝处于 `TODO` 时永远不调用完成接口。
- 订阅和游戏任务只有在任务类型满足允许规则时才执行。
- 肥料兑换任务永远跳过。
- RPC 成功但状态未推进时记录日志并等待下次调度，不写入每日完成标记。
- 单个任务失败不阻断同一模块中的其他安全任务。
- 工作流设置有限轮数，避免服务端状态不变时循环请求。

## 测试

金豆策略测试覆盖：

- 支付和余额宝 `TODO` 返回 `WAIT`，`FINISHED` 返回 `CLAIM`。
- 订阅、游戏和金豆乐园 `TODO` 返回 `COMPLETE`。
- 财运签 `TODO` 返回专用决策。
- 肥料兑换和未知任务返回 `SKIP`。
- 所有可领取状态进入 `CLAIM`。

金豆工作流测试覆盖：

- 财运签调用专用接口并在同步后确认 `RECEIVED`。
- 普通任务严格按“完成、同步、领取、同步”的顺序执行。
- 状态未推进时不领取且返回可重试。
- 单个任务失败后继续处理其他任务。
- 有限轮数和去重规则能够阻止重复提交。

鱼池测试覆盖：

- 广告任务按“通知、配置查询、尽力曝光、动态等待、完成、同步、复查”顺序执行。
- 广告内容拉取失败不阻断任务完成。
- 广告配置中的 `duration` 优先于任务描述。
- 完成接口成功但复查仍为 `TODO` 时不能视为完成。

接入测试覆盖：

- “农场”设置包含默认关闭的 `goldenBeanTreasure`。
- 开关关闭时不运行金豆工作流。
- 金豆工作流异常不阻断农场和鱼池。

最终运行新增专项测试、现有鱼池与农场相关测试，并执行 Debug APK 构建。

## 完成标准

- 金豆夺宝开关位于“农场”设置且默认关闭。
- 财运签、订阅、游戏和金豆乐园任务能够按服务端状态完成闭环。
- 支付和余额宝任务只领取已完成奖励，不主动执行。
- 肥料兑换和未知任务不会被误操作。
- 鱼池广告任务按抓包时序执行并通过任务列表复查。
- 新增测试、相关回归测试和 Debug 构建全部通过。
