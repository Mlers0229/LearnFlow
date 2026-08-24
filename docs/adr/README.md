# LearnFlow 架构决策记录

本目录保存会影响多个组件、生产安全或数据兼容性的架构决策。决策一旦被接受，后续实现应遵循对应约束；需要改变时，应新增 ADR 说明替代关系，而不是静默修改历史结论。

| ADR | 状态 | 主题 |
| --- | --- | --- |
| [0001](0001-system-boundaries-and-production-platform.md) | Accepted | 系统边界与初期生产平台 |
| [0002](0002-authentication-and-session-strategy.md) | Accepted | 身份认证与会话策略 |
| [0003](0003-database-schema-ownership.md) | Accepted | 数据库 Schema 唯一管理权 |
| [0004](0004-infrastructure-adoption-gates.md) | Accepted | Redis、队列与 Kubernetes 引入门槛 |
| [0005](0005-database-runtime-role-isolation.md) | Accepted | 数据库运行时角色隔离 |
| [0006](0006-postgresql-durable-task-queue.md) | Accepted | PostgreSQL 持久异步任务队列 |
| [0007](0007-resource-ingestion-ownership-and-storage.md) | Accepted | 资源摄取所有权、版本与原件存储 |
| [0008](0008-pgvector-dense-retrieval-and-embedding-versions.md) | Accepted | pgvector Dense Retrieval 与 Embedding 版本 |
| [0009](0009-postgresql-sparse-retrieval-and-rrf.md) | Accepted | PostgreSQL Sparse Retrieval 与 RRF |
| [0010](0010-evidence-grounded-cross-encoder-reranking.md) | Accepted | 基于证据的 Cross Encoder 重排 |
| [0011](0011-durable-study-plan-workflow-checkpoints.md) | Accepted | 持久学习计划 Workflow Checkpoint |
| [0012](0012-bounded-validation-repair-and-workflow-pause.md) | Accepted | 有界验证修复与 Workflow 暂停 |
| [0013](0013-versioned-learning-events-and-mastery-profile.md) | Accepted | 版本化学习事件与 Mastery Profile |
| [0014](0014-deterministic-mastery-driven-adaptive-policy.md) | Accepted | 确定性掌握度自适应策略 |
| [0015](0015-platform-neutral-production-runtime-contract.md) | Accepted | 平台无关生产运行与发布契约 |
| [0016](0016-production-capacity-model-and-gate.md) | Accepted | 版本化生产容量模型与失败关闭发布门禁 |
| [0017](0017-fail-closed-disaster-recovery-drills.md) | Accepted | 失败关闭的灾难恢复演练与发布门禁 |
| [0018](0018-evidence-bound-release-and-data-governance.md) | Accepted | 证据绑定发布与数据治理门禁 |
| [0019](0019-durable-data-export-and-account-erasure.md) | Accepted | 持久数据导出与账户擦除 |

每份 ADR 必须包含背景、决策、取舍、风险和回滚方式。
