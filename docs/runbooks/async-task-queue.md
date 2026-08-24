# 异步任务队列 Runbook

负责人：Backend。Dashboard：Grafana `LearnFlow / Async Queue`。关联告警：`LearnFlowQueueBacklogOld`、`LearnFlowQueueDeadLetters`、`LearnFlowExpiredTaskLeases`、`LearnFlowTaskWorkersSaturated`、`LearnFlowQueueMetricsStale`。

## 适用事件

- 计划任务长期停留在 `PENDING` 或 `RUNNING`。
- `FAILED` 任务快速增加。
- Worker 重启后任务未恢复。
- 用户取消后仍产生新的计划。

## 首轮检查

1. 确认 Backend 健康且 `LEARNFLOW_TASKS_ENABLED=true`。
2. 按状态、最早创建时间检查 `async_task`，不得查询或复制 `request_payload` 到工单和聊天工具。
3. 检查 `lease_owner`、`lease_expires_at`、`attempt_count/max_attempts`、`deadline_at` 和 `error_code`。
4. 检查 Backend 数据库连接池、Agent Bulkhead/熔断状态及 Agent 健康。
5. 确认 `learnflow_async_queue_metrics_fresh=1`；为 0 时先按数据库故障处理，不信任其余缓存队列指标。

建议的只读统计：

```sql
SELECT status, task_type, count(*)
FROM async_task
GROUP BY status, task_type
ORDER BY status, task_type;
```

```sql
SELECT id, status, progress, attempt_count, max_attempts,
       lease_owner, lease_expires_at, deadline_at, error_code, created_at
FROM async_task
WHERE status IN ('PENDING', 'RUNNING', 'FAILED')
ORDER BY created_at
LIMIT 100;
```

## 处置

### PENDING 积压

- 如果 Worker 被关闭，恢复配置并滚动重启 Backend。
- 如果 Agent 熔断或连接池饱和，先处理下游故障；不要盲目增加 Worker 并发。
- 如果数据库领取延迟升高，检查 `idx_async_task_claim` 是否存在及查询计划。

### RUNNING 租约过期

正常 Worker 会在下一次领取前自动回收过期租约。不要手工把仍有存活 Worker 执行的任务改回 `PENDING`。默认租约 3 分钟，必须长于单次计划调用预算。

### FAILED / 死信

1. 根据 `error_code` 和受控日志定位原因，不把用户目标或生成内容复制到审计日志。
2. 修复根因后，由管理员调用 `POST /api/admin/tasks/{taskId}/replay`。
3. 重放保留原任务 ID，学习计划通过 `study_plan.source_task_id` 唯一约束保证幂等。
4. 失败 Payload 默认只保留 7 天；清理后接口返回 `410 Gone`，不得从日志反向拼接用户输入。

### 取消

- `PENDING` 任务立即取消并清除 Payload。
- `RUNNING` 任务会记录取消请求并关闭当前 Java→Agent 调用；竞态或供应商清理最迟受整体调用预算限制，持久化前的取消检查会阻止后续业务写入。
- 如果任务已经成功持久化，则返回成功结果，不自动删除计划。

## 灾备演练：积压与全部 Worker 重启

仅在 staging 使用合成用户执行：

1. 记录不可变版本、任务 ID、队列基线和演练开始时间，提交一组可追踪计划任务形成受控积压。
2. 在任务处于 `PENDING/RUNNING` 时滚动到零个可用 Worker 或同时重启全部 Backend 副本；不要删除或手工修改队列表。
3. 验证已接受任务仍存在、readiness/告警符合预期、租约到期后只被一个 Worker 接管。
4. 恢复 Worker 后等待队列年龄回到基线，核对 `source_task_id` 无重复计划，取消任务未产生迟到写入。
5. 记录开始/恢复时间、最老任务年龄、失败/死信数、重复写入检查、告警/Trace 和清理结果。

若出现任务丢失、重复计划、队列无限增长或恢复超过 60 分钟，场景必须失败并保留现场。不得通过人工重建任务伪造恢复成功。

## 回滚

1. 设置 `LEARNFLOW_TASKS_ENABLED=false` 并滚动重启 Backend，停止领取新任务。
2. 前端可暂时切回兼容接口 `POST /api/plan`。
3. 不删除 `async_task` 或 V7 迁移；保留现场用于恢复和审计。
4. 恢复后重新启用 Worker，租约回收器会接管未完成任务。

## 升级条件

以下任一指标持续发生时，按 ADR-0006 评估独立消息中间件：任务领取 P95 超过 1 秒、稳态积压超过 10,000，或队列轮询消耗超过数据库容量预算的 10%。
