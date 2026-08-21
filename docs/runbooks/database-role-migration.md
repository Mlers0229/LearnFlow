# PostgreSQL 最小权限迁移 Runbook

## 新数据库

`docker-compose.yml` 会在空数据卷初始化时执行 `ops/postgres/init-roles.sh`。必须为 Owner、Migrator、Backend、Agent 配置四个不同密码。Backend 使用独立 Flyway 凭证迁移，再以运行账号启动；Flyway V5 分配表权限。

## 已有数据卷

PostgreSQL 官方镜像不会对已有数据卷重新执行初始化脚本。升级前先备份，然后由数据库管理员执行：

1. 创建 `learnflow_migrator`、`learnflow_backend`、`learnflow_agent` 登录角色。
2. 将 Schema CREATE/USAGE 授予 migrator，只把 USAGE 授予两个运行角色。
3. 使用 migrator 运行 Flyway V2～V6。
4. 以管理员身份执行 `ops/postgres/apply-runtime-grants.sql`。
5. 分别用两个运行角色验证权限，再切换服务凭证。

Agent 必须满足：可读资源与反馈、可写/清理 `agent_call_log`，不能读取 `app_user`，不能修改资源或执行 DDL。验证可使用 `has_table_privilege` 和 `has_schema_privilege`。

## 回滚

角色和 GRANT 都不修改业务数据。发生权限遗漏时先补充单项 GRANT；紧急回滚可暂时恢复旧运行账号。不要删除角色，直至确认没有活动连接和对象所有权。
