# Sprint 15 M4 状态化计划工作流交付证据

- 日期：2026-08-22
- 范围：M4-AGENT-01 显式状态、PostgreSQL Checkpoint、失败续跑、取消与幂等 SAVE 基座

## 已实现

- 新增 `StudyPlanState` v1，固定 Goal、Schedule、Plan、Validate、Replan、Save 节点、状态、完成节点和节点尝试次数。
- 通过 workflow ID、规范化请求指纹和状态 Checksum 阻止串单、损坏状态和未知版本恢复。
- 通过 Flyway V14 建立当前 workflow 与追加式 checkpoint 历史，包含约束、索引、7 天保留元数据和 Agent 精确权限。
- Agent 在节点开始、完成、跳过和失败时写 Checkpoint；任务重试只执行最后未完成节点。
- Sprint 15 将 Replan 建为显式节点但记录为 `SKIPPED`，不提前宣称 Validator 自动修复闭环完成。
- Agent 到 `READY_TO_SAVE` 后由 Spring 保存业务计划；`source_task_id` 保证重复执行不重复写入计划和学习日。
- Spring 在业务保存后追加 `SAVE/COMPLETED`，并在任务取消时同步 workflow `CANCELLED` 终态。
- 异步计划不再在 Agent 失败时保存无 Checkpoint 的本地示例计划；同步兼容路径仍保留原降级行为。
- 新增低基数节点转换指标，不将目标、计划、验证报告或 Checkpoint 正文写入遥测。
- 新增 `LEARNFLOW_WORKFLOW_CHECKPOINTS_ENABLED` 回滚开关；关闭后恢复旧无状态编排，V14 Schema 保持前向兼容。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Java | 90 tests，0 failures/errors，3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Python | 63 passed；Ruff、Mypy 通过 |
| Frontend | ESLint 0 errors（24 条既有 warning）、Vitest 5 passed、生产构建通过 |
| 状态恢复 | Goal/Schedule 完成后 Plan 首次失败，重试只重跑 Plan，节点尝试次数为 2 |
| 幂等与回放 | Ready workflow 重复调用直接返回 Checkpoint 计划；Spring SAVE 继续使用 `source_task_id` |
| 安全边界 | 请求指纹、未知版本、Checksum、Agent 表权限和禁止业务表写权限具备代码或契约断言 |
| 回滚 | 关闭功能开关后带 workflow ID 的请求恢复无状态路径且不写 Checkpoint |

## 仍未关闭

- 本机没有 Docker，V14 真实 PostgreSQL JSONB、外键、并发 Upsert、Agent ACL 和终态 CTE 仍需远端 CI/staging 首次运行证据。
- 尚未提供用户侧显式暂停/继续入口；当前已支持失败后恢复、取消和幂等重放，因此 M4-AGENT-01 的复合暂停项保持未完成。
- Validator 失败后的最多两次 Replan、版本化前后快照和人工确认/明确失败策略留到 Sprint 16。
- Checkpoint 终态定时清理尚未自动化；V14 已提供 `expires_at` 与索引，需在受控环境确认审计窗口后接入清理任务。

## 关联资料

- ADR：[`../adr/0011-durable-study-plan-workflow-checkpoints.md`](../adr/0011-durable-study-plan-workflow-checkpoints.md)
- Runbook：[`../runbooks/study-plan-workflow-checkpoints.md`](../runbooks/study-plan-workflow-checkpoints.md)
