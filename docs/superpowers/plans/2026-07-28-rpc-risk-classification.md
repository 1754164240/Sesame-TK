# RPC 风险分类实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RPC 失败稳定分类为离线、人工验证、频率限制、可重试和未知，避免频率限制激进重开或未知失败被当作成功。

**Architecture:** 新增纯 `RpcFailureClassifier`，由 `RequestManager` 在非空响应返回调用方前进行分类。分类器不持有状态；现有 `RpcRecoveryPolicy` 继续负责离线去重和验证阻断，不整体替换 `RpcBridge`。

**Tech Stack:** Kotlin/JVM、JUnit 4、org.json、Android Gradle Plugin

## Global Constraints

- 仅参考 Sesame-AG 提交 `88932c55` 的错误字段和风控语义，禁止复制或 cherry-pick AGPL 源码。
- 验证类失败必须停止自动重试；频率限制不得触发重开；未知失败不得重置恢复状态或误判成功。
- 空响应继续使用 `EMPTY_RPC_RESPONSE`；现有验证响应继续使用 `RPC_VERIFICATION_REQUIRED`。
- 不改变 `RpcBridge` 接口，不替换 New/Old Bridge。
- 所有 Gradle 命令追加 `--no-build-cache "-Pkotlin.compiler.execution.strategy=in-process" --no-daemon --console=plain`。
- 保留 dirty 工作区，不暂存、不提交、不切换分支。

---

### Task 1: 纯失败分类器

**Files:**
- Create: `app/src/main/java/fansirsqi/xposed/sesame/hook/RpcFailureClassifier.kt`
- Create: `app/src/test/java/fansirsqi/xposed/sesame/hook/RpcFailureClassifierTest.kt`

**Interfaces:**
- Produces: `RpcFailureKind` with `SUCCESS`, `OFFLINE`, `VERIFICATION_REQUIRED`, `FREQUENCY_LIMITED`, `RETRYABLE`, `UNKNOWN`
- Produces: `RpcFailure(code: String, message: String, kind: RpcFailureKind)`
- Produces: `RpcFailureClassifier.classify(raw: String?): RpcFailure`

- [x] **Step 1: 写失败测试**

覆盖成功 JSON、空响应、`I07`/离线模式、`1009`/验证关键词、频繁/稍后再试/`LIMIT`、明确网络超时、失败但无已知信号、畸形 JSON。测试验证优先于频率限制，空/畸形响应属于 `RETRYABLE`，未知失败属于 `UNKNOWN`。

- [x] **Step 2: 运行红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.RpcFailureClassifierTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：因分类器不存在而编译失败。

- [x] **Step 3: 最小实现**

从根对象及 `data/result/resData` 中提取 `resultCode/errorCode/code/retCode/sspErrorCode` 与 `resultDesc/resultView/memo/desc/errorMessage/errorMsg/sspErrorMsg/message`。只在明确成功字段或成功码时返回 `SUCCESS`。

- [x] **Step 4: 运行绿灯**

重复 Step 2 命令，预期通过。

### Task 2: 接入 RequestManager 恢复决策

**Files:**
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/RequestManager.kt`
- Modify: `app/src/main/java/fansirsqi/xposed/sesame/hook/RpcRecoveryPolicy.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/hook/RpcRecoveryPolicyTest.kt`
- Modify: `app/src/test/java/fansirsqi/xposed/sesame/hook/RequestManagerTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `RpcFailureClassifier.classify`。
- Produces: `RpcRecoveryPolicy.onFrequencyLimited()` and `onUnknownFailure()`，二者均不调度重开。

- [x] **Step 1: 写恢复策略失败测试**

测试频率限制和未知失败不增加网络重开计数、不产生 `SCHEDULE_REOPEN`；验证仍只通知一次；后续明确成功才清空阻断状态。

- [x] **Step 2: 运行红灯**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.RpcRecoveryPolicyTest" `
  --tests "fansirsqi.xposed.sesame.hook.RequestManagerTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：因新恢复入口尚不存在或行为不符合而失败。

- [x] **Step 3: 最小接入**

`executeRpc` 对非空响应分类：`VERIFICATION_REQUIRED` 调用既有人工验证阻断；`OFFLINE` 计入网络恢复；`FREQUENCY_LIMITED` 和 `UNKNOWN` 保留响应并不调用 `onSuccess`；`RETRYABLE` 走现有网络失败计数；只有 `SUCCESS` 清空恢复状态。

- [x] **Step 4: RPC 模块回归**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "fansirsqi.xposed.sesame.hook.RpcFailureClassifierTest" `
  --tests "fansirsqi.xposed.sesame.hook.RpcRecoveryPolicyTest" `
  --tests "fansirsqi.xposed.sesame.hook.RequestManagerTest" `
  --no-build-cache `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  --no-daemon --console=plain
```

预期：全部通过，Bridge 接口没有变化。
