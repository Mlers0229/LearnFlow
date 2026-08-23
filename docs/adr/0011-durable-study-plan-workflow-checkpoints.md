# ADR-0011：学习计划工作流使用 PostgreSQL Checkpoint

- 状态：Accepted
- 日期：2026-08-22
- 关联：M4-AGENT-01、ADR-0006

## 背景

计划生成已经由 Backend 的 PostgreSQL 持久任务队列执行，但 FastAPI 内部仍以一次请求串行运行 Goal、Schedule、Plan 和 Validate。进程重启或模型调用失败后，任务只能从头执行，既重复消耗模型预算，也无法证明恢复点和节点输入输出。

同时，`study_plan` 与 `study_plan_day` 是确定性业务数据，必须继续由 Spring Backend 持有写权限，不能为了 Agent 状态恢复扩大 `learnflow_agent` 的业务表权限。

## 决策

1. `async_task.id` 同时作为 `workflow_id`，由 Backend 通过版本化请求契约传给 Agent。
2. 状态 Schema 首版固定为 `StudyPlanState` v1；未知版本、请求指纹不一致或 Checksum 不一致时拒绝恢复。
3. 节点固定为 `GOAL → SCHEDULE → PLAN → VALIDATE → REPLAN → SAVE`。
4. Agent 在节点开始和完成/失败后写入当前快照，并向 `agent_workflow_checkpoint` 追加不可变事件。
5. 已完成节点不会重跑；失败任务由现有有限重试队列重新调用 Agent，并从最后完成节点继续。
6. Sprint 15 只建立 `REPLAN` 显式节点并记录 `SKIPPED`；Validator → Replan 的最多两次自动修复闭环由 Sprint 16 启用。
7. `SAVE` 是 Backend 所有的确定性节点。Agent 到达 `READY_TO_SAVE` 后停止；Backend 依靠 `study_plan.source_task_id` 幂等保存，再原子记录 `SAVE/COMPLETED`。
8. 取消以 `async_task` 为事实源，并同步写入 workflow 终态；活动 HTTP 调用继续使用既有连接取消传播。
9. Checkpoint 默认保留 7 天，仅 `learnflow_agent`、`learnflow_backend` 和 migrator 拥有必要权限；状态正文不得复制到日志、指标或 Trace。

## 节点契约

| 节点 | 输入 | 输出 | 执行方 | 重试语义 |
| --- | --- | --- | --- | --- |
| GOAL | `GoalRequest` | `GoalPlanStructure` | Agent | 未完成时可重跑 |
| SCHEDULE | Goal + Blueprint | Phase/Week | Agent | 纯规则，可安全重跑 |
| PLAN | Goal + Blueprint + Schedule | Draft Plan | Agent | 共享整体截止时间，按任务有限重试 |
| VALIDATE | Draft Plan + Goal | Validation Report | Agent | 纯规则，可安全重跑 |
| REPLAN | Validation State | 修复后的 Plan | Agent | Sprint 15 仅建节点；Sprint 16 限制最多两次 |
| SAVE | Ready State + Task ID | `study_plan.id` | Backend | `source_task_id` 唯一索引保证幂等 |

节点调用继承计划场景的整体截止时间、取消信号、Bulkhead 和熔断策略。`node_attempts` 只记录有限次数，不包含用户文本。

## 数据模型

- `agent_workflow`：每个任务一行当前状态、Schema 版本、请求指纹、Checksum、当前节点、终态和过期时间。
- `agent_workflow_checkpoint`：以 `(workflow_id, sequence)` 为主键的追加式节点历史。
- V14 由 Flyway 创建表、约束、索引和精确授权；ORM 不创建或修改表。

## 安全与隐私

- Agent 仍不能写 `study_plan`、`study_plan_day`、`async_task` 或用户表。
- Workflow 状态是恢复所必需的受限业务数据，不是日志；观测系统只记录节点、结果和次数等低基数元数据。
- 请求指纹排除随机 workflow ID，但覆盖所有学习目标参数，防止同一 ID 被不同内容复用。
- Checksum 在加载前验证，损坏状态不能继续执行。

## 回滚

设置 `LEARNFLOW_WORKFLOW_CHECKPOINTS_ENABLED=false` 并重启 Agent，可恢复原无状态编排路径。V14 表保持不删除，以兼容旧应用和后续重新启用；Backend 的终态更新在不存在 workflow 行时为安全空操作。

回滚不会恢复异步 Agent 故障时的本地示例计划写入。持久任务必须失败并按有限策略重试，避免产生没有 Checkpoint 的伪成功数据。

## 取舍

- 复用 PostgreSQL，避免在尚无吞吐证据时引入 Redis、Kafka 或 LangGraph Checkpointer。
- Checkpoint 会短期保存恢复所需的计划状态，因此必须执行权限、保留期和脱敏约束。
- Sprint 15 不宣称完成 Validator 自动修复，也不提供用户侧手动暂停 UI。
