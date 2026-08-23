# ADR-0012：计划验证采用有界自动修复，任务暂停与 Workflow 协调

- 状态：Accepted
- 日期：2026-08-23
- 关联：M4-AGENT-01、M4-AGENT-02、ADR-0006、ADR-0011

## 背景

Sprint 15 已将计划生成拆成可恢复节点，但 `REPLAN` 仍固定记录为 `SKIPPED`。Validator 即使返回错误，计划仍可能进入 `READY_TO_SAVE`。同时用户只有取消入口，无法在保留 Payload、尝试预算和 Checkpoint 的前提下暂时停止长任务。

自动修复不能形成无限模型循环，也不能改变计划天数、日期或已完成业务状态。暂停也不能复用取消语义，因为取消会清除 Payload 并把 Workflow 置为不可恢复终态。

## 决策

1. Validator 失败后进入 `REPLAN`，自动修复最多两次；配置值被硬限制在 `0..2`。
2. 自动修复复用现有 `ReplanAgent`，只处理当前 Validator 的明确错误码：主题覆盖、重复计划和日负载。延期驱动的用户重规划继续使用原有独立路径。
3. 每个计划版本记录 SHA-256 指纹、`plan_revision` 和 `validated_revision`。修复重复历史版本时立即失败，避免无变化循环。
4. 修复不得改变天数、`day_index`、日期；单日任务数不得超过 4，预计时长不得超过用户预算的 1.5 倍。
5. 每轮 `REPLAN/STARTED` 保存修复前计划和失败报告，`REPLAN/COMPLETED` 保存修复后计划，随后 `VALIDATE/COMPLETED` 保存新报告。正文只存在受控 Checkpoint，不进入指标或 Trace。
6. 两次后仍不合格时 Workflow 明确进入 `FAILED`，不得进入 Backend `SAVE`。
7. `async_task` 新增 `PAUSED` 和 `pause_requested_at`。暂停保留请求 Payload、进度、Checkpoint 和剩余尝试预算；继续时回到 `PENDING` 并刷新整体截止时间。
8. Backend 负责 `PAUSED/RESUMED` Workflow 事件。Agent 的 Checkpoint Upsert 不允许覆盖已暂停、已取消任务。
9. Agent 仅新增对 `async_task.id/status` 的列级读取权限，不获得 Payload 或任何写权限。
10. Worker 在 Agent 返回后、业务保存前和业务保存后再次检查暂停；迟到完成不能把 `PAUSED/CANCELLED` 覆盖为 `SUCCEEDED`。

## 回滚

- 设置 `LEARNFLOW_WORKFLOW_AUTO_REPLAN_ENABLED=false` 可停止自动修复；不合格计划仍会明确失败，不会恢复为不安全保存。
- `LEARNFLOW_WORKFLOW_MAX_REPLAN_ATTEMPTS` 可调低但最大只能为 2。
- 应用回滚时保留 V15 Schema；旧 Worker 只领取 `PENDING`，不会领取 `PAUSED`。恢复新版后可继续处理暂停任务。
- 如暂停接口异常，可从前端隐藏入口并回滚应用；禁止直接修改任务或 Checkpoint 表。

## 取舍

- 确定性修复覆盖当前可解释 Validator 错误，质量提升可复现，但不会处理未知语义缺陷；未知或无变化修复进入明确失败。
- 暂停后的极窄保存竞态可能已产生由 `source_task_id` 保护的不可见幂等计划行；任务不会被标为成功，继续后会复用该行完成提交。
- 暂不引入 LangGraph、消息中间件或新的 Agent 类。
