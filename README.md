![Sesame-TK](https://socialify.git.ci/1754164240/Sesame-TK/image?description=1&font=Inter&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Light)


## 最近功能更新

| 日期 | 模块 | 功能改动 |
| --- | --- | --- |
| 2026-06-20 | 运动步数 / 家庭捐步 | 调整步数同步逻辑，支持 8 点前通过本地步数读取 hook 覆盖低步数；家庭捐步改为每天只尝试一次，避免失败后第二轮重复执行。 |
| 2026-06-20 | RPC / 安全验证 | RPC 空响应改为结构化失败结果，减少空 JSON 解析异常；识别安全验证响应并提示人工验证。 |
| 2026-06-20 | 能量雨 | 放慢能量雨执行与结算节奏；结算触发验证时只结束本次结算，不再暂停当天能量雨流程。 |
| 2026-06-20 | 森林寻宝 / 芝麻信用 / 家庭请客 | 森林抽抽乐失败任务达到上限后本轮跳过；芝麻信用不可重试任务自动跳过；家庭请客遇并发异常时本轮跳过，避免重复刷失败日志。 |
| 2026-06-19 | 小鸡庄园 | 新增庄园捐步任务，支持捐步前同步运动步数并领取饲料奖励。 |
| 2026-06-19 | 小鸡乐园 | 新增乐园限时活动任务解析与奖励领取能力。 |
| 2026-06-19 | 小鸡家庭 | 调整家庭小鸡帮喂逻辑，关闭第二次家庭帮喂，减少重复执行。 |


> [!TIP]
> ## 授权说明
> 本项目`fork`自[Sesame-TK](https://github.com/Fansirsqi/Sesame-TK)基于`constanline`版[XQuickEnergy](https://github.com/constanline/XQuickEnergy) 与`pansong291`版[XQuickEnergy](https://github.com/pansong291/XQuickEnergy)开发的项目[Sesame-TK](https://github.com/TKaxv-7S/Sesame-TK)  并且在其基础上进行了少量的功能改进与优化。得益于AI大模型的强大能力使得本项目得以延续发展，请自行斟酌考虑使用。


> [!Important]
> ## 鸣谢 感谢各位开发者的辛苦贡献
> ![[贡献列表](https://github.com/Fansirsqi/Sesame-TK/graphs/contributors)](https://contrib.rocks/image?repo=Fansirsqi/Sesame-TK)


