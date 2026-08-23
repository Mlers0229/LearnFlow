# 学习计划 Workflow Checkpoint Runbook

## 适用告警与现象

- `learnflow.plan.workflow.transitions` 中 `failed` 持续增加。
- 计划任务长期停留在 `RUNNING` 或 workflow 长期停留在 `READY_TO_SAVE`。
- 日志出现 `workflow request fingerprint mismatch`、`workflow state checksum mismatch` 或 `unsupported workflow schema version`。
- 用户取消任务后仍观察到 Agent 节点继续执行。
- Validator 错误持续出现、Replan 次数达到 2，或日志出现自动修复重复版本。
- 用户暂停后任务仍被 Worker 领取，或暂停任务被迟到响应标记为成功。

禁止在排障日志、工单或聊天中复制 `state_json`。只查询 ID、状态、节点、序号、时间和错误码。

## 快速定位

1. 使用 Request ID/Trace ID 定位 Backend 任务 Span 与 Agent 节点 Span。
2. 使用任务 ID 查询非敏感元数据：

```sql
SELECT t.id, t.status AS task_status, t.attempt_count, t.lease_expires_at,
       t.pause_requested_at,
       w.status AS workflow_status, w.current_node, w.checkpoint_sequence,
       w.last_error_code, w.updated_at, w.expires_at
FROM async_task t
LEFT JOIN agent_workflow w ON w.workflow_id = t.id
WHERE t.id = :task_id;
```

3. 查询节点事件时不要选择状态正文：

```sql
SELECT sequence, node, outcome, node_attempt, created_at
FROM agent_workflow_checkpoint
WHERE workflow_id = :task_id
ORDER BY sequence;
```

## 处置

### 节点临时失败

- 让现有任务队列按共享截止时间和最大尝试次数自动重试。
- 确认下一次调用只增加失败节点的 `node_attempt`，已完成节点不应再次出现 `STARTED`。
- 进入 `FAILED` 后只能通过既有管理员死信重放入口恢复，不手工改状态或序号。

### 长期 READY_TO_SAVE

- 检查 Backend Worker 租约、数据库连接池和 `study_plan.source_task_id`。
- Worker 重领任务后，Agent 会返回已保存的 Ready 快照；Backend 保存依靠 `source_task_id` 去重。
- 若已有业务计划但任务未完成，重放应复用原计划并补齐 `SAVE/COMPLETED`，不得删除计划后重试。

### 取消

- 通过用户任务取消 API 或管理员受控流程取消，不直接修改 workflow 表。
- 预期结果：活动 Agent HTTP 调用被取消，`async_task` 最终为 `CANCELLED`，workflow 追加 `CANCELLED` 事件且不会进入业务保存。

### Validator → Replan

- 按顺序检查 `VALIDATE/COMPLETED → REPLAN/STARTED → REPLAN/COMPLETED → VALIDATE/COMPLETED`，不要选择 `state_json`。
- `REPLAN` 的 `node_attempt` 最大为 2；`SKIPPED` 的 attempt 为 0。
- `WORKFLOWVALIDATIONEXHAUSTED` 表示两次修复后仍不合格；`WORKFLOWREPAIRSTALLED` 表示修复重复历史计划。两者都不得人工改为 `READY_TO_SAVE`。
- 根据受控 Checkpoint 复盘修复规则和 Validator 版本，修复代码或数据后通过既有死信重放入口恢复。

### 暂停与继续

- 只使用用户暂停/继续 API，不直接更新 `async_task` 或 `agent_workflow`。
- 暂停预期：任务为 `PAUSED`、Payload 保留、租约为空、Workflow 追加 `PAUSED`；活动 Agent 调用被关闭。
- 继续预期：Workflow 先追加 `RESUMED`，任务再成为可领取的 `PENDING`，下一次执行从最近完整 Checkpoint 恢复。
- 如果暂停发生在首个 Checkpoint 之前，Workflow 可以尚不存在；Agent 的首次写入会被任务状态阻止，继续后再正常创建。
- 若发现 `PAUSED` 任务存在 `source_task_id` 对应计划，不要删除。该行由幂等键保护，继续后会复用并完成提交。

### 指纹、Checksum 或版本错误

- 立即停止重放；这类错误表示串单、损坏或不兼容部署，不允许跳过校验。
- 核对应用版本、Flyway 历史和任务 ID，不读取或传播状态正文。
- 若为版本发布问题，回滚应用并保留 V14 数据供复盘。

## 回滚

1. 设置 `LEARNFLOW_WORKFLOW_CHECKPOINTS_ENABLED=false`。
2. 滚动重启 Agent；Backend 无需降级数据库 Schema。
3. 验证新计划请求走旧无状态路径且正常返回。
4. 保留 V14 表，不执行 `DROP TABLE`；修复后重新启用即可继续处理尚未终结且兼容 v1 的工作流。

仅回滚自动修复时设置 `LEARNFLOW_WORKFLOW_AUTO_REPLAN_ENABLED=false`。此模式下 Validator 错误会明确失败，不会保存不合格计划。V15 表和 `PAUSED` 行必须保留，恢复新版后再继续。

## 保留期

终态 workflow 默认过期时间为 7 天。清理任务只删除已终结且超过 `expires_at` 的父记录，由外键级联清理事件；执行清理前应确认审计与故障调查窗口已经结束。
