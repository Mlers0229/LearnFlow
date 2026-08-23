---
type: db_table
project: LearnFlow
table_name: app_user
status: 已实现
layer: 核心层
priority: 核心
module: backend
storage: Java JPA
entity: com.learnflow.entity.User
primary_key: id
foreign_keys: []
depends_on: []
progress: 100
summary: 用户账号主表，承载用户名、密码哈希、角色、状态和学习水平。
tags:
  - learnflow
  - database
  - user
---

# app_user

用于管理 LearnFlow 的登录账号与角色信息。当前代码中实际表名为 `app_user`，用于避免与数据库关键字 `user` 冲突。

## 关键字段

- `id`
- `username`
- `password_hash`
- `email`
- `role`
- `status`
- `level`
- `created_at`
- `updated_at`

## 关系

- 被 `study_plan.user_id` 依赖
- 被 `exercise_record.user_id` 依赖
- 被 `user_resource_feedback.user_id` 依赖

## 备注

- 注册默认角色为 `student`
- 管理端登录依赖 `role=admin`
