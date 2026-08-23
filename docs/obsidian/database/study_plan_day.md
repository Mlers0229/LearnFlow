---
type: db_table
project: LearnFlow
table_name: study_plan_day
status: 已实现
layer: 核心层
priority: 核心
module: backend
storage: Java JPA
entity: com.learnflow.entity.StudyPlanDay
primary_key: id
foreign_keys:
  - plan_id -> study_plan.id
depends_on:
  - study_plan
progress: 100
summary: 每日计划表，记录计划中的单日主题、任务列表与执行状态。
tags:
  - learnflow
  - database
  - plan-day
---

# study_plan_day

用于承接每日学习主题、任务列表、学习日期和进度状态，是历史计划页和执行面板的核心数据来源。

## 关键字段

- `plan_id`
- `day_index`
- `date`
- `title`
- `tasks_json`
- `status`

## 关系

- 归属于 `study_plan`
- 被 `exercise_record.plan_day_id` 依赖
- 未来会被 `study_resource.plan_day_id` 依赖

## 备注

- `tasks_json` 目前使用 JSON 字符串存储任务列表
