# Sprint 17 M4 学习事件与 Mastery Profile 交付证据

- 日期：2026-08-23
- 范围：M4-LEARN-01

## 已实现

- V16 建立 `knowledge_point`、追加式 `learning_event` 与按算法版本隔离的 `mastery_profile`。
- 学习日开始/完成/延期/重置、练习作答/复习/删除和资源反馈均从可信用户事务写入事件。
- 事件只保存类型、来源引用、数值信号和受限分类摘要，不复制题干、答案、评论、Prompt、Token 或身份原文。
- weighted-v1 使用可解释加权平均；返回得分、置信度、有效权重、样本量、版本、更新时间和最近证据。
- 置信度使用有上限的有效样本函数；资源反馈与复习点击权重为 0，避免行为点击制造虚假掌握。
- 唯一事件键防止重复计分；练习删除追加反向事件；同一用户－知识点以 PostgreSQL transaction advisory lock 串行重算。
- 提供 `GET /api/mastery`、`POST /api/mastery/recompute` 和 `POST /api/exercise-records/{recordId}/review`，身份仅来自 JWT。
- 练习回顾页展示掌握度、置信度、样本量和最近证据，并支持显式重算和幂等复习标记。
- 新增功能开关、固定算法版本、低基数指标、内部 Span、ADR、Runbook、OpenAPI 路径和回滚方式。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Java | 106 tests，0 failures/errors；3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Mastery 算法 | 加权手算、零权重忽略、小样本低置信度、0.95 上限通过 |
| 身份与状态 | JWT 用户 ID、跨用户学习日拒绝、重复状态幂等、事件迁移隐私与 ACL 契约通过 |
| Python | 68 passed，Ruff 与 Mypy 通过 |
| Frontend | Vitest 7 passed；ESLint 0 errors（24 条既有 warning）；生产构建通过 |

## 未关闭

- 本机无 Docker，V16 真实 PostgreSQL 约束、索引、Backend/Agent ACL 和 advisory lock 并发行为等待远端 CI/staging 证据。
- 尚未在运行中的三服务和真实浏览器执行“作答→画像更新→复习→删除→反向重算”E2E。
- M4-LEARN-02 尚未开始；当前掌握度只展示和重算，不影响计划、资源或练习排序。

## 关联资料

- ADR：[`../adr/0013-versioned-learning-events-and-mastery-profile.md`](../adr/0013-versioned-learning-events-and-mastery-profile.md)
- Runbook：[`../runbooks/learning-events-and-mastery.md`](../runbooks/learning-events-and-mastery.md)

