---
type: db_table
project: LearnFlow
table_name: resource_bank
status: 已实现
layer: 核心层
priority: 核心
module: shared
storage: FastAPI SQLAlchemy + Java JPA
entity: agent-platform/app/db.py::ResourceBank / com.learnflow.entity.ResourceBank
primary_key: id
foreign_keys: []
depends_on: []
progress: 100
summary: 资源库主表，承接用户上传、审核状态、RAG 推荐和管理端运营治理。
tags:
  - learnflow
  - database
  - resource
---

# resource_bank

这是资源推荐和资源管理能力的核心表。FastAPI 侧负责 RAG 加载，Java 侧负责资源上传、审核和查询。

## 关键字段

- `title`
- `url`
- `level`
- `duration_minutes`
- `tags`
- `status`

## 关系

- 被 `user_resource_feedback.resource_bank_id` 依赖
- 未来会被 `study_resource.resource_bank_id` 依赖

## 备注

- 推荐链路默认只使用 `status=ACTIVE` 的资源
