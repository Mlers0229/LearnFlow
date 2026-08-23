---
type: db_table
project: LearnFlow
table_name: exercise_record
status: 已实现
layer: 扩展层
priority: 核心
module: backend
storage: Java JPA
entity: com.learnflow.entity.ExerciseRecord
primary_key: id
foreign_keys:
  - user_id -> app_user.id
  - plan_day_id -> study_plan_day.id
depends_on:
  - app_user
  - study_plan_day
progress: 100
summary: 练习题与用户作答记录表，用于练习回顾、AI 评测沉淀和后续学习分析。
tags:
  - learnflow
  - database
  - exercise
---

# exercise_record

用于保存 Tutor 生成的题目、参考答案、解释以及用户实际作答，是练习回顾页的数据核心。

## 关键字段

- `user_id`
- `plan_day_id`
- `question`
- `answer`
- `explanation`
- `user_answer`
- `created_at`

## 关系

- 指向 `app_user`
- 指向 `study_plan_day`

## 备注

- 当前更偏原始存档，后续可增加得分、题型、知识点等字段
