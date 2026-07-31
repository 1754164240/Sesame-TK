# 会员任务黑名单误伤修复实施计划

> **执行要求：** 按 TDD 顺序实施，每一步完成后运行对应测试。用户未授权提交，所有改动保持未提交状态。

**目标：** 防止会员任务“逛一逛”“玩一玩”等标题被其他业务黑名单误伤，并由会员模块自己的代码黑名单阻止资金动作。

**方案：** 会员任务不读取全局或用户黑名单，在 `MemberTaskSafetyPolicy` 中维护独立的硬编码资金动作关键词。结构合法且未命中专用黑名单的游戏、浏览任务继续执行。

**技术栈：** Kotlin、JUnit 4、Gradle。

## 全局约束

- 文件使用 UTF-8 编码且不带 BOM。
- 新增代码注释使用中文。
- 不修改全局内置黑名单的现有匹配行为。
- 不创建 Git 提交。

### 任务 1：建立回归测试

**文件：**

- 修改：`app/src/test/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskSafetyPolicyTest.kt`
- 修改：`app/src/test/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskWorkflowTest.kt`

- [ ] 增加资金动作被代码黑名单阻止的测试。
- [ ] 增加“玩一玩游戏、逛一逛”仍进入执行流程的测试。
- [ ] 运行定向测试，确认因缺少会员专用黑名单而失败。

### 任务 2：实施最小修复

**文件：**

- 修改：`app/src/main/java/fansirsqi/xposed/sesame/task/antMember/MemberTaskSafetyPolicy.kt`

- [ ] 增加会员专用的硬编码资金动作关键词。
- [ ] 在任务类型分派前优先应用专用黑名单。
- [ ] 运行定向测试，确认通过。

### 任务 3：验证

- [ ] 运行全部单元测试。
- [ ] 执行 `git diff --check`。
- [ ] 校验改动文件为 UTF-8 且无 BOM。
- [ ] 检查 Git 差异并保持未提交。
