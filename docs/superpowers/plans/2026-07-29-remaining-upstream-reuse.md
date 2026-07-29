# 上游剩余安全复用实施计划

> **状态说明（2026-07-29）：** 本计划中的森林巡护和余额宝体验金安全子集均已完成。后续缺口与执行顺序以 `docs/superpowers/plans/2026-07-29-sesame-ag-recent-commit-replan.md` 为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking。当前仓库禁止使用子代理、禁止未经授权暂存或提交。

**Goal:** 补齐 Sesame-AG `205a8318e3` 的森林巡护筛选/派遣排序，以及 `3fe2394580` 后续演进出的余额宝体验金安全子集。

**Architecture:** 所有决策先落在可独立单测的纯 Kotlin 策略或工作流中，再由现有 `AntForest`、`AntMember` 调用。森林只复用常驻动物过滤、图鉴缺片优先和库存/收益排序；余额宝只允许查询、签到和待使用券处理，并要求动作后回查，不复用泛化任务完成、广告、购买、充值、付费兑换或资产提现。

**Tech Stack:** Kotlin、Java、`org.json`、Kotlin Coroutines、JUnit 4、Gradle。

## Global Constraints

- 仅支持 libxposed API 102。
- 新开关默认关闭。
- 广播、RPC ACK 和动作成功响应均不代表业务终态。
- 余额宝不得实现或调用 `task.complete`、`task.trigger`、`exchangeYebExpGold`、购买、充值、广告和提现链路。
- 森林巡护数据未知时保留当前地图，不扩大执行范围。
- 严格 TDD；Gradle 命令固定追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`。
- 文件使用 UTF-8 无 BOM，代码注释使用中文。
- 未经用户授权，不暂存、不创建 Git 提交。

---

## Task 1: 森林巡护筛选与派遣排序

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/ForestPatrolPolicy.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antForest/ForestPatrolPolicyTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antForest/AntForestRpcCall.java`

- [x] **Step 1: 写失败测试**

覆盖常驻在线动物过滤、限定/活动动物排除、普通动物缺片判断、旧到新未完成地图选择、全部完成后最新地图回退、零库存伙伴跳过、库存优先及同库存预计收益优先。

- [x] **Step 2: 运行测试确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antForest.ForestPatrolPolicyTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 3: 实现最小纯策略**

策略输出稳定的 `patrolId` 或伙伴数组索引；空数组、未知字段和无有效候选均返回 `null`。

- [x] **Step 4: 接入现有森林流程**

地图切换前按 `patrolId` 查询图鉴；切换成功后重新查询当前巡护状态。伙伴派遣只消费策略选择结果，不改变现有默认关闭开关。

- [x] **Step 5: 运行森林定向测试**

运行 `ForestPatrolPolicyTest` 与全部 `antForest.*` 测试。

## Task 2: 余额宝体验金安全子集

**Files:**

- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/YebExpGoldPolicy.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/YebExpGoldWorkflow.kt`
- Create: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/YebExpGoldRpcCall.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/YebExpGoldPolicyTest.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/task/antMember/YebExpGoldWorkflowTest.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/task/antMember/AntMember.kt`

- [x] **Step 1: 写策略失败测试**

覆盖今日待签到识别、未知签到结构收窄、待使用券计数、嵌套成功码解析和动作响应不能直接视为终态。

- [x] **Step 2: 写工作流失败测试**

覆盖签到后必须回查为非待签到、券处理后待使用数量必须减少、动作成功但回查失败返回重试、无待处理项目不执行动作。

- [x] **Step 3: 运行测试确认红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.task.antMember.YebExpGoldPolicyTest" `
  --tests "fansirsqi.xposed.sesame.task.antMember.YebExpGoldWorkflowTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

- [x] **Step 4: 实现策略、工作流和 RPC 边界**

RPC 只包含主页查询、签到、券查询和全部待使用券处理。工作流通过注入函数测试，生产接入不持久化未经回查的完成标记。

- [x] **Step 5: 增加默认关闭开关并接入会员并行任务**

字段键固定为 `yebExpGold`，默认值为 `false`。日志区分“确认完成”“无待处理”和“需要后续重试”。

- [x] **Step 6: 运行会员定向测试**

运行 `YebExpGold*Test` 与全部 `antMember.*` 测试。

## Task 3: 全量验收

- [ ] **Step 1: 运行全部单元测试**
- [ ] **Step 2: 构建 Debug APK**
- [ ] **Step 3: 重新审计危险 RPC**
- [ ] **Step 4: 运行 `git diff --check` 和 UTF-8 BOM 扫描**
