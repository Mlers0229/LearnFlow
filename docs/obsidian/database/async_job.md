---
type: db_table
project: LearnFlow
table_name: async_job
status: 规划中
layer: 扩展层
priority: 扩展
module: planned
storage: 待设计
entity: 待补充
primary_key: id
foreign_keys: []
depends_on: []
progress: 10
summary: 异步任务队列表，用于承接长耗时生成、批处理和后台任务编排。
tags:
  - learnflow
  - database
  - async
  - planned
---

# async_job

这张表目前还未真正落地，但对后续计划生成异步化、资源批处理和后台任务调度很有价值。

## 可能字段

- `job_type`
- `status`
- `payload`
- `result`
- `retry_count`
- `scheduled_at`
- `started_at`
- `finished_at`

## 关系

- 未来可能和用户、计划、资源任务建立弱关联

## 备注

- 适合作为后续“异步任务 / 队列系统”设计入口
