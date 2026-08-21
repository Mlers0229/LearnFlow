## LearnFlow 项目阶段性进度报告

> 本文用于记录当前项目整体进展，方便后续开发、汇报和论文撰写。  
> 状态标记：✅ 已完成 ｜ 🔄 进行中 ｜ ⏳ 规划中

---

## 1. 整体规划与文档

- ✅ `README.md`：完成整体系统规划，并持续对齐最新实现
  - 项目背景与目标（多 Agent + RAG 智能学习系统）
  - 三层架构：前端（Vue3）+ 后端（SpringBoot3）+ Agent 平台（FastAPI）
  - 多 Agent 设计：Goal / Plan / RAG / Tutor / Supervisor / Orchestrator
  - 资源质量五层体系、异步任务机制、RAG 流程
  - 开发阶段里程碑：MVP → 引入 RAG → Tutor/Supervisor → 优化与论文相关内容
  - 已根据最新实现补充：简版 Tutor 功能、多 Agent 调用日志与调试页等状态说明。

- ✅ `docs/db-design.md`：数据库设计文档
  - 已实现表：`study_plan`、`study_plan_day`、`resource_bank`、`agent_call_log`、`exercise_record`、`user_resource_feedback`
  - 规划中的表：`study_resource`、`async_job` 等
  - 每张表有清晰的字段说明和用途说明，可直接用于论文“数据库设计”章节。

- ✅ 当前新增：`docs/progress-report.md`（本文件）
  - 用于持续记录每个阶段完成内容与下一步计划。

---

## 2. 后端（SpringBoot3）进度

工程根目录：`backend/`

- ✅ 项目骨架
  - `pom.xml`：已配置
    - `spring-boot-starter-web`
    - `spring-boot-starter-validation`
    - `spring-boot-starter-data-jpa`
    - `postgresql` 驱动
  - 主启动类：`LearnFlowApplication`

- ✅ 配置与数据库连接
  - `application.yml`：
    - `server.port=8080`
    - 连接本机 Postgres：
      - 数据库：`learnflow`
      - 用户：`learnflow_user`
      - 密码：`0229`（当前配置）
      - JPA：`ddl-auto=update`，自动建表
    - `learnflow.ai-agent.base-url=http://localhost:8000`（FastAPI Agent 平台）

- ✅ 核心实体与表（已通过 JPA 自动建表）
  - `StudyPlan` → 表 `study_plan`
    - 字段：`id, userId, goalText, title, durationWeeks, hoursPerDay, level, startDate, endDate, status, createdAt, updatedAt`
  - `StudyPlanDay` → 表 `study_plan_day`
    - 字段：`id, plan(plan_id), dayIndex, date, title, tasksJson, status, createdAt, updatedAt`
  - `ResourceBank` → 表 `resource_bank`
    - 字段：`id, title, url, level, durationMinutes, tags, status, createdAt, updatedAt`
  - Repository：
    - `StudyPlanRepository`
    - `StudyPlanDayRepository`
    - `ResourceBankRepository`

- ✅ 对接 FastAPI Agent 平台 + 持久化逻辑
  - `AiProxyService`：
    - 使用 `RestTemplate` 调用 `http://localhost:8000/api/plan`
    - 请求体字段映射：`goalText → goal_text` 等（驼峰 ↔ 下划线）
    - 成功时：将 `PlanResponse` 结果持久化到 `study_plan` 和 `study_plan_day`
    - 失败时：使用本地示例计划兜底，并同样持久化
    - 统一将数据库生成的 `plan.id` 写回 `planResponse.planId`，方便前端和后续查询
    - 额外封装：
      - `recommendResources(...)`：调用 `/api/rag/resources`，解析资源列表；
      - `refineDayTasks(...)`：调用 `/api/plan/day/refine`，细化单日任务；
      - `generateExercises(...)`：调用 `/api/tutor/exercise`，生成当日练习题；
      - `getAgentLogs(...)`：调用 `/api/agent/logs`，获取多 Agent 调用日志。

- ✅ REST 接口
  - `PlanController`：
    - `POST /api/plan`：生成并持久化学习计划（写入 `study_plan.user_id` 实现用户绑定）
    - `GET /api/plan/{id}`：根据数据库中的 `planId` 与 `userId` 查询完整计划
    - `GET /api/plan/recent?limit&userId`：按用户查询最近计划概要列表（默认最多 50 条）
    - `GET /api/plan/{id}/resources`：整份计划维度的资源推荐
    - `GET /api/plan/day/{dayId}/resources`：按“天”维度的资源推荐
    - `PATCH /api/plan/day/{dayId}/status`：每日任务状态更新（打卡）
    - `GET /api/plan/{id}/progress`：整份计划完成度统计（用于进度条）
    - `POST /api/plan/day/{dayId}/refine`：调用 DetailPlanAgent 细化当日任务
    - `GET /api/plan/day/{dayId}/exercises`：调用 TutorAgent 为当日生成 1–2 道练习题
    - `POST /api/plan/day/{dayId}/exercise-records`：将题干、参考答案与本次作答写入 `exercise_record` 表。
    - `PATCH /api/plan/{id}`：允许用户重命名计划或更新计划状态（active/completed/cancelled），仅限本人计划。
    - `DELETE /api/plan/{id}`：删除（软删）用户自己的计划，将 `status` 标记为 `cancelled` 并删除其下所有 `study_plan_day`，通过 `@Transactional` 保证删除与状态更新在一个事务中完成。
  - `ResourceController`：
    - `POST /api/resources`：上传新的学习资源到 `resource_bank`
    - `GET /api/resources`：查询资源列表（支持状态显示与管理端审核）
    - `PATCH /api/resources/{id}/status`：审核通过 / 下线资源
    - `POST /api/resources/{id}/feedback`：提交资源评分与“无效/不相关”举报，写入 `user_resource_feedback`
    - `GET /api/resources/quality-stats`：按资源维度聚合平均评分、反馈数与举报数，为管理端 Dashboard 提供数据
  - `DebugController`：
    - `GET /api/agent/logs?traceId&limit`：从 FastAPI 侧拉取多 Agent 调用日志，供前端调试页展示
  - 支撑查询逻辑：
    - `PlanQueryService`：封装按 `planId + userId` 查询计划详情 / 按用户查询最近计划列表 / 更新计划 / 删除计划的业务逻辑
    - `StudyPlanDayRepository.findByPlan_IdOrderByDayIndexAsc(...)`、`deleteAllByPlan_Id(...)`
    - `StudyPlanRepository.findAllByUserIdOrderByCreatedAtDesc(Pageable ...)`、`findByUserIdAndId(...)`
    - `PlanQueryService.buildPlanTopicText(...)`：聚合 plan 的目标文本和每日标题，作为 RagAgent 的主题输入
    - `PlanQueryService.getPlanLevel(...)`：获取计划的难度等级，作为 RagAgent 的 level 输入

- 🔄 下一步计划（后端）
  - 计划与资源关联表 `study_resource` 的实现与查询接口（用于统计某计划用了哪些资源）
  - 在已落地的 `exercise_record` / `user_resource_feedback` 表基础上，补充 Supervisor 分析逻辑与统计接口（例如错题统计、资源质量趋势）。

---

## 3. AI Agent 平台（FastAPI）进度

工程根目录：`agent-platform/`

- ✅ 基础结构
  - `requirements.txt`：`fastapi, uvicorn, pydantic, httpx, sqlalchemy, psycopg2-binary` 等
  - `app/main.py`：FastAPI 应用入口，挂载 `/api/plan`、`/api/rag`、`/api/tutor`、`/api/agent/logs` 等路由

- ✅ 模型与 Agent 实现
  - `app/models/goal.py`：
    - `GoalRequest`（与 Java 的 GoalRequest 对应）
    - `GoalTopic`、`GoalPlanStructure`：用于表示 GoalAgent 拆解后的知识主题列表
  - `app/models/plan.py`：`PlanDay`、`PlanResponse`、`DayRefineRequest/Response`（与 Java 的 PlanResponse/PlanDayDto 对应）
  - `app/models/resource.py`：`ResourceItem`、`ResourceRecommendRequest`、`ResourceRecommendResponse`
  - `app/models/exercise.py`：`ExerciseQuestion`、`TutorExerciseRequest/Response`（练习题结构）
  - `app/models/log.py`：`AgentCallLogItem`、`AgentCallLogListResponse`（多 Agent 调用日志结构）
  - `app/core/llm.py`：
    - `ask_llm` 统一封装，基于 `httpx` 调用 OpenAI 兼容接口（支持 DeepSeek 等）
    - 优先从 `app/config/llm_settings.py` 读取 `LLM_API_BASE` / `LLM_API_KEY` / `LLM_API_MODEL` 配置，
      若未配置则退回到环境变量，再退回本地 echo 兜底
  - `app/config/llm_settings.py`：集中管理 LLM 配置（本地可直接填写 DeepSeek 等参数）
  - `app/agents/goal_agent.py`：
    - 优先调用 `ask_llm`，让 LLM 按约定 JSON 结构返回 4–6 个学习主题；失败时自动使用本地兜底主题列表
    - 每次调用都会将输入输出与耗时记录到 `agent_call_log`（通过 `save_agent_call`）
  - `app/agents/plan_agent.py`：
    - 能够利用 `GoalPlanStructure` 中的主题列表，生成与主题对齐的 3 天示例课表
    - 当 `ENABLE_LLM_PLAN=true` 时：
      - 优先调用 LLM 直接生成多天计划（按 `day_offset` 计算日期），并解析为 `PlanResponse`
      - 出错时退回到本地 3 天示例计划
    - 调用 LLM 时同样记录调用日志到 `agent_call_log`
  - `app/agents/detail_plan_agent.py`：
    - 提供 `refine_day` 方法，将某一天的粗略任务细化成更小的执行步骤
    - 在调用 LLM 细化任务时，会记录请求、响应 JSON 子串以及耗时到 `agent_call_log`
  - `app/agents/rag_agent.py`：
    - 当前版本不调用 LLM 生成 URL，只基于本地/数据库中的资源库做匹配推荐
    - 启动时从 `resource_bank` 表加载所有 `ACTIVE` 资源到内存（若表为空则使用内置示例资源）
    - 匹配策略：根据整份计划聚合的主题文本（目标描述 + 每日标题）和 level，对资源的标签/tag 与标题做关键词匹配，返回相关性最高的若干条
    - 推荐完成后，将请求与返回结果记录到 `agent_call_log`（model_name 记录为 `-`）
  - `app/agents/tutor_agent.py`：
    - 简版 TutorAgent：根据某一天主题 + 整体目标 + 学习水平生成 1–2 道练习题
    - 优先调用 LLM 按约定 JSON 输出题目与参考答案；失败时回退到本地兜底题目
    - 同样将请求与响应（或异常）记录到 `agent_call_log`
  - `app/orchestrator/study_orchestrator.py`：
    - 已实现 `GoalAgent → PlanAgent` 的串联调度流程
    - 为每次 `/api/plan` 请求生成 `trace_id`，并在内部透传，用于将多条日志串成一条调用链
  - `app/db.py`：
    - 使用 SQLAlchemy 连接 Postgres（默认连接 `learnflow` 数据库）
    - 定义 `ResourceBank` ORM 模型，并在应用启动时自动建表
    - 新增 `AgentCallLog` ORM 模型及 `save_agent_call(...)` 工具函数，用于持久化多 Agent 调用日志
  - `app/config/db_settings.py`：
    - 通过文件集中管理 Agent 侧数据库连接信息（`DATABASE_URL` 或分字段配置）

- ✅ 路由
  - `app/routers/plan.py`：
    - `POST /api/plan`：接受 `GoalRequest`，生成学习计划（内部会串联 GoalAgent + PlanAgent，并记录 traceId）
    - `POST /api/plan/day/refine`：接受 `DayRefineRequest`，调用 `DetailPlanAgent.refine_day` 细化当日任务
  - `app/routers/rag.py`：
    - `POST /api/rag/resources`：接受 `ResourceRecommendRequest`，调用 `RagAgent.recommend` 返回 `ResourceRecommendResponse`
  - `app/routers/tutor.py`：
    - `POST /api/tutor/exercise`：接受 `TutorExerciseRequest`，调用 `TutorAgent.generate_exercises` 返回练习题列表
  - `app/routers/log.py`：
    - `GET /api/agent/logs?trace_id&limit`：查询多 Agent 调用日志，支持按 traceId 过滤

- 🔄 下一步计划（Agent 平台）
  - 已完成 RAG MVP 与 pgvector 稠密检索升级：资源库元数据索引、关键词倒排召回、版本化向量构建与原子切换、混合召回融合、反馈感知重排，以及索引状态/重建接口；向量服务异常时自动降级
  - 设计并实现 `SupervisorAgent` 的简版能力（基于计划与练习记录给出学习诊断与调整建议）

---

## 4. 前端（Vue3 + Vite）进度

工程根目录：`frontend/`

- ✅ 基本工程
  - 使用 Vite + Vue3 构建：
    - `package.json`：`vue`、`vue-router`、`vite`
    - `vite.config.js`：端口 `5173`
  - 入口：
    - `index.html`
    - `src/main.js`：挂载 Vue 应用并注册路由
    - 全局样式：`src/style.css`（已设计出较现代的卡片式 UI）

- ✅ 路由与布局
  - `src/router/index.js`：
    - `/`（用户端）→ `UserLayout`：
      - `/` → `PlanGeneratorPage`
      - `/history` → `PlanHistoryPage`
      - `/about` → `AboutPage`
    - `/admin`（管理端）→ `AdminLayout`：
      - `/admin/resources` → `ResourceManagePage`
  - `src/components/UserLayout.vue`：
    - 顶部导航（Logo + 菜单：生成学习计划 / 历史计划 / 关于系统）
    - 使用 `<RouterView />` 渲染用户端页面
    - 当用户角色为 `admin` 时，在导航中额外展示“管理端”入口，以及仅管理员可见的“开发者”下拉菜单（含 Agent 调用日志调试页）。
  - `src/components/AdminLayout.vue`：
    - 独立的“LearnFlow 管理端”导航，仅展示管理功能（目前为资源管理 + 返回用户端）

- ✅ API 封装
  - `src/api/config.js`：`API_BASE_URL = 'http://localhost:8080'`
  - `src/api/plan.js`：
    - `generatePlan(payload)` 调用后端 `POST /api/plan`，携带 `userId`，在后端落库到 `study_plan.user_id`。
    - `getRecentPlans(limit, userId)` 调用 `GET /api/plan/recent`，按用户查询最近计划（默认最多 50 条）。
    - `getPlanById(id, userId)` 调用 `GET /api/plan/{id}`，按 `planId + userId` 查询详情。
    - `updatePlan(id, userId, payload)` 调用 `PATCH /api/plan/{id}`，用于重命名计划或更新状态。
    - `deletePlan(id, userId)` 调用 `DELETE /api/plan/{id}`，删除当前用户自己的计划。
    - `getResourcesByPlan(planId)` 调用 `GET /api/plan/{id}/resources`
    - `getResourcesByDay(dayId)` 调用 `GET /api/plan/day/{dayId}/resources`
    - `updateDayStatus(dayId, status)` 调用 `PATCH /api/plan/day/{dayId}/status`
    - `getPlanProgress(planId)` 调用 `GET /api/plan/{id}/progress`
    - `refineDay(dayId)` 调用 `POST /api/plan/day/{dayId}/refine`（细化当日任务）
    - `getExercisesByDay(dayId)` 调用 `GET /api/plan/day/{dayId}/exercises`（生成当日练习题）
    - `saveExerciseRecord(dayId, payload)` 调用 `POST /api/plan/day/{dayId}/exercise-records`（持久化练习记录）
    - `getAgentLogs(params)` 调用 `GET /api/agent/logs`（多 Agent 调用日志）
  - `src/api/resource.js`：
    - `createResource` / `listResources` / `updateResourceStatus`：资源上传与审核。
    - `submitResourceFeedback`：提交资源评分与举报。
    - `getResourceQualityStats`：获取按资源聚合的质量统计数据，支撑管理端 Dashboard。

- ✅ 页面与组件
  - `src/pages/PlanGeneratorPage.vue`：
    - 表单输入：学习目标、周期、每天时长、基础水平
    - 调用 `generatePlan`，展示生成的学习计划
  - `src/components/PlanResultCard.vue`：
    - 展示计划标题、时间范围、各天任务列表和状态
    - 支持一键为整份计划加载推荐资源（调用 `GET /api/plan/{id}/resources`），在计划头部下方统一展示推荐资源列表
    - 每日任务卡片：
      - 可以标记“已完成”（调用后端打卡接口，并更新前端状态）；
      - 可以点击“细化今日任务”，调用 DetailPlanAgent 生成更细的任务列表；
      - 可以按天加载推荐资源（调用 `GET /api/plan/day/{dayId}/resources`）；
      - 可以点击“生成 / 查看当日练习题”，调用 TutorAgent，展示 1–2 道题目 + 本地作答文本框 + 折叠参考答案；
      - 可以点击“保存本次作答”，调用 `saveExerciseRecord` 将本次答题记录写入后端。
    - 在每条资源下方增加“觉得这个资源怎么样？”行，提供“有帮助 / 不相关 / 无效”两个按钮，调用 `submitResourceFeedback` 写入 `user_resource_feedback`。
    - 头部显示整份计划完成度进度条（基于 `GET /api/plan/{id}/progress`）。
  - `src/pages/PlanHistoryPage.vue`：
    - 左侧：通过 `getRecentPlans(limit, userId)` 展示当前登录用户最近生成的计划列表（按创建时间倒序，默认最多 50 条）。
    - 右侧：点击列表项后，通过 `getPlanById(id, userId)` 加载详情，以“按天维度”的布局展示选中日期的任务、资源及练习题。
    - 右侧顶部增加“当前计划推荐资源总览”卡片，可一键加载当前计划下所有推荐资源，并在同一位置集中展示。
    - 右侧操作区提供“重命名计划”“删除计划”按钮，分别调用 `updatePlan` 和 `deletePlan`，仅作用于当前登录用户自己的计划。
    - 支持：加载中状态、错误提示、无数据时的引导文案。
  - `src/pages/AboutPage.vue`：
    - 用通俗语言说明系统架构与调用链，适合论文和答辩截图使用
  - `src/pages/ResourceManagePage.vue`（管理端）：
    - 表单输入：资源标题、URL、适用水平、预计学习时长（小时+分钟）、标签（逗号分隔），时长输入不再限制最大 24 小时 / 59 分钟。
    - 调用 `POST /api/resources` 将资源写入 `resource_bank`，并通过管理端操作更新 `status`。
    - 下方通过 `GET /api/resources` 展示当前资源库中的资源列表，并配合状态字段进行管理。
    - 新增资源质量统计看板：通过 `getResourceQualityStats` 展示“有评分的资源总数 / 全局平均评分 / 举报总数”等聚合指标。
    - 在资源表格中为每条资源展示平均评分、反馈数和举报数，辅助管理员优化资源库。
  - `src/pages/AgentLogDebugPage.vue`（调试页）：
    - 通过 `getAgentLogs` 拉取最近的多 Agent 调用日志
    - 支持按 `traceId` 过滤查看某一次完整调用链（例如一次 `/api/plan` 请求下的 GoalAgent + PlanAgent 调用）
    - 使用表格展示：时间、Agent 名称、耗时、traceId、请求/响应摘要，方便论文截图与调试说明

- 🔄 下一步计划（前端）
  - 在计划详情区域增加一个“显示原始 JSON”调试开关，便于论文展示数据结构。
  - 在管理端资源列表中继续扩展编辑功能与更细粒度的筛选条件。
  - 基于已落地的 `exercise_record` 与 `user_resource_feedback` 数据，在前端增加简单的练习统计 / 资源质量趋势图表。

---

## 5. 当前整体联调状态

- 前端：
  - 在 `http://localhost:5173` 输入学习目标，点击“生成学习计划”，系统会以当前登录用户的 `userId` 生成并绑定学习计划。
  - 在「历史计划」页面可以看到当前登录用户最近生成的计划列表，并点击查看详情、重命名或删除计划（不会看到其他用户的计划）。
  - 在计划详情 / 历史计划详情页：
    - 可以为整份计划或某一天加载推荐资源；
    - 可以为每日任务标记完成、细化任务、生成当日练习题，并将本次作答保存到后端；
    - 可以对每条资源进行“有帮助 / 不相关”反馈；
    - 可以在 `/debug/agent-logs` 页面查看最近的多 Agent 调用日志。
- 后端（SpringBoot3）：
  - `POST /api/plan` 接收请求，调用 FastAPI 平台生成计划并持久化到 Postgres 的 `study_plan` 和 `study_plan_day`，同时写入 `user_id`。
  - `GET /api/plan/{id}`：根据 `planId + userId` 组装 `PlanResponse` 返回前端。
  - `GET /api/plan/recent?limit&userId`：返回当前用户最近 N 个计划的简要信息（默认最多 50 条）。
  - `GET /api/plan/{id}/resources` / `GET /api/plan/day/{dayId}/resources`：针对整份计划或某一天推荐资源。
  - `PATCH /api/plan/day/{dayId}/status`：更新每日任务状态。
  - `GET /api/plan/{id}/progress`：统计整份计划完成率。
  - `POST /api/plan/day/{dayId}/refine`：调用 DetailPlanAgent 细化当日任务。
  - `GET /api/plan/day/{dayId}/exercises`：调用 TutorAgent 生成练习题。
  - `POST /api/plan/day/{dayId}/exercise-records`：写入练习记录，为后续 Supervisor 与统计图表提供数据。
  - `POST /api/resources` / `GET /api/resources` / `PATCH /api/resources/{id}/status`：管理端维护资源库（资源上传与审核）。
  - `POST /api/resources/{id}/feedback` / `GET /api/resources/quality-stats`：收集用户评分与举报信息，并按资源维度聚合质量数据，为管理端 Dashboard 使用。
  - `GET /api/agent/logs`：转发 FastAPI 日志接口，为前端调试页提供数据。
- AI Agent 平台（FastAPI）：
  - `POST /api/plan` 调用 `StudyOrchestrator.run_study_plan()`：
    - `GoalAgent`：优先通过 DeepSeek（或其他 OpenAI 兼容模型）拆解学习目标为主题列表，失败时退回本地规则
    - `PlanAgent`：根据目标、时间设置和主题列表生成学习计划；在 `ENABLE_LLM_PLAN=true` 时优先调用 LLM 直接生成多天计划
  - `POST /api/rag/resources`：
    - 从 Postgres 的 `resource_bank` 中加载资源，根据整份计划的主题文本和 level 做关键词匹配，返回推荐资源列表
  - `POST /api/tutor/exercise`：
    - 根据 TutorExerciseRequest 生成 1–2 道练习题（优先 LLM，失败时本地兜底）
  - `GET /api/agent/logs`：
    - 查询 `agent_call_log` 表，返回最近的多 Agent 调用日志，支持按 traceId 过滤

目前已打通的链路：

- 计划生成与查询：**前端 → 后端（Java `/api/plan`）→ Agent 平台（Python `/api/plan`）→ Java 持久化 → 前端展示 & 历史查询**
- 资源推荐：**前端计划详情页 → 后端 `/api/plan/{id}/resources` 或 `/api/plan/day/{dayId}/resources` → Agent 平台 `/api/rag/resources` → Postgres `resource_bank` → 前端展示推荐资源**
- 练习题生成与记录：**前端每日任务卡片 → 后端 `/api/plan/day/{dayId}/exercises` → Agent 平台 `/api/tutor/exercise` → LLM / 本地兜底 → 前端展示题目与参考答案 → 用户作答后调用 `/api/plan/day/{dayId}/exercise-records` 写入 `exercise_record` 表**
- 调用链日志：**前端 `/debug/agent-logs` → 后端 `/api/agent/logs` → Agent 平台 `/api/agent/logs` → Postgres `agent_call_log` → 前端表格展示**

---

## 6. 下一阶段重点工作（建议优先级）

1. **Supervisor 简版能力 + 学习诊断**
   - 设计并实现 `SupervisorAgent`：基于计划完成度、练习题结果等信息输出简要学习诊断与下周建议
   - 在前端增加简单的“学习统计 / 诊断”页，先用少量字段和文案展示效果
2. **练习记录与数据积累（已完成基础落地，后续扩展）**
   - ✅ 已通过 `exercise_record` 表记录 Tutor 生成题目与用户作答结果，并在前端提供“保存本次作答”按钮；
   - 后续可基于这些数据增加“错题统计 / 知识点掌握度”等可视化模块，为 Supervisor 提供更丰富输入。
3. **资源质量与用户反馈（已完成基础落地，后续扩展）**
   - ✅ 已通过 `user_resource_feedback` 表收集资源评分与举报信息，并在管理端实现资源质量 Dashboard（平均评分、反馈数、举报数）；
   - 后续可在 RAG 排序与 Qscore 计算中引入这些指标，并在前端增加资源质量趋势与对比视图。
4. **RAG 向量检索升级**
   - 已完成轻量 RAG MVP：资源库元数据索引、关键词倒排召回、本地哈希向量 fallback、混合召回融合、反馈感知重排，以及 `/api/v2/rag/index/status`、`/api/v2/rag/index/rebuild` 索引接口。
   - 已完成 pgvector 稠密召回：固定维度向量表、HNSW 余弦索引、向量模型版本状态机、批量异步回填、激活版本原子切换、元数据预过滤和故障降级；后续可基于真实流量继续调优召回参数。

本进度报告会随着每次重大改动同步更新，保持与代码实现一致，方便你随时拿去写周报、开题报告和毕业论文中的“系统实现进度”部分。 

---

## 7. 本次迭代更新摘要（2025-11-30）

- **后端**
  - 新增 `User` / `UserRepository` / `AuthService` / `AuthController`，支持 `/api/auth/register`、`/api/auth/login`，使用 BCrypt 存储密码，区分 `student` 与 `admin` 角色。
  - 完成每日任务状态更新接口、计划完成率统计接口、按天任务细化接口，以及资源审核流（用户提交资源默认为 `PENDING`，管理端审核后变为 `ACTIVE`）。
- **AI Agent 平台**
  - 调整 `PlanAgent`：首次只生成 3–7 天的粗略“示例天”，再按学习周期周数扩展为完整天数。
  - 新增 `DetailPlanAgent` + `/api/plan/day/refine`，支持基于“整体目标 + 当日主题 + 粗任务列表”细化单天任务，并带有 JSON 子串提取与兜底逻辑。
- **前端**
  - 新增登录页、注册页和简单的 `auth` 状态管理；在导航栏展示当前登录用户并支持退出登录。
  - 为每日任务增加“标记为已完成”和“细化今日任务”按钮，以及整份计划的完成度进度条。
  - 资源管理页支持显示资源状态，并提供“审核通过并上线 / 下线 / 拒绝”操作；普通用户可通过导航中的“上传学习资源（待审核）”入口提交资源。

---

## 8. 本次迭代更新摘要（2025-12-xx，简版 Tutor + 调用链日志）

- **后端（SpringBoot3）**
  - 在 `AiProxyService` 中新增：
    - `generateExercises(...)`：协调调用 FastAPI `TutorAgent` 为某天生成 1–2 道练习题；
    - `getAgentLogs(...)`：转发 FastAPI 的 `/api/agent/logs` 接口，获取多 Agent 调用日志。
  - 新增 DTO：
    - `ExerciseQuestionDto`：封装练习题的题干、参考答案与讲解；
    - `AgentCallLogDto`：封装 Agent 调用日志的关键信息（agentName、traceId、durationMs 等）。
  - 在控制器层：
    - `PlanController` 新增 `GET /api/plan/day/{dayId}/exercises`，根据 `dayId` 查出主题与计划信息，再调用 `generateExercises`。
    - 新增 `DebugController`，暴露 `GET /api/agent/logs`，为前端调试页提供统一入口。

- **AI Agent 平台（FastAPI）**
  - 新增 `ExerciseQuestion / TutorExerciseRequest/Response` 模型与 `TutorAgent`：
    - 根据当日主题 + 整体目标 + 学习水平生成 1–2 道练习题，优先调用 LLM，失败时使用本地兜底题目。
  - 新增 `AgentCallLog` ORM 模型与 `save_agent_call(...)` 工具函数：
    - 自动将 Goal/Plan/Detail/RAG/Tutor 等 Agent 的请求、响应和耗时写入 Postgres 的 `agent_call_log` 表。
  - 为各 Agent 增加日志记录：
    - `GoalAgent` / `PlanAgent` / `DetailPlanAgent` / `RagAgent` / `TutorAgent` 在关键调用处调用 `save_agent_call`；
    - `StudyOrchestrator` 为每次 `/api/plan` 请求生成 `trace_id` 并透传。
  - 新增 `app/models/log.py` 与 `app/routers/log.py`：
    - 提供 `GET /api/agent/logs?trace_id&limit` 接口，支持按 traceId 查询调用链日志。

- **前端（Vue3）**
  - 在 `PlanResultCard.vue` 中：
    - 为每日任务增加“生成 / 查看当日练习题”按钮与展示区域，支持本地填写答案、折叠查看参考答案与讲解。
  - 新增调试页面 `AgentLogDebugPage.vue` 并在路由下挂载 `/debug/agent-logs`：
    - 通过 `getAgentLogs` 调用 `/api/agent/logs`；
    - 使用表格展示最近的多 Agent 调用日志，支持按 traceId 过滤，便于论文和答辩截图展示“多 Agent 调用链”。

---

## 9. 本次迭代更新摘要（2025-12-09，前端 UI 重构 + 登录守卫 + 资源上传分离）

- **前端（Vue3）**
  - 完成基于 Naive UI 的一轮前端重构，核心页面包括：
    - 顶层布局：`UserLayout` / `AdminLayout`，统一导航样式与内容区域宽度，适配 1920×1080 / 2560×1440 等常见桌面分辨率，页面更适合论文与答辩截图使用。
    - 计划详情组件：`PlanResultCard.vue` 使用多列卡片网格展示每日任务，头部展示计划标题、时间范围与完成度进度条，支持按天细化任务、加载资源与生成练习题。
    - 计划生成页：`PlanGeneratorPage.vue` 采用“三段式”布局（系统说明卡片 + 表单 + 结果预览），整体观感更类似“控制台面板”。
    - 历史计划页：`PlanHistoryPage.vue` 顶部提供统计卡片，下方采用左右分栏（左侧列表 + 右侧单日详情）展示近期计划及每日任务，右侧不再直接复用整份 `PlanResultCard`，而是单独展示选中日期的任务与资源，更适合答辩讲解。
    - 登录 / 注册页：`LoginPage.vue` / `RegisterPage.vue` 使用统一卡片样式和表单校验规则，支持区分 `student` / `admin` 角色，布局在大屏上更加居中、美观。
    - 资源相关页面：
      - 用户端新增 `ResourceUploadPage.vue`：普通登录用户可在“上传学习资源”页提交资源（进入 `PENDING` 状态），页面只负责录入，不暴露审核按钮。
      - 管理端继续使用 `ResourceManagePage.vue`：在原有表单 + 列表基础上，增加搜索/筛选（按标题/URL/标签、level、status），并保留“审核通过并上线 / 下线 / 拒绝”操作，仅供 `admin` 使用。
    - 说明与调试页面：`AboutPage.vue` / `AgentLogDebugPage.vue` 使用更规整的卡片分区与排版，更适合论文中展示系统架构与多 Agent 调用链。
  - 路由与登录守卫：
    - 在 `router/index.js` 中为核心业务路由添加 `meta.requiresAuth`，统一通过 `beforeEach` 守卫控制：未登录用户访问任意业务页面会自动跳转到登录页，实现“先登录/注册，再进入系统”。
    - 已登录用户访问 `/login` 或 `/register` 时自动跳回首页，避免重复登录。
    - `/admin` 路由恢复为 `meta.requiresAdmin`，只允许 `admin` 角色进入管理端导航与资源管理页面；普通用户仅能访问用户端页面和用户侧“上传学习资源”入口。

- **AI Agent 平台（FastAPI）**
  - `GoalAgent` 与 `PlanAgent`：
    - 在调用 LLM 后增加统一的“JSON 子串提取”辅助函数：从包含说明文字、Markdown 代码块或其它噪声的响应中提取出真正的 JSON 片段，再交给 `json.loads` 解析。
    - 针对之前出现的 `JSONDecodeError: Expecting value: line 1 column 1 (char 0)` 问题进行了修复和加固：即使模型输出前后夹杂中文提示或 Markdown，也尽量从中间提取出合法 JSON；若提取/解析仍失败，则优雅地回退到本地兜底主题列表或示例计划，不影响整体链路。
  - 通过上述改动，配合已有的兜底逻辑，使得在 LLM 输出不稳定时，系统依然能稳定返回可用的学习计划结构，减少前后端联调时的偶发错误。

---

## 10. 本次迭代更新摘要（2025-12-15，AI 对话页 + 流式接口）

- **AI Agent 平台（FastAPI）**
  - 新增 `app/routers/chat.py` 与 `/api/chat/stream`：调用 OpenAI 兼容流式接口返回增量文本；未配置 LLM 时自动回显兜底，避免前端卡死。
  - `main.py` 增加全局 CORS 允许前端（5173/8080）直连 8000 端口的流式接口。
- **前端（Vue3）**
  - 新增聊天页面 `/chat`：自由提问，回答以流式形式实时输出，支持“停止生成”“清空对话”。
  - API 配置增加 `CHAT_API_BASE_URL`（默认 `http://localhost:8000`），并封装 `streamChat` 供页面使用。

---

## 11. 本次迭代更新摘要（2025-12-20，结果页布局优化 + 个人设置 + 管理端增强）

- **前端（Vue3）**
  - 生成结果页（PlanResultCard）重新布局：整份计划推荐资源移到顶部，日卡改单列时间线，任务默认展示前 3 条可折叠，资源反馈改为单选防重复提交并显示“已反馈”状态。
  - 个人设置页 `/profile`：可修改邮箱、学习水平，支持原密码校验后修改密码；主题亮/暗切换、字号增减移动到此页面；退出登录新增确认弹窗。
  - AI 聊天页：为助手消息增加“复制内容”按钮，“停止生成”按钮更显眼。
  - 资源管理：支持批量上线/下线、编辑资源、CSV 导出修正（含 BOM，列顺序明确），表格选择列启用行 key。
- **后端（SpringBoot3）**
  - `AuthController` 增加 `/api/auth/profile`：支持更新邮箱/level/密码（校验原密码），`AuthResponse` 返回 email；登录校验用户状态（ACTIVE/DISABLED）。
  - 管理端扩展：用户创建、角色/状态更新、重置密码（临时密码）接口；资源批量状态更新与信息编辑；基础审计日志实体/查询接口。
- **用户体验**
  - 主题/字号持久化到本地，适配全局。
  - 结果页信息密度降低，空白减少，阅读体验更贴合答辩/展示。
