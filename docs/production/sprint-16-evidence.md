# Sprint 16 M4 有界 Replan 与暂停恢复交付证据

- 日期：2026-08-23
- 范围：M4-AGENT-01 暂停/继续收口，M4-AGENT-02 Validator → Replan 有限闭环

## 已实现

- Validator 错误计划最多自动修复两次，每次修复后必须重新验证；不合格计划不能进入 `SAVE`。
- 复用 `ReplanAgent` 对主题覆盖、重复计划和负载漂移进行确定性修复，不新增 Agent 类。
- 使用 `plan_revision`、`validated_revision` 与历史 SHA-256 指纹保证修复后续验、失败恢复和重复计划终止。
- Checkpoint 依次保留修复前计划与失败报告、修复后计划、新验证报告；遥测只记录节点和结果。
- V15 为 `async_task` 增加 `PAUSED/pause_requested_at`，为 Workflow 追加 `RESUMED` 事件。
- 新增用户所有权保护的暂停/继续 API 和计划生成页控件；暂停保留 Payload、进度、尝试预算和 Checkpoint。
- Worker、租约恢复和完成提交均识别暂停；迟到的 Agent 响应不能覆盖暂停或取消终态。
- Agent Checkpoint 首次写入和 Upsert 都受任务状态保护，并只读取 `async_task.id/status` 两列。
- 新增自动 Replan 开关、最多两次配置、OpenAPI 必需路径、ADR 与 Runbook。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Java | 96 tests，0 failures/errors，3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Python | 68 passed；Ruff、Mypy 通过 |
| Frontend | Vitest 6 passed；ESLint 0 errors（24 条既有 warning）；生产构建通过 |
| 有界闭环 | 覆盖一次修复成功、修复后复验中断恢复、重复计划终止、两次耗尽失败 |
| 修复规则 | 覆盖主题覆盖、重复度、负载修复和天数/日期形状不漂移 |
| 暂停恢复 | 覆盖可信身份、Payload 保留、尝试预算、恢复领取、Worker 失败与迟到完成竞态 |
| 数据权限 | V15 契约断言仅授予 Agent `async_task.id/status` 列读取，不授予 Payload 或写权限 |

## 仍未关闭

- 本机无 Docker，V15 真实 PostgreSQL 约束、列级 ACL、`PAUSED/RESUMED` 并发事务和 Checkpoint 首次写入竞态仍需远端 CI/staging 证据。
- 尚未在真实浏览器和运行中的三服务环境执行暂停→继续→完成 E2E。
- Checkpoint 终态定时清理仍等待审计窗口确认，沿用 Sprint 15 的未关闭项。

## 关联资料

- ADR：[`../adr/0012-bounded-validation-repair-and-workflow-pause.md`](../adr/0012-bounded-validation-repair-and-workflow-pause.md)
- Runbook：[`../runbooks/study-plan-workflow-checkpoints.md`](../runbooks/study-plan-workflow-checkpoints.md)
