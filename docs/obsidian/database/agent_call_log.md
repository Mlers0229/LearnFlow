---
type: db_table
project: LearnFlow
table_name: agent_call_log
status: 已实现
layer: 扩展层
priority: 核心
module: agent-platform
storage: FastAPI SQLAlchemy
entity: agent-platform/app/db.py::AgentCallLog
primary_key: id
foreign_keys: []
depends_on: []
progress: 100
summary: 多 Agent 调用日志表，记录 trace_id、输入输出、模型名和耗时，支撑调试与可解释性。
tags:
  - learnflow
  - database
  - log
---

# agent_call_log

这是多 Agent 编排可视化和排障的关键数据表，前端调试页、管理端日志页都依赖它。

## 关键字段

- `trace_id`
- `agent_name`
- `request_payload`
- `response_payload`
- `model_name`
- `duration_ms`
- `created_at`

## 关系

- 逻辑上通过 `trace_id` 串起一次完整请求链路

## 备注

- 已接入 GoalAgent、PlanAgent、DetailPlanAgent、RagAgent、TutorAgent
