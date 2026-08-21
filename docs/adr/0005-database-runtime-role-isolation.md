# ADR-0005：数据库运行角色与 Schema 隔离

- 状态：Accepted
- 日期：2026-08-21

## 背景

Backend 与 Agent 原先共享同一个高权限数据库账号。任何一个服务发生注入、凭证泄漏或配置错误时，都可能读取或修改全部业务数据；同时 Flyway 与应用运行时也没有权限边界。

## 决策

生产环境使用三个固定职责角色：

- `learnflow_migrator`：执行 Flyway，拥有 Schema DDL 权限。
- `learnflow_backend`：拥有业务表所需的 SELECT、INSERT、UPDATE、DELETE 与序列权限，不拥有 DDL。
- `learnflow_agent`：只读 `resource_bank`、`user_resource_feedback`，并可对 `agent_call_log` 执行 SELECT、INSERT、DELETE。

当前继续使用同一个 `public` Schema，通过 PostgreSQL ACL 隔离。原因是 Agent 当前只映射三张表，独立 Schema 会引入跨 Schema 外键、迁移顺序和部署复杂度，却不会消除 Agent 对上述业务数据的必要读取。

## 重新评估条件

出现以下任一情况时，将 Agent 自有表迁移到独立 Schema：

- Agent 自有表超过 5 张，或引入状态 Checkpoint/队列表。
- Agent 需要独立备份、保留或扩缩容策略。
- 安全审计要求数据库对象级所有权完全隔离。

## 回滚

紧急情况下可以暂时把 Backend/Agent 指向原兼容账号恢复服务，但必须记录安全例外与到期时间。角色创建不会删除数据；回滚应用配置后可保留角色，待确认无依赖再由数据库管理员撤销。
