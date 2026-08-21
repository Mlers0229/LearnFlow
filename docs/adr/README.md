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

每份 ADR 必须包含背景、决策、取舍、风险和回滚方式。
