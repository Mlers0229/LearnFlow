---
type: db_table
project: LearnFlow
table_name: study_plan
status: 已实现
layer: 核心层
priority: 核心
module: backend
storage: Java JPA
entity: com.learnflow.entity.StudyPlan
primary_key: id
foreign_keys:
  - user_id -> app_user.id
depends_on:
  - app_user
progress: 100
summary: 学习计划主表，一条记录代表一次完整的学习计划生成结果。
tags:
  - learnflow
  - database
  - plan
---

# study_plan

承接一次计划生成请求的主记录，是计划浏览、历史计划、计划统计的核心入口。

## 关键字段

- `user_id`
- `goal_text`
- `title`
- `duration_weeks`
- `hours_per_day`
- `level`
- `start_date`
- `end_date`
- `status`

## 关系

- `study_plan_day.plan_id -> study_plan.id`

## 备注

- 已按用户隔离
- 支持重命名、状态更新、软删除
