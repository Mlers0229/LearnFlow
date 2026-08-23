---
type: db_table
project: LearnFlow
table_name: study_resource
status: 规划中
layer: 扩展层
priority: 扩展
module: planned
storage: 待设计
entity: 待补充
primary_key: id
foreign_keys:
  - plan_day_id -> study_plan_day.id
  - resource_bank_id -> resource_bank.id
depends_on:
  - study_plan_day
  - resource_bank
progress: 30
summary: 计划与资源的关联表，用于记录某一天最终选择了哪些资源及其排序分。
tags:
  - learnflow
  - database
  - resource
  - planned
---

# study_resource

这是后续资源可解释性和计划资源绑定能力的重要支点，适合在推荐链路稳定后补齐。

## 关键字段

- `plan_day_id`
- `resource_bank_id`
- `rank`
- `score_semantic`
- `score_suitability`
- `score_q`
- `final_score`
- `url_checked`
- `status`

## 关系

- 连接 `study_plan_day` 与 `resource_bank`

## 备注

- 适合后续做“某份计划实际用了哪些资源”的追踪分析
