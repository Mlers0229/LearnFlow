---
type: db_table
project: LearnFlow
table_name: user_resource_feedback
status: 已实现
layer: 扩展层
priority: 核心
module: backend
storage: Java JPA
entity: com.learnflow.entity.UserResourceFeedback
primary_key: id
foreign_keys:
  - user_id -> app_user.id
  - resource_bank_id -> resource_bank.id
depends_on:
  - app_user
  - resource_bank
progress: 100
summary: 用户对资源的评分、评论和无效举报记录，是资源质量闭环的基础表。
tags:
  - learnflow
  - database
  - feedback
---

# user_resource_feedback

这张表把用户主观反馈转成资源质量统计，为资源管理页和 Dashboard 提供聚合基础。

## 关键字段

- `user_id`
- `resource_bank_id`
- `rating`
- `comment`
- `reported_invalid`
- `created_at`

## 关系

- 指向 `app_user`
- 指向 `resource_bank`

## 备注

- 后端已基于此表提供资源质量统计聚合接口
