# Sprint 7 M2 持久异步任务交付证据

- 日期：2026-08-21
- 范围：M2-ASYNC-01 队列选型、任务状态机、长计划任务、幂等、重试、死信、进度、取消与超时回收

## 架构决策

- ADR-0006 选择 PostgreSQL 持久任务队列，不在当前容量证据不足时新增 Redis、RabbitMQ 或 Kafka。
- Spring Boot 是任务提交、身份校验、领取和业务写入边界。
- Worker 使用 `FOR UPDATE SKIP LOCKED` 短事务领取任务，领取提交后才调用 Agent，模型调用期间不持有数据库锁。
- 队列采用至少一次投递；`study_plan.source_task_id` 唯一约束使计划持久化对任务 ID 幂等。
- 当前只迁移已经存在且可能超过同步预算的计划生成。批量 Embedding、持久索引和批量评测尚无 M3 实现，因此没有虚假标记为已迁移。

## 数据与状态

Flyway V7 新增 `async_task`，包含：

- `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED` 受约束状态。
- 用户、任务类型和 `Idempotency-Key` 唯一约束，以及请求 SHA-256 指纹冲突校验。
- 进度、尝试次数、下次执行时间、租约归属/过期时间、取消请求和整体截止时间。
- 结果只保存 `STUDY_PLAN + planId` 引用，不复制计划正文。
- 成功/取消立即清除请求 Payload；失败 Payload 最多保留 7 天用于受控重放。
- `FAILED` 且达到最大尝试次数的任务作为死信集合，管理员重放保留原任务 ID。

## API 与前端

- `POST /api/plan/tasks`：从 JWT 安全上下文写入用户身份，返回 `202`、`Location` 和 `Retry-After`。
- `GET /api/tasks/{taskId}`：仅任务所有者可查询进度与结果。
- `DELETE /api/tasks/{taskId}`：待执行任务立即取消；运行中任务关闭当前 Java→Agent 调用并阻止后续业务写入。
- `GET /api/admin/tasks/failed` 与 `POST /api/admin/tasks/{taskId}/replay`：仅管理员可用，重放写入管理审计日志。
- 前端使用每次提交唯一的幂等键，轮询任务进度、支持取消，并在成功后按 `planId` 获取计划。
- 旧 `POST /api/plan` 保留为回滚兼容路径。

## 故障恢复

- Worker 崩溃后，过期租约会被下一实例恢复为待执行或终态。
- 失败采用有限指数退避，达到最大尝试次数或整体截止时间后进入死信。
- 配置包括开关、并发、租约、整体超时、最大尝试次数、重试间隔、死信 Payload 保留期与清理计划。
- 运维步骤见 [`docs/runbooks/async-task-queue.md`](../runbooks/async-task-queue.md)。

## 自动化验证

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 64 tests，0 failures/errors，3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Agent `pytest` | 24 passed |
| Agent Ruff | `app` 与 `tests` 通过 |
| Agent Mypy | 33 个 `app` 源文件通过 |
| Frontend ESLint | 0 errors，24 个既有非阻断 warnings |
| Frontend Vitest | 3 passed |
| Frontend production build | 隔离输出目录构建通过，2851 modules transformed |
| Compose 配置 | YAML 解析通过，任务环境变量存在 |
| PostgreSQL 18 临时实例 | V1～V7 执行通过；任务领取后为 `RUNNING/attempt=1`；Backend 可读写任务表、Agent 无任务表读取权限；幂等索引存在 |

专项测试覆盖：可信用户身份、任务归属查询、幂等键参数冲突、待执行/运行中取消、活动 Agent 调用关闭、有限重试、死信重放、错误摘要脱敏、任务 Worker 编排、任务到计划的幂等写入，以及管理员接口 RBAC。

Testcontainers 用例已扩展为验证 V7 Schema、Backend/Agent 对 `async_task` 的最小权限，以及过期租约只能被一个替代 Worker 领取。本地已使用一次性 PostgreSQL 18 实例验证 V1～V7、ACL 和基础领取 SQL；多连接并发领取仍需由远端 CI 首次执行提供最终证据。

## 尚未关闭的范围

- 批量 Embedding、持久索引和批量评测将在 M3 对应流水线出现后接入任务适配层，因此 M2-ASYNC-01 的复合迁移条目仍保持未完成。
- 当前内存 RAG 索引不适合在多 Agent 副本间通过单个任务重建；M3 改为 PostgreSQL 持久索引后再迁移。
- OpenAPI 必需路径清单已纳入新接口；完整契约抓取由远端 CI 执行。
