# LearnFlow 论文素材提纲（可直接拷贝到论文各章节）

## 1. 系统概述
- 目标：基于多 Agent + RAG 的智能学习系统，自动生成个性化学习计划、推荐资源、生成练习并收集反馈。
- 用户：自学者/学生，需求：快速拆解目标、获得高质量资源与练习闭环。
- 技术栈：前端 Vue3 + Naive UI；后端 SpringBoot3 + Postgres；AI Agent 平台 FastAPI（OpenAI 兼容流式接口）；Redis（异步任务）、MinIO（资源存储）、FAISS/Milvus 预留。

## 2. 总体架构
- 三层：前端（展示/交互/流式聊天）、后端（用户/计划/资源/代理转发）、AI Agent 平台（Goal/Plan/RAG/Tutor/Supervisor 预留）。
- 数据流：前端请求 → Java 后端 → FastAPI Agents → 数据持久化 → 前端展示；反馈/练习记录反哺资源质量与诊断。
- 部署：前后端分离，REST API；AI 平台独立服务，SSE 流式输出 `/api/chat/stream`。

## 3. 核心功能
- 学习计划生成：输入目标/周期/时长/水平 → GoalAgent 拆解 → PlanAgent 生成日程 → 持久化 → 前端展示进度与日任务。
- 资源推荐：整计划/单日调用 RagAgent，返回资源列表；用户反馈“有帮助/不相关”，写入 `user_resource_feedback`。
- 练习与记录：单日生成 1–2 道练习题（TutorAgent），作答保存 `exercise_record`。
- 计划管理：历史列表、重命名、软删、日任务状态更新、进度条。
- 管理端：资源上传/审核/批量上下线/编辑/CSV 导出；资源质量统计。用户管理（角色/状态/创建/重置密码）。
- 聊天页：流式回答、停止生成、复制内容，使用 SSE。

## 4. 数据库设计（摘要）
- 核心表：`app_user`（角色/状态/邮箱/level）、`study_plan`、`study_plan_day`、`resource_bank`、`user_resource_feedback`、`exercise_record`。
- 扩展表：`agent_call_log`（多 Agent 调用链）、`admin_audit_log`（审计日志）、`async_job`（规划中）。
- 示例字段：
  - study_plan：user_id, goal_text, title, duration_weeks, hours_per_day, level, start_date, end_date, status
  - study_plan_day：plan_id, day_index, date, title, tasks_json, status
  - resource_bank：title, url, level, duration_minutes, tags, status
  - user_resource_feedback：resource_bank_id, user_id, rating, reported_invalid, comment, created_at
  - exercise_record：plan_day_id, user_id, question, answer, explanation, user_answer, created_at

## 5. 关键流程/算法
- 多 Agent 调度：GoalAgent 拆解 → PlanAgent 生成骨架 →（可选异步 RAG）→ DetailPlanAgent 细化 → TutorAgent 生成练习。
- RAG 策略：已实现资源库元数据索引、关键词倒排召回、pgvector 稠密向量召回、混合召回融合与反馈感知重排；向量模型采用显式版本管理，可灰度构建并原子切换，异常时自动降级到关键词/本地检索链路。
- 流式聊天：FastAPI `/api/chat/stream` + SSE；前端 TextDecoderStream 实时渲染，支持停止与复制。
- 资源质量闭环：用户评分/举报 → 质量统计 → 管理端仪表板与排序参考。

## 6. 性能与非功能指标
- 计划生成 P95 ≤ 2s（不含资源异步）；单日资源推荐 P95 ≤ 10s；常规查询 P95 ≤ 300ms。
- 可用性：核心接口 ≥ 99%；安全性：密码加盐哈希，计划按 userId 隔离；管理端操作需 admin 角色。

## 7. 前端界面亮点
- 结果页：日任务时间线（单列），整份资源置顶；任务折叠；资源反馈单选防重复并显示状态。
- 管理端：资源表批量上下线/编辑/导出（含 BOM）；用户管理（角色/状态/创建/重置）；质量统计看板。
- 个人设置：邮箱/水平修改；主题亮暗、字号调整；改密需原密码；退出确认。
- 聊天：流式输出，复制代码，显眼停止按钮。

## 8. 迭代要点（摘自 progress-report）
- 2025-12-20：结果页布局优化、个人设置、资源批量/编辑/导出修正、聊天复制/停止按钮。
- 2025-12-15：流式聊天接口 + 聊天页。
- 2025-12-09：前端 UI 重构、登录守卫、资源上传分离。
- 更早：Tutor/Detail Agent、计划/资源/练习闭环、调用链日志等。

## 9. 可用图示建议
- 架构图：前端/后端/Agent 平台/DB。
- 数据流/时序：计划生成、资源推荐、练习生成、流式聊天。
- RAG 流程：资源库 → 召回/过滤/打分 → 前端反馈。
- E-R 图：核心表及关系。

## 10. 评测思路
- 耗时：计划生成/资源推荐接口 P50/P95。
- 推荐有效性：有帮助率、举报率。
- 练习参与度：每日练习提交数/完成率。
- 可用性：登录、计划查询成功率与响应时间。


