# Flyway 基线接管计划

- 状态：空库与已有非空 Schema 接管演练均已通过
- 对应任务：M1-DATA-01
- 约束：当前 `spring.flyway.enabled=false`，不得在核验前改为 `true`

## 当前模型清单

Spring JPA 管理的业务表：

- `app_user`
- `study_plan`
- `study_plan_day`
- `exercise_record`
- `resource_bank`
- `user_resource_feedback`
- `admin_audit_log`

SQLAlchemy 还映射 `agent_call_log`，并对 `resource_bank`、`user_resource_feedback` 建立轻量映射。实际数据库可能包含 ORM 未声明的索引、默认值或历史列，最终基线以实际 Schema 为准。

## 接管步骤

1. 在 staging 克隆现有数据库，确认备份和恢复可用。
2. 使用只读账号导出 Schema：`pg_dump --schema-only --no-owner --no-privileges`。
3. 导出表、列、约束、索引、序列和扩展清单，与 JPA/SQLAlchemy 模型生成差异报告。
4. 明确每个差异是保留、补充约束、清理历史结构还是实体映射缺失；不得直接丢弃未知列。
5. 编写幂等目标为“仅用于空库”的 `V1__baseline.sql`，并在临时空库验证可从零构建。
6. 在已有 staging 库上执行一次性 baseline-at-version=1，只写入 Flyway 历史，不重复执行 V1。
7. 将 staging 应用切换为 Flyway enabled、JPA `validate`、`show-sql=false`，同时移除 FastAPI `create_all()`，运行完整冒烟与契约测试。
8. 至少观察一个发布窗口后，按同样流程接管生产库；`baseline-on-migrate` 不写入长期配置。

## 验证项

- 空库经 Flyway 后所有表、序列、外键、唯一约束、检查约束和索引完整。
- 已有库接管前后 Schema 与数据行数不发生非预期变化。
- Spring Boot 使用 `validate` 可以启动。
- FastAPI 在没有建表权限时可以启动并完成读写所需功能。
- 旧应用与迁移后的 Schema 至少在一个兼容窗口内共存。

## 失败与回滚

- 任何差异未解释、备份未验证或 staging 演练失败时停止切换。
- V1 尚未用于生产前可以通过修正 V1 重做空库验证；一旦任一共享环境记录了 V1，就不得修改已发布迁移。
- 应用切换失败时回滚应用配置；数据库只接受经过评审的前向修复，不删除 Flyway 历史。

## 2026-08-21 演练证据

- 在工作区隔离的 PostgreSQL 18.1 临时实例中，先由 Hibernate 生成当前 ORM Schema 并通过 `pg_dump --schema-only` 取得快照。
- 空库执行 `V1__baseline.sql` 与 `V2__add_security_and_agent_tables.sql` 后，Flyway 到达 v2，Hibernate `validate` 启动成功。
- 非空 ORM Schema 使用一次性 `baseline-on-migrate=true` 标记 v1，再执行 V2，Hibernate `validate` 启动成功。
- 演练实例已停止。正式接管前仍须对目标数据库执行备份、Schema 差异报告和 staging 恢复验证。
