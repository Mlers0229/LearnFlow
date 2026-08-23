# Sprint 18 M4 掌握度驱动计划交付证据

- 日期：2026-08-23
- 范围：M4-LEARN-02

## 已实现

- `adaptive-v1` 在 Backend 依据掌握度、置信度和样本量生成确定性、有界策略。
- 证据不足、对照组、功能关闭和 Agent 失败均回退到原固定策略，不产生过度确定结论。
- V17 建立稳定实验分组、隐私最小化决策证据、90 天保留入口及 Agent 零权限边界。
- 掌握度影响计划/主题难度、复习间隔、弱项优先级及每日时间比例。
- Hybrid RAG 使用目标难度进行过滤和重排，并在推荐理由中解释调整依据。
- Tutor 根据弱项选择回忆举例、应用纠错或迁移综合题型；规则降级路径行为一致。
- Replan 在首个未完成日插入弱项纠正任务、分配每日时间比例并更新难度。
- 计划响应持久化策略版本、分组、应用状态和原因；生成页、历史页及练习卡提供可解释提示。
- 新增功能开关、稳定实验键、低基数指标、Span 属性、ADR、Runbook 和配置示例。

## 自动化结果

| 门禁 | 结果 |
| --- | --- |
| Backend `mvn -q test` | 110 tests，0 failures，0 errors，3 skipped（本机无 Docker） |
| Agent `pytest` | 72 passed，1 warning |
| Agent `ruff check` | 通过 |
| Agent `mypy` | 46 个源文件，0 issues |
| Frontend `vitest` | 4 个测试文件、10 tests 全部通过 |
| Frontend `npm run build` | 通过（仅保留既有 chunk-size 提示） |
| Frontend `eslint` | 0 errors，24 warnings（既有告警） |
| `git diff --check` | 通过 |

以上结果来自本次实际命令输出；跳过项不作为真实 PostgreSQL 运行证据。

## 未关闭

- 本机无 Docker，V17 真实 PostgreSQL 约束、索引、Backend/Agent ACL 和清理任务等待远端 CI/staging 证据。
- 尚未在真实浏览器完成“产生学习事件→画像变化→新计划/资源/练习/Replan 改变”的三服务 E2E。
- 稳定 CONTROL/ADAPTIVE 分组和行为评测入口已建立，但真实学习效果需要足够线上样本；在此之前 M4-LEARN-02 的实验效果子项保持未完成。

## 关联资料

- ADR：[`../adr/0014-deterministic-mastery-driven-adaptive-policy.md`](../adr/0014-deterministic-mastery-driven-adaptive-policy.md)
- Runbook：[`../runbooks/adaptive-learning.md`](../runbooks/adaptive-learning.md)

