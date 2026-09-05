# 安全验证与日志异常修复计划

> 执行方式：按任务逐项实施，每项先复现、再修改、最后验证。2026-09-05 已完成下述实施范围，待用户实机验证。

## 实施记录

### 分支更正

- 用户确认目标为 `codex/dev`，以 `59b34f37` 为基础迁移修复；Launcher 恢复入口适配 `ModernXposedRuntime`，保留 libxposed 102 与开发分支新增功能。
- 开发分支原有 `ChouChouLeSchedulePolicyTest.kt` 引用不存在的 `ChouChouLeScheduleAction/ChouChouLeSchedulePolicy`，完整测试编译失败。在临时隔离且随后原样恢复该文件后，164 项其余测试通过，APK 构建成功；不能将此表述为完整测试通过。
- 原提交 `d22ff368` 位于错误的 `codex/pre-libxposed-102` 分支，通过新增回退提交撤销，不强推改写历史；本地最终保留在 `codex/dev`。

- [x] 核实 webhook.db 共 73 条请求，会员新版入口样本 61/71、对应首页 64；捐蛋列表 1、详情 3/10、成功捐赠 6。未复制认证数据到测试。
- [x] 新旧 Bridge 限流前后检查离线；普通成功响应不再清除验证状态；取消任务保留待退出 Job，恢复先等待旧任务退出。
- [x] 验证状态按账号保存在宿主私有存储，重启保留；通知和支付宝主页确认框提供手动恢复入口，恢复动作校验账户及一次性令牌并串行执行。
- [x] 森林、庄园、运动主流程占用调度槽直至结束，超时改为 10 分钟；独立长期子任务不纳入主 Job 等待。离线不启动下一轮及下一次定时流程。
- [x] 捐蛋及收蛋响应缺失 memo 时正常处理失败；验证时退出庄园流程；不误写成功状态。
- [x] 保护罩按轮次与道具 ID 去重；包括领取/兑换中新出现并实际尝试的道具；保留用户阈值，获取成功后刷新背包。
- [x] 兼容抓包证实的会员 externalGameCenter 入口及首页参数，保留旧入口；新版页面访问不冒充旧奖励完成。
- [x] 按用户补充，会员游戏按账号、当天、任务标识在执行前持久化尝试记录；成功与尝试分开，失败当天也不重复；跨日先清理旧状态，只保留当天记录。
- [x] 最终 Gradle `:app:testDebugUnitTest :app:assembleDebug` 通过，109 项测试、0 失败、0 错误，生成各 ABI 及通用 APK。
- [x] 校验本次 23 个源码、测试、计划文件 UTF-8 无 BOM；用户原有 serve-debug 文件改动保留。
- [ ] 实机检查安全验证暂停及人工恢复、重启去重、跨日清理、实际会员奖励和调度时长。

### 决策与限制

- 当前 ONE/ALL 只决定捐赠活动数量，没有标的选项。自营 SOLDBY 明确跳过并继续普通活动，未猜测 targetId。代价是自营自动捐赠暂不可用，需明确标的配置后再接入已知协议。
- 验证暂停不会自动重启恢复。已发送请求无法撤回；限流后检查与反射调用之间仍存在极短竞争窗口，不宣称绝对零在途请求。
- 取消后同步浏览等待可能尚未结束，恢复会等待旧业务退出；实机需检查耗时。部分业务内部仍捕获取消并记录失败，不能承诺所有连带日志立即消失。
- 青春特权 3000 分别来自不同轮次，抓包没有该接口样本；未猜协议修复。其他业务失败与蹲点空收取保留现有处理。
- 本机 JDK 的 Unix 域临时路径导致 Gradle 回环连接失败；构建命令临时设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp` 后通过，未修改项目 JVM 配置。
- 当前源码分支为 `codex/pre-libxposed-102`；日志版本名不足以唯一识别原安装包提交。本次未提交、推送或安装 APK。

以下为原计划及验收清单，实施结果以本节为准。

**目标：** 收到安全验证后可靠停止自动请求，减少无效重复操作，并修复已确认的响应解析与业务协议问题。

**架构：** 沿用 RequestManager 的统一拦截和 RpcRecoveryPolicy 的状态管理；在实际 RPC 发送边界与任务循环补齐停止检查。业务响应兼容修复留在各自模块，不重构无关任务。

**技术栈：** Kotlin、Java、Android、协程、JUnit、Gradle。

**需求依据：** 本次会话提供的两份 2026-09-05 日志及用户要求。日志记录 3 次原始 1009 响应，不能推断服务端风控规则或实际弹窗次数。

## 全局约束

- 文件保存为 UTF-8 无 BOM；代码注释及提交信息使用中文。
- 不自动完成或绕过安全验证，不把重启应用视为验证成功。
- 不承诺修复后绝不出现支付宝验证；程序可控制的是停止行为、调度和接口兼容性。
- 抓包样本删除认证凭据及个人信息，保留业务字段结构和编码层级。
- 修改前核对安装包与当前源码版本，日志中的 0.9.9-debug 不能唯一确定提交。

## 第一阶段：现有证据足够的修复

### 1. 验证阻断与任务停止

文件：
- `app/src/main/java/fansirsqi/xposed/sesame/hook/RequestManager.kt`
- `app/src/main/java/fansirsqi/xposed/sesame/hook/RpcRecoveryPolicy.kt`
- `app/src/main/java/fansirsqi/xposed/sesame/hook/rpc/bridge/NewRpcBridge.java`
- `app/src/main/java/fansirsqi/xposed/sesame/hook/ApplicationHook.kt`
- `app/src/main/java/fansirsqi/xposed/sesame/task/TaskRunner.kt`
- 现有 `RequestManagerTest.kt`、`RpcRecoveryPolicyTest.kt`、`TaskRunnerPolicyTest.kt`。

- [ ] 用受控并发复现：请求 A 等待限流，请求 B 返回 1009，释放 A 后检查是否仍发送。当前 Bridge 在等待前检查离线，等待后直接调用 RPC，需验证该窗口。
- [ ] 在限流等待后、重试前检查阻断状态；已经发出的请求不能声称可撤回。验收为阻断之后尚未发送的等待请求不再调用底层接口。
- [ ] 测试成功回包晚于 1009 时不能清除验证阻断，且不会安排自动重启恢复。
- [ ] 核对 Bridge 初始化、进程重启与用户恢复流程；明确验证暂停状态的账户作用域及恢复入口，再实现重建后保留暂停的策略。
- [ ] 森林、庄园、海洋、运动、会员、农场在收到验证结果后结束当前任务循环；保留清理逻辑，避免把取消当成业务异常。
- [ ] 日志区分原始服务端验证与本地拦截；单次阻断仅提示一次，保留汇总计数。

### 2. 捐蛋响应解析兜底

文件：`app/src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarm.kt`；新增同目录测试包中的 `AntFarmDonationResponseTest.kt`。

- [ ] 使用日志中的 `{"success":false,"resultCode":"RPC_VERIFICATION_REQUIRED","resultDesc":"触发安全验证，请人工验证后继续"}` 建立回归用例，覆盖缺失 memo、空响应包装和正常成功响应。
- [ ] 在 handleDonation、performDonation、harvestProduce 中先判断响应结果；失败信息使用可选字段及 resultDesc 回退，验证失败直接退出。
- [ ] 验收：不再出现 No value for memo；失败不写入捐赠成功状态，不调用后续捐赠接口。

### 3. 保护罩无效重复尝试

文件：`app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`；新增 `AntForestShieldRetryPolicyTest.kt`。

- [ ] 核对 90 次尝试中实际发送的领取、兑换请求次数，区分函数进入日志和真正网络请求。
- [ ] 同一轮中记录无可用道具、领取无收益、兑换无收益，避免每个能量球重复进入同一获取流程；背包刷新出现可用道具后允许重试。
- [ ] 保留用户设置的 2359 阈值，不擅自改变配置；建议用户根据实际需求降低阈值。
- [ ] 验收：无可用道具时每轮不会持续重复领取与兑换；背包更新或进入下一轮后可正常重新判断。

### 4. 调度并发核对

文件：`app/src/main/java/fansirsqi/xposed/sesame/task/TaskRunner.kt`、`ModelTask.kt`、`TaskRunnerPolicy.kt` 与现有调度测试。

- [ ] 复现后台任务启动即释放调度槽的行为，统计活跃任务和在途 RPC；不能用日志中的并发数 3 代替实际请求并发。
- [ ] 将后台任务完成信号纳入任务并发约束；长期蹲点子任务与当轮主流程分别管理，避免长期占用槽位或重复执行。
- [ ] 验收：普通主流程不超过配置并发；已运行任务不会在第二轮重复启动；验证暂停后不启动新任务；后台蹲点不会阻塞整轮结束。

## 第二阶段：补抓样本后修复协议

### 5. 会员限时游戏入口

文件：`task/antMember/MemberTaskProtocol.kt`、`AntMember.kt`、`AntMemberRpcCall.java` 和 `MemberTaskProtocolTest.kt`，均位于现有主源码或测试包中。

- [ ] 获取 `com.alipay.amic.biz.rpc.game.h5.GameCenterQueryFacade.queryGameEntranceInfo` 的请求与完整响应各一份，优先采用触发解析失败的样本。
- [ ] 保留 actionUrl、外层包装及 URL 编码结构；当前解析依赖内层 url、chInfo、tab、channelTaskPassThrough 中的 sceneId/taskId。
- [ ] 将脱敏样本加入解析测试，对照正常页面实际结构修复；没有活动入口与缺失必要参数分别记录，禁止编造任务参数。
- [ ] 验收：有效入口可解析，正常无活动不报解析异常，格式错误仍有可定位且脱敏的诊断。

### 6. 自营项目捐蛋

文件：`task/antFarm/AntFarm.kt`、`AntFarmRpcCall.java` 和捐蛋协议测试。

- [ ] 获取 `com.alipay.antfarm.listActivityInfo` 完整请求/响应，以及同一自营项目官方页面正常操作产生的详情查询和 `com.alipay.antfarm.donation` 请求/响应。
- [ ] 优先使用已有成功抓包；只有用户原本就打算捐赠时才抓实际捐赠，不为排查额外消耗鸡蛋。
- [ ] 根据实际样本确定标的物字段、来源和项目关联规则；未取得样本前仅做明确失败提示，不猜字段名、不默认选择捐赠标的。
- [ ] 验收：普通项目参数保持兼容，自营项目参数与官方样本一致；218 不标记成功且不进行无效重试。

### 7. 青春特权与其他非核心失败

- [ ] 第一阶段后若仍出现 3000，补抓 `alipay.membertangram.biz.rpc.student.queryCheckInModel` 请求/响应，并记录同时间官方页面能否正常打开。
- [ ] 服务端同样失败则停止当轮重复尝试；仅模块失败时再对比参数和上下文。
- [ ] CAMP_TRIGGER_ERROR 且 retryable=false 保持停止；蹲点空收取和超远未来记录暂作为诊断数据，无新增证据不改成熟时间或强制重试。

## 验证与交付

- [ ] 每项先运行对应回归测试，再做最小修改并复测。
- [ ] 第一阶段集成后执行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`；环境阻塞如实记录，不能宣称通过。
- [ ] 实机先暂停自动任务、人工完成验证，确认普通使用状态；随后只启用必要模块，逐项恢复，记录启动和验证时间。
- [ ] 核对原始 1009 数量、本地阻断计数、阻断后新发请求、保护罩实际请求和业务成功状态。不能仅用调度器“成功”计数判断业务成功。
- [ ] 交付代码差异、测试结果、实机观察结果以及仍需服务端样本确认的事项。
