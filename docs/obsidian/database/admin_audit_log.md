---
type: db_table
project: LearnFlow
table_name: admin_audit_log
status: 已实现
layer: 扩展层
priority: 扩展
module: backend
storage: Java JPA
entity: com.learnflow.entity.AdminAuditLog
primary_key: id
foreign_keys: []
depends_on: []
progress: 85
summary: 管理端审计日志表，记录资源审核、用户管理和重置密码等操作痕迹。
tags:
  - learnflow
  - database
  - audit
---

# admin_audit_log

管理端的运营动作和治理动作会沉淀到这张表，适合后续扩展操作历史、审计追踪和风险排查。

## 关键字段

- `type`
- `operator`
- `target_type`
- `target_id`
- `detail`
- `created_at`

## 关系

- 当前以业务对象 ID 形式记录目标，不依赖严格外键

## 备注

- 已有实体和服务调用，适合后续补管理端审计日志页面
