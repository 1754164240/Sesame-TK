![Sesame-TK](https://socialify.git.ci/1754164240/Sesame-TK/image?description=1&font=Inter&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Light)


## 最近功能更新

| 日期 | 模块 | 功能改动 |
| --- | --- | --- |
| 2026-07-28 | libxposed / 构建系统 | 模块入口和 Hook 运行时迁移至 libxposed 102，仅保留现代 API 入口；移除 API 82/100、旧版 Xposed 兼容层和 `xposed_init`；模块最低框架 API 调整为 101，目标 API 调整为 102。 |
| 2026-07-28 | 任务调度 / 运动步数 / 蚂蚁森林 / RPC Hook | 配置重载和任务超时时会取消并等待实际业务 Job 退出，避免旧任务与新一轮重叠；阻止森林、庄园和运动后台任务在第二轮重复排队；运动步数改用家庭捐步接口同步并二次查询确认，兼容 `resultCode=200`；修正 `positionRequest` 位于 `requestData[0]` 时的 Token 提取；合并森林蹲点异常日志和持久化写入，减少重复任务与日志刷屏。 |
| 2026-07-27 | RPC / 任务调度 / 运动步数 / 蚂蚁森林 / 小鸡乐园 / 小鸡家庭 | 增加 RPC 熔断、单次恢复和安全验证人工暂停机制；按任务执行策略区分完成、后台运行、超时与离线跳过；运动步数改为按方法签名定位同步入口；森林抽抽乐未知游戏任务等待抓包并限制失败重试；修复乐园权益空响应误报、能量蹲点重复任务与日志风暴，以及家庭请客和动物派遣的幂等业务码误报。 |
| 2026-07-27 | 会员任务 | 适配新版会员任务墙协议，支持广告任务和普通浏览任务的领取、等待、执行与结算；增加会员积分累计进度复查、黑名单过滤和单任务异常隔离，移除已失效的旧版会员任务接口。 |
| 2026-06-20 | 应用图标 / 滑块验证服务 / 运动步数 / 家庭捐步 / RPC / 安全验证 / 能量雨 / 森林寻宝 / 芝麻信用 / 家庭请客 | 重新生成主应用图标；去除自动滑块验证服务；调整步数同步与家庭捐步逻辑；RPC 空响应改为结构化失败结果并识别安全验证；放慢能量雨执行与结算节奏，结算触发验证时只结束本次结算；森林抽抽乐、芝麻信用和家庭请客异常场景自动跳过。 |
| 2026-06-19 | 小鸡庄园 / 小鸡乐园 / 小鸡家庭 | 新增庄园捐步任务和乐园限时活动奖励领取；调整家庭小鸡帮喂逻辑，关闭第二次家庭帮喂，减少重复执行。 |
| 2026-04-12 | 芝麻信用 / 小鸡家庭 / 运动步数 / 小鸡庄园 / 家庭喂食 | 增强多任务异常处理，芝麻信用不可重试任务自动跳过；家庭任务与运动步数流程增加风控和异常保护；优先使用家庭指定饲料列表处理帮喂和庄园助手任务，减少误用饲料。 |
| 2026-04-11 | 小鸡庄园休息 / 启动体验 | 优化 20 点后小鸡睡觉任务优先级和执行流程，提升夜间休息任务稳定性；去除启动免责声明弹窗，简化应用进入流程。 |
| 2026-04-09 | 小鸡庄园雇佣 | 雇佣成功后继续扫描页面，避免成功处理一个对象后漏扫后续可处理项。 |
| 2026-04-02 | 蚂蚁森林 / 能量雨 | 将能量雨执行时机前移到蚂蚁森林主流程更靠前的位置。 |
| 2026-04-01 | 品牌与界面 / README / 发布流程 | 项目标识调整为 Sesame-VN；去除界面水印和查看异常日志验证；更新 README 展示内容与 Android 发布 workflow 配置。 |

## 运行与构建要求

- 模块仅支持现代 libxposed，`minApiVersion=101`，`targetApiVersion=102`，不再支持 API 82/100 旧入口。
- Android 应用要求 Android 8.0（API 26）及以上；项目使用 `compileSdk=37.0`、`targetSdk=36` 和 Build Tools `37.0.0`。
- libxposed 依赖使用 `app/libs` 中的 `api-102.0.0.aar`、`interface-102.0.0.aar` 和 `service-102.0.0.aar`。
- 构建环境使用 JDK 17 及以上、Gradle 9.5.0 和 Android Gradle Plugin 9.3.0。

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

> [!TIP]
> ## 授权说明
> 本项目`fork`自[Sesame-TK](https://github.com/Fansirsqi/Sesame-TK)基于`constanline`版[XQuickEnergy](https://github.com/constanline/XQuickEnergy) 与`pansong291`版[XQuickEnergy](https://github.com/pansong291/XQuickEnergy)开发的项目[Sesame-TK](https://github.com/TKaxv-7S/Sesame-TK)  并且在其基础上进行了少量的功能改进与优化。得益于AI大模型的强大能力使得本项目得以延续发展，请自行斟酌考虑使用。


> [!Important]
> ## 鸣谢 感谢各位开发者的辛苦贡献
> <a href="https://github.com/1754164240/Sesame-VN/graphs/contributors">
>   <img src="https://contrib.rocks/image?repo=1754164240/Sesame-VN" alt="贡献列表" />
> </a>
