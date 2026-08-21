# ADR-0003：数据库 Schema 唯一管理权

- 状态：Accepted
- 日期：2026-08-21
- 决策人：LearnFlow 维护者

## 背景

当前 Spring JPA 使用 `ddl-auto: update`，FastAPI 在启动时调用 SQLAlchemy `create_all()`。同一 Schema 被两个 ORM 隐式修改，无法审查变更，也无法可靠回滚。

## 决策

1. Flyway 是 PostgreSQL 业务 Schema 的唯一变更机制。
2. Java 实体和 SQLAlchemy 模型只负责映射；生产环境 JPA 使用 `validate`，FastAPI 不再执行 `create_all()`。
3. 首次接管前先从实际数据库导出 Schema，并与七个 JPA 实体、三个 SQLAlchemy 映射逐项比对。
4. `V1__baseline.sql` 必须能在空数据库创建当前完整 Schema。已有非空数据库在备份和校验后，将版本标记为 1，不重复执行建表语句。
5. 接管分两次发布：版本 A 引入迁移并保持 ORM 行为不变；完成 staging 基线演练后，版本 B 启用 Flyway、JPA `validate` 并移除 `create_all()`。
6. 后续迁移采用 Expand/Contract，必须包含兼容窗口、失败处理和数据回填策略。

## 取舍

- 两阶段接管比直接切换慢，但能避免已有环境被重复建表或因实际 Schema 漂移而无法启动。
- SQL 迁移增加维护工作，但提供可审查、可复现的发布证据。

## 风险

- 实际数据库可能包含 ORM 未声明的列、索引或约束；必须以实际 Schema 为准完成差异报告。
- `baseline-on-migrate` 误用于空库会跳过 V1，因此只允许在一次性已有库接管命令中显式启用，不能作为长期默认配置。

## 回滚

迁移前创建可恢复备份。仅追加结构的迁移优先通过应用回滚；破坏性变更必须等兼容窗口结束，并提供前向修复脚本。Flyway 历史不得手工删除或改写。
