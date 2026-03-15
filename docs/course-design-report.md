## 综合课程设计报告

题目：LearnFlow 智能学习系统（多 Agent + RAG 学习规划平台）  
学院：智算工程学院  
专业：待填  
学号：待填  
学生姓名：待填  
指导教师：待填  
日期：2025 年 12 月 日

---

## 目 录
- 1 课题概述  
  - 1.1 课题意义  
  - 1.2 课题目标  
  - 1.3 开发环境  
- 2 课题设计  
  - 2.1 数据库设计  
  - 2.2 系统设计  
- 3 课题实现  
  - 3.1 前端实现  
  - 3.2 后端实现  
  - 3.3 AI Agent 平台实现  
  - 3.4 典型使用流程与时序  
  - 3.5 性能与测试要点  
- 4 总结  

> 目录可在 Word 中通过“引用-目录-插入目录”自动生成；正文标题已按一级/二级结构撰写。
> 排版提示：一级/二级标题均为四号，首行空 2/4/6 个半角空格；表格使用三线表，表题与正文各空一行。

---

## 1  课题概述

### 1.1 课题意义
LearnFlow 面向不会写代码的学习者，提供“一键生成学习计划 + 推荐高质量资源 + 练习与反馈闭环”，让自学过程更高效、可监督、可调整。它将“AI 班主任 + 教研组 + 助教”能力组合到一个平台，可用于课程学习、考证、自学提升，兼具产品与论文价值。核心意义包括：  
- 降低学习规划门槛：用自然语言输入目标即可得到按周/按天计划，消除“不会排课”的焦虑。  
- 提升资源可用性：通过资源库、质量打分与用户反馈闭环，减少“踩坑”无效链接，提高学习时间利用率。  
- 建立监督与自评：练习生成、作答存档与进度统计，为学习诊断、错题分析、学习档案留存提供基础。  
- 便于论文与答辩展示：多 Agent 调用链日志、资源质量五层体系、异步设计可作为创新与实验亮点；界面卡片化，截图友好。  
- 普适性与可扩展性：设计不绑定单一学科，可扩展到编程、英语、考证等多领域；Agent 角色和 RAG 能力可替换或增强。
- 典型应用场景：  
  - 考证/竞赛：如软考、CCF CSP、蓝桥杯，强调高频知识点与题型练习；系统可生成“冲刺计划+每日刷题+资源精简包”。  
  - 职场转岗：如“2 个月转 Java 后端”，按时间与基础水平排课，突出项目实践与源码阅读任务。  
  - 本科课程/毕设：结合课程大纲生成周计划，补齐论文阅读与实验复现任务，便于导师检查。  
  - 终身学习：英语/写作/公开课自学，按可用时长动态调整任务密度。

**价值点（见表1-1）：**

表1-1  LearnFlow 价值点  
| 价值点 | 说明 | 受益人 |
| --- | --- | --- |
| 自动拆解目标 | Goal/Plan Agent 将模糊目标转成按天计划 | 学习者 |
| 资源精准推荐 | RagAgent 从资源库匹配高质量链接 | 学习者/教师 |
| 练习与反馈闭环 | TutorAgent 出题，反馈反哺资源质量 | 学习者/管理员 |
| 可解释与可调试 | Agent 调用链日志便于论文与运维 | 开发者/评审 |

### 1.2 课题目标
- 功能：一键生成计划（按周/天并持久化，按用户隔离）、资源推荐与反馈、每日练习题生成与作答存档、管理端资源审核与质量看板、Agent 调用日志调试。  
- 体验：计划生成 P95 ≤ 2s（不含异步资源），常规查询 P95 ≤ 300ms，前端卡片化布局适合论文/答辩截图。  
- 研究：展示 Goal/Plan/RAG/Tutor/Detail 多 Agent 调度与调用链日志；论证资源质量五层体系与异步 RAG 流程；提供可复现的调用链与日志以支撑论文实验。  
- 可扩展性：预留向量检索、异步队列、SupervisorAgent 学习诊断等演进空间。
- 应用成效指标（可用于论文实验）：  
  - 资源有效率：推荐资源无效链接率 < 5%，举报率逐步下降。  
  - 学习完成度：计划完成率、连续学习天数、每日学习时长达成率。  
  - 练习参与度：每日练习提交数/活跃天数，错题率下降趋势。  
  - 响应性能：核心接口 P95 延迟符合目标值。

### 1.3 开发环境
- 前端：Vue3 + Vite + Naive UI，端口 5173。  
- 后端：SpringBoot3（JPA + Postgres），端口 8080。  
- AI Agent 平台：FastAPI，httpx 调 OpenAI 兼容接口，端口 8000。  
- 数据库：Postgres；缓存/队列预留 Redis；向量库预留 FAISS/Milvus；对象存储预留 MinIO。  
- 操作系统：Win/Linux 通用；包管理 npm / Maven / pip。  
- 硬件：常规 x86_64 开发/服务器，8GB+ 内存即可；论文演示可本机三进程启动。  
- 运行拓扑（可插图）：前端 5173、后端 8080、Agent 8000、Postgres 5432，浏览器直接访问前端，前端经 REST/SSE 调用后端与 Agent。
- 部署形态建议：  
  - 开发/演示：单机多进程，.env 配置 API Key；Postgres 本地或容器。  
  - 生产/云：前后端分离部署，Agent 独立服务，可用 Nginx 作反向代理与 HTTPS；Redis/向量库独立实例；MinIO/S3 做资源文件存储。
- 安全与合规：  
  - 登录态以 Token/Session 保存，敏感配置（LLM Key/DB 密码）放环境变量或配置中心，不写代码库。  
  - 日志脱敏：不记录明文密码和长文本用户答案，仅存必要摘要用于排障。  
  - 访问控制：管理端仅 admin 角色可用，资源审核与用户管理操作需后端再校验角色。

---

## 2  课题设计

### 2.1 数据库设计
核心表与关系可据此绘制 E-R 图（图2-1 可在 Word/Visio 绘制）：  
- 已实现：  
  - `study_plan`（计划主表：user_id、goal_text、duration_weeks、level、status...）  
  - `study_plan_day`（每日任务：day_index、date、tasks_json、status）  
  - `resource_bank`（资源库：title、url、level、duration_minutes、tags、status）  
  - `exercise_record`（练习记录：question、answer、user_answer）  
  - `user_resource_feedback`（资源反馈：rating、reported_invalid、comment）  
  - `agent_call_log`（Agent 调用日志：trace_id、agent_name、payload、duration_ms）  
- 规划中（论文展望）：  
  - `study_resource`（计划-资源关联，含排序分、校验状态）  
  - `async_job`（异步任务队列，记录 RAG/URL 校验状态）  

图2-1  E-R 图  
> 插入 E-R 图示意：user → study_plan → study_plan_day；resource_bank ← user_resource_feedback；plan_day ← exercise_record；agent_call_log 按 trace_id 查询。

表2-1  核心数据表字段摘要  
| 表名 | 关键字段 | 用途 | 状态 |
| --- | --- | --- | --- |
| study_plan | user_id, title, duration_weeks, level, status | 计划主表，按用户隔离 | 已实现 |
| study_plan_day | plan_id, day_index, date, tasks_json, status | 每日任务 | 已实现 |
| resource_bank | title, url, level, duration_minutes, tags, status | 资源池（人工审核） | 已实现 |
| user_resource_feedback | resource_bank_id, user_id, rating, reported_invalid | 资源评分/举报 | 已实现 |
| exercise_record | plan_day_id, user_id, question, answer, user_answer | 练习与作答记录 | 已实现 |
| agent_call_log | trace_id, agent_name, request/response, duration_ms | 多 Agent 调用链 | 已实现 |
| study_resource | plan_day_id, resource_bank_id, final_score, url_checked | 计划-资源关联 | 规划 |
| async_job | job_type, payload, status, error_message | 异步任务状态 | 规划 |
- 数据完整性与索引建议：  
  - 外键：`study_plan_day.plan_id`，`exercise_record.plan_day_id`，`user_resource_feedback.resource_bank_id`。  
  - 索引：`study_plan(user_id, created_at desc)` 提升“最近计划”查询；`study_plan_day(plan_id, day_index)` 加速日程查询；`user_resource_feedback(resource_bank_id)` 支撑质量聚合。  
  - 审计：`agent_call_log.trace_id` 索引用于调用链查询。
- 数据示例（便于论文描述）：  
  - `study_plan`：id=101，user_id=7，title=“Java 入门 8 周”，duration_weeks=8，hours_per_day=2，level=beginner，status=active。  
  - `study_plan_day`：plan_id=101，day_index=3，title=“Java 基础语法”，tasks_json=["变量与类型","分支循环","练习 3 题"]。  
  - `resource_bank`：id=55，title=“B 站 Java 基础教程”，level=beginner，tags="java,basic", status=ACTIVE。  
  - `user_resource_feedback`：resource_bank_id=55, user_id=7, rating=5, reported_invalid=false。  
  - `agent_call_log`：trace_id=abc123，agent_name=GoalAgent，duration_ms=820。

### 2.2 系统设计
- 三层架构：  
  - 前端（Vue3）：计划生成、历史计划、资源列表、练习题、聊天页、资源上传/管理、Agent 日志调试。  
  - 后端（SpringBoot3）：用户与鉴权、计划/任务 CRUD、资源管理、练习记录、进度统计，作为 AI Proxy 调 FastAPI。  
  - AI Agent 平台（FastAPI）：GoalAgent 拆解目标，PlanAgent 生成计划骨架，RagAgent 资源匹配，DetailPlanAgent 细化任务，TutorAgent 出题，统一 `ask_llm`，日志落库。  
- 关键流程：  
  - 计划生成：前端 → Java `/api/plan` → FastAPI `/api/plan`（Goal+Plan）→ 持久化 `study_plan`/`study_plan_day` → 前端展示。  
  - 资源推荐：前端 → Java `/plan/{id}/resources` 或 `/day/{dayId}/resources` → FastAPI `/api/rag/resources` → 返回资源 → 前端反馈写入 `user_resource_feedback`。  
  - 练习出题：前端 → Java `/plan/day/{dayId}/exercises` → FastAPI `/api/tutor/exercise` → 返回题目 → `/exercise-records` 存档。  
  - 调用链日志：FastAPI `agent_call_log` 记录 → 前端 `/debug/agent-logs` 展示。  
- 非功能：计划生成 P95 ≤ 2s，资源推荐 P95 ≤ 10s；安全（userId 隔离、BCrypt 密码、角色守卫）；可用性 ≥ 99%，前端提供 loading/empty/error 三态。  
- 资源质量五层设计（论文可展开）：  
  1) 资源库人工审核（resource_bank）；  
  2) 语义/适配/质量打分（Score = 0.5semantic + 0.3suitability + 0.2qscore）；  
  3) URL 有效性校验（状态码、标题匹配）；  
  4) Qscore 聚合（人工评分 + 用户评分/举报 + 点击率 + 完成率 + LLM 复核）；  
  5) LLM 二次审核不合适资源。  
- 异步思路（当前预留）：计划先回计划骨架，资源匹配可入 Redis 队列 → Worker → SSE/WebSocket 通知前端。
- 推荐插图：  
  - 图2-1 E-R 图（数据库）  
  - 图2-2 系统架构图（前端/后端/Agent/DB 数据流）  
  - 图2-3 多 Agent 调用链时序（前端→Java→FastAPI→LLM/DB）
- 安全设计要点：  
  - 鉴权：登录后才可访问业务接口；admin 路由需角色校验。  
  - 数据隔离：所有计划/任务/反馈操作均校验 userId，防止越权。  
  - 敏感信息：密码 BCrypt 存储，不在日志输出；Agent API Key 存配置/环境变量。
- 传统学习计划工具 vs 多 Agent/RAG 方案（对比说明，可写入论文“相关工作/优势”）：  
  - 计划生成方式：传统工具多为手工编辑或固定模板；多 Agent 通过 Goal/Plan Agent 自动拆解目标并动态排程。  
  - 资源来源：传统依赖用户自找或静态链接；RAG 结合资源库与用户水平，匹配并过滤无效资源，支持反馈迭代。  
  - 动态调整：传统需要用户自行改表；多 Agent 可细化任务、重新生成练习、后续可接入 Supervisor 做诊断与调整。  
  - 互动与练习：传统仅展示任务；本方案内置 TutorAgent 生成练习题与讲解，支持作答存档。  
  - 可解释性：传统缺少链路日志；本方案提供 `agent_call_log` 和调试页，便于论文展示与问题追踪。  
  - 体验与性能：前后端分离 + 异步/流式设计，计划 P95 ≤ 2s，相比静态表格工具更具实时反馈。

### 2.3 接口契约与时序说明
- 核心接口契约（字段仅列关键项，便于正文描述，详见 README）：  
  - `POST /api/plan`：入参 goalText/durationWeeks/hoursPerDay/level/userId；回参 planId、days[]（dayIndex、title、tasks、status）。  
  - `GET /api/plan/{id}`：出参计划元信息 + days[]；若 userId 不匹配则返回 404/空。  
  - `PATCH /api/plan/day/{dayId}/status`：入参 status=NOT_STARTED/IN_PROGRESS/COMPLETED/DELAYED。  
  - `POST /api/plan/day/{dayId}/refine`：入参 dayId，出参 refinedTasks[]。  
  - `GET /api/plan/day/{dayId}/exercises`：出参 questions[]（stem, answer, explanation）。  
  - `POST /api/plan/day/{dayId}/exercise-records`：入参 question/answer/userAnswer。  
  - `GET /api/plan/{id}/resources`、`GET /api/plan/day/{dayId}/resources`：出参资源列表（title/url/level/duration/tags/reason）。  
  - `POST /api/resources/{id}/feedback`：入参 rating、reported_invalid、comment。  
  - `GET /api/resources/quality-stats`：出参 avgRating、feedbackCount、invalidReportCount。  
  - `GET /api/agent/logs`：入参 traceId、limit，出参 agent 调用日志列表。
- 时序（文字描述，可配图）：  
  - 计划生成：前端表单提交 → 后端校验 userId → 调用 FastAPI `/api/plan`（Goal+Plan）→ 回填 planId 持久化 → 前端展示。  
  - 资源推荐：前端点击“加载资源”→ 后端聚合 plan 文本 → 调 FastAPI `/api/rag/resources` → 返回资源列表 → 前端渲染并允许反馈。  
  - 练习生成：前端点击 → 后端查 day 信息 → 调 FastAPI `/api/tutor/exercise` → 返回题目 → 前端展示与保存作答。  
  - 调用链日志：请求携带 traceId 透传各 Agent，写入 `agent_call_log`，前端调试页按 traceId 拉取。

---

## 3  课题实现

### 3.1 前端实现
- 技术栈：Vue3 + Vite + Naive UI，路由守卫要求登录；API Base：`http://localhost:8080`，聊天 SSE 直连 `http://localhost:8000`.  
- 页面与组件：  
  - `PlanGeneratorPage`：表单生成计划，loading 防重；结果区用 `PlanResultCard` 展示。  
  - `PlanHistoryPage`：左列计划列表 + 日程概览，右列当天详情；支持重命名、软删、进度卡片。  
  - `PlanResultCard`：计划头部、进度条、全局资源加载、日任务卡（完成/细化/资源/练习）、资源反馈按钮。  
  - `ResourceManagePage`（admin）：资源录入、列表、状态审核，质量统计看板（平均评分、反馈数、举报数）。  
  - `ResourceUploadPage`（user）：仅提交资源，进入待审核。  
  - `AgentLogDebugPage`：按 traceId/limit 查询多 Agent 调用日志表格，方便论文截图。  
  - `Chat` 页：SSE 流式输出，支持停止/清空/复制。  
- 交互与体验：  
  - 全局 loading/empty/error 三态；按钮禁用与“正在...”文案；表单校验。  
  - 角色识别：student/admin 决定导航与管理端入口；未登录跳转登录页。  
- UI 亮点（可截图说明）：  
  - 计划详情单列时间线 + 进度条，资源卡片置顶，适合展示。  
  - 资源反馈按钮有“已反馈”状态，避免重复提交。  
  - Agent 日志表格列出时间、Agent、耗时、traceId，清晰展现调用链。

### 3.2 后端实现
- 核心实体：`StudyPlan`、`StudyPlanDay`、`ResourceBank`、`ExerciseRecord`、`UserResourceFeedback`，JPA 自动建表。  
- 主要接口（全部按 userId 校验隔离）：  
  - 计划：`POST /api/plan` 生成并持久化；`GET /api/plan/{id}` 详情；`GET /api/plan/recent` 最近 50 条；`PATCH /api/plan/{id}` 重命名/状态；`DELETE /api/plan/{id}` 软删；`GET /api/plan/{id}/progress` 完成度。  
  - 每日任务：`PATCH /api/plan/day/{dayId}/status` 打卡；`POST /api/plan/day/{dayId}/refine` 细化；`GET /api/plan/day/{dayId}/exercises` 生成题；`POST /api/plan/day/{dayId}/exercise-records` 保存作答。  
  - 资源：`POST /api/plan/{id}/resources`（整计划推荐）/`GET` 版本按实现；`GET /api/plan/day/{dayId}/resources`；`POST /api/resources` 上传；`GET /api/resources` 列表；`PATCH /api/resources/{id}/status` 审核；`POST /api/resources/{id}/feedback` 反馈；`GET /api/resources/quality-stats` 质量聚合。  
  - 调试：`GET /api/agent/logs` 透传 Agent 平台日志。  
- 关键逻辑：  
  - `AiProxyService` 封装调用 FastAPI `/api/plan`/`/api/rag/resources`/`/api/plan/day/refine`/`/api/tutor/exercise`，失败有本地兜底。  
  - 事务与一致性：删除计划时事务删除其天记录并标记 cancelled；状态/重命名均校验归属。  
  - 安全：BCrypt 密码；角色 student/admin；禁用用户不可登录。  
- 可扩展接口（论文展望）：  
  - `study_resource` 关联查询与统计；  
  - Supervisor 分析输出诊断报告接口；  
  - 资源质量趋势与练习统计接口。

### 3.3 AI Agent 平台实现
- 统一 LLM：`ask_llm` 基于 httpx 调 OpenAI 兼容接口，支持从配置/环境获取 Key；有 JSON 子串提取防解析失败，若失败回显兜底。  
- Agent 职责：  
  - GoalAgent：拆解目标为 4–6 个主题，日志落库。  
  - PlanAgent：生成 3–7 天示例或直接多天计划，支持 `ENABLE_LLM_PLAN`；解析失败回退示例计划。  
  - DetailPlanAgent：细化单日任务，记录请求/响应。  
  - RagAgent：从 `resource_bank` 加载 ACTIVE 资源，基于关键词匹配标题/标签并排序。  
  - TutorAgent：生成 1–2 道练习题；失败回退本地题目。  
  - StudyOrchestrator：为 `/api/plan` 请求生成 trace_id 串联 Goal→Plan。  
- 日志：`agent_call_log` 记录 agent_name、trace_id、payload、model_name、duration_ms，可供前端调试页与论文截图。
- 后续增强方向：  
  - 向量召回：引入 FAISS/Milvus，embedding + rerank 替代关键词匹配。  
  - SupervisorAgent：基于进度与练习记录输出学习诊断与调整建议。  
  - 更细粒度的异常兜底：针对 LLM 超时/解析失败的回退策略与重试。

### 3.4 典型使用流程与时序
1) 学生登录；  
2) 填写目标/周期/每天时长/水平后提交生成计划；  
3) Java 后端调用 FastAPI Goal+Plan，持久化 `study_plan` 与 `study_plan_day`；  
4) 前端展示计划、进度条，可一键加载整计划资源；  
5) 学生按日查看任务，点击“细化”“生成练习题”“标记完成”；  
6) 对资源提交“有帮助/不相关/举报”，写入反馈；  
7) 练习作答保存到 `exercise_record`；  
8) 管理员在资源管理页审核资源、查看质量看板；  
9) 开发者在 Agent 日志页查看调用链。  
> 可配合时序图：前端→后端→Agent→DB→前端，标注 trace_id 贯穿。

### 3.5 性能与测试要点
- 性能目标：计划生成 P95 ≤ 2s（不含异步资源）；单日资源推荐 P95 ≤ 10s；常规查询 P95 ≤ 300ms。  
- 测试要点：  
  - 接口联调：`/api/plan`、`/plan/{id}`、`/plan/day/{dayId}/exercises`、`/resources/quality-stats`。  
  - 角色与权限：未登录访问业务页需跳转登录；student 无法访问 admin 路由；禁用用户登录失败。  
  - 计划隔离：不同 userId 互不可见计划与任务。  
  - 资源反馈与质量统计：提交评分/举报后，质量看板聚合数据更新。  
  - SSE 聊天：无 Key 时可回显兜底，避免前端卡死；停止/清空按钮可用。  
  - 端到端回归：生成计划→加载资源→细化任务→生成练习→保存作答→提交反馈→查看质量看板，全链路需成功。  
  - 可靠性：异常时前端展示错误提示，不出现白屏；后端/Agent 失败有兜底计划或题目。

### 3.6 测试用例与性能数据示例
- 功能用例（可在正文列表或附录给出细表）：  
  - 登录/注册：正确凭证成功；错误密码失败；禁用用户拒绝。  
  - 计划生成：必填校验；LLM 正常返回；LLM 失败触发兜底计划。  
  - 计划查看：仅本人可见；他人 planId 返回 404。  
  - 日任务打卡：状态流转 NOT_STARTED→IN_PROGRESS→COMPLETED，重复点击幂等。  
  - 细化任务：返回更细任务列表，失败提示并保留原任务。  
  - 练习生成与保存：题目展示正确；保存作答成功后可在 DB 查询到记录。  
  - 资源推荐：有资源时列表渲染；无资源时空态提示；错误时提示“稍后重试”。  
  - 资源反馈：同一资源同一用户多次提交时需前端防重复（已反馈状态）。  
  - 管理端：资源审核状态流转 PENDING→ACTIVE/INACTIVE；质量看板显示聚合值。  
  - 调试页：按 traceId 过滤日志，耗时字段为正数，日志时间倒序。
- 性能数据模板（可在论文插入表格）：  
  - 表3-1 接口性能 P50/P95（单位 ms）：`/api/plan`、`/api/plan/{id}`、`/api/plan/day/{dayId}/exercises`、`/api/plan/{id}/resources`。  
  - 表3-2 资源有效性：有效率（200/总数）、举报率、平均评分。  
  - 表3-3 用户行为：平均每天学习时长达成率、连续学习天数分布、练习提交率。  
  - 测试环境说明：CPU/内存、并发数、是否开启 LLM、是否走兜底。

式（1-1）  资源排序示例公式  
Score = 0.5 × semantic + 0.3 × suitability + 0.2 × qscore  

表1-2  资源反馈样例（示意三线表）  
| 姓名 | 学号 | 资源ID | 评分 | 举报 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 同学1 | 2025xxxx | R-01 | 5 | 否 | 很有用 |
| 同学2 | 2025xxxx | R-02 | 3 | 否 | 一般 |
（续）表1-2  
| 姓名 | 学号 | 资源ID | 评分 | 举报 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 同学3 | 2025xxxx | R-03 | 2 | 是 | 链接失效 |
| 同学4 | 2025xxxx | R-04 | 4 | 否 | 内容清晰 |

> 代码不宜正文大量贴出，如需可在附录放置关键接口示例（<40 行/段），正文保留调用链和流程描述。

---

## 4  总结
已完成前端-后端-多 Agent 的端到端联调，实现计划生成、资源推荐、练习与反馈闭环，并提供调试与质量看板，满足论文与演示需要。后续可扩展：  
- 引入向量检索（FAISS/Milvus）提升资源召回。  
- 实现 SupervisorAgent 做学习诊断与动态调整。  
- 完善异步队列与 URL 校验、Qscore 计算，提升资源质量闭环。  
- 增加练习统计与资源质量趋势图，丰富论文实验与可视化。  

> 可在 Word 中按学校格式调整字号/行距、插入 E-R 图、系统架构图、调用链时序图和界面截图，再更新目录即可。

