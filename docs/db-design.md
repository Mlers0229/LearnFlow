## LearnFlow 数据库设计（规划与实现进度）

> 说明：本文件用于记录数据库表结构设计和当前实现进度，方便后续开发和论文撰写。  
> 实际已落地的表，会在状态中标注为「已实现」。

---

## 1. 总览

按“核心优先、扩展加分”的思路，数据库分为两层：

- **核心层（必须）**：用户、学习计划、每日任务、资源库、计划资源关联
- **扩展层（可选/论文加分）**：用户反馈、练习记录、Agent 调用日志、异步任务队列表

当前代码中已经实现的核心表：

- `study_plan`：学习计划总表（由 Java 后端 JPA 管理） ✅
- `study_plan_day`：每日计划表（由 Java 后端 JPA 管理） ✅
- `resource_bank`：资源库表（由 FastAPI 侧 SQLAlchemy 与 Java JPA 共同管理） ✅
- `agent_call_log`：Agent 调用日志表（由 FastAPI 侧 SQLAlchemy 管理） ✅
- `exercise_record`：练习记录表（由 Java 后端 JPA 管理） ✅
- `user_resource_feedback`：用户资源反馈表（由 Java 后端 JPA 管理） ✅

其余表暂时处于“规划中”，方便后续逐步实现。

---

## 2. 用户表 `user`（规划中）

> 管理学习者、教师、管理员等账号信息。

**字段示例：**

- `id` `bigserial` PK
- `username` `varchar(50)` 唯一
- `password_hash` `varchar(200)`
- `email` `varchar(100)` 可空
- `role` `varchar(20)`：`student / teacher / admin`
- `level` `varchar(20)`：`beginner / intermediate / advanced`
- `created_at` `timestamp`
- `updated_at` `timestamp`

状态：**规划中，尚未在代码中实现。**

---

## 3. 学习计划总表 `study_plan`（已实现）

> 一条记录代表一次完整的学习计划生成，对应前端的一次“生成计划”。

**表名：** `study_plan`

**对应实体：** `com.learnflow.entity.StudyPlan`

**主要字段：**

- `id` `bigserial` PK  
- `user_id` `bigint` 外键 → `user.id`，用于实现“每个用户只能看到自己的计划”
- `goal_text` `text`：用户原始目标描述
- `title` `varchar(200)`：计划标题，例如 “Java 入门 8 周计划”
- `duration_weeks` `int`：学习周期（周）
- `hours_per_day` `int`：每天学习时长（小时）
- `level` `varchar(20)`：学习者基础水平
- `start_date` `date`
- `end_date` `date`
- `status` `varchar(20)`：`active / completed / cancelled`
- `created_at` `timestamp`
- `updated_at` `timestamp`

**代码中的行为：**

- 在 `AiProxyService.generatePlan` / `persistPlan` 中，当从 FastAPI 拿到 `PlanResponse` 后，会创建并保存一条 `StudyPlan`，并将前端传入的 `userId` 写入 `user_id` 字段。
- 在 `StudyPlanRepository` 中通过 `findByUserIdAndId(...)`、`findAllByUserIdOrderByCreatedAtDesc(...)` 等方法按用户过滤计划；
  `PlanQueryService.getPlanById`、`getRecentPlans`、`updatePlan`、`deletePlanForUser` 等接口都依赖这些方法，保证用户只能访问和操作自己的计划。

状态：**已实现（使用 H2 + JPA 自动建表）。**

---

## 4. 每日计划表 `study_plan_day`（已实现）

> 一条记录代表某个计划中的“一天”及其任务列表。

**表名：** `study_plan_day`

**对应实体：** `com.learnflow.entity.StudyPlanDay`

**主要字段：**

- `id` `bigserial` PK  
- `plan_id` `bigint` FK → `study_plan.id`（JPA 中通过 `@ManyToOne` 映射为 `StudyPlan plan`）
- `day_index` `int`：第几天（从 1 开始）
- `date` `date`：该天对应的日期
- `title` `varchar(200)`：当日学习主题
- `tasks_json` `text`：任务列表的 JSON 字符串（例如 `["任务1","任务2"]`）
- `status` `varchar(20)`：`NOT_STARTED / IN_PROGRESS / COMPLETED / DELAYED`
- `created_at` `timestamp`
- `updated_at` `timestamp`

**代码中的行为：**

- `AiProxyService.persistPlan(...)` 会遍历 `PlanResponse.days`：
  - 为每一天创建一条 `StudyPlanDay`，写入 `dayIndex / date / title / tasksJson / status`。
  - 使用 Jackson 将 `List<String> tasks` 序列化为 `tasksJson`。
  - 将小写状态（如 `not_started`）转换为大写（`NOT_STARTED`）存表。

状态：**已实现（使用 H2 + JPA 自动建表）。**

---

## 5. 资源库表 `resource_bank`（已实现）

> 对应“五层质量体系”的第 1 层：人工审核后的资源池。

**表名：** `resource_bank`

**当前实现（FastAPI 侧 SQLAlchemy）：**

- 位置：`agent-platform/app/db.py` 中的 `ResourceBank` 模型
- 主要字段：

  - `id` `serial` PK
  - `title` `varchar(300)`
  - `url` `text`
  - `level` `varchar(20)`：`beginner / intermediate / advanced`
  - `duration_minutes` `int` 可空
  - `tags` `text`：逗号分隔的标签字符串，例如 `"java,basic,intro"`
  - `status` `varchar(20)`：`ACTIVE / INACTIVE`

**用途与行为：**

- FastAPI 启动时通过 `init_db()` 自动建表。
- `RagAgent` 启动时会从 `resource_bank` 中加载所有 `status=ACTIVE` 的记录映射为 `ResourceItem`，作为资源推荐的候选集合。
- Java 后端的 `ResourceController` 通过 `POST /api/resources` / `GET /api/resources` 写入和读取这张表，实现资源管理端能力。

状态：**已实现**。

---

## 6. 计划资源关联表 `study_resource`（规划中）

> 某个计划某一天，最终选用了哪些资源，以及各自的打分信息。

**表名：** `study_resource`

**主要字段（规划）：**

- `id` `bigserial` PK
- `plan_day_id` `bigint` FK → `study_plan_day.id`
- `resource_bank_id` `bigint` FK → `resource_bank.id`
- `rank` `int`：排序优先级（1 为最高）
- `score_semantic` `numeric(3,2)`：语义相似度分
- `score_suitability` `numeric(3,2)`：适配度分
- `score_q` `numeric(3,2)`：质量分（可冗余自 resource_bank）
- `final_score` `numeric(3,2)`：最终排序分（0.5*semantic + 0.3*suitability + 0.2*qscore）
- `url_checked` `boolean`
- `url_status_code` `int`
- `url_title_matched` `boolean`
- `status` `varchar(20)`：`PENDING / VALID / INVALID`
- `created_at` `timestamp`
- `updated_at` `timestamp`

状态：**规划中，尚未在代码中实现。**

---

## 7. 用户资源反馈表 `user_resource_feedback`（已实现）

> 用于统计用户评分和举报信息，反向更新 Qscore。

**表名：** `user_resource_feedback`

**主要字段（当前实现）：**

- `id` `bigserial` PK
- `user_id` `bigint` FK → `user.id`
- `resource_bank_id` `bigint` FK → `resource_bank.id`
- `rating` `int`（1–5）
- `comment` `text`
- `reported_invalid` `boolean`
- `created_at` `timestamp`

状态：**已实现（由 Java 后端通过 JPA 管理，实体为 `UserResourceFeedback`）。**

**补充：资源质量聚合视图**

- 在 `UserResourceFeedbackRepository` 中通过原生 SQL 聚合：
  - 每个 `resource_bank_id` 的平均评分 `avgRating`
  - 反馈次数 `feedbackCount`
  - 举报为无效的次数 `invalidReportCount`
- Java 侧使用 `ResourceQualityStatsDto` 暴露为 `/api/resources/quality-stats` 接口，供管理端资源质量 Dashboard 使用。

---

## 8. 练习与答题记录表 `exercise_record`（已实现）

> Tutor Agent 生成题目与用户作答记录，支撑学习效果分析。

**表名：** `exercise_record`

**主要字段（当前实现）：**

- `id` `bigserial` PK
- `user_id` `bigint` FK
- `plan_day_id` `bigint` FK
- `question` `text`：题干
- `answer` `text`：参考答案
- `explanation` `text`：答案讲解（可空）
- `user_answer` `text`：用户本次作答
- `created_at` `timestamp`

状态：**已实现（由 Java 后端通过 JPA 管理，实体为 `ExerciseRecord`）。**

当前版本未区分是否答对，仅做“原始练习数据存档”，方便后续 Supervisor 分析与统计图表扩展。

---

## 9. Agent 调用日志表 `agent_call_log`（已实现）

> 记录每次 Agent 调用的输入输出和耗时，提供可解释性和排障能力，是“多 Agent 调用链”可视化的基础。

**表名：** `agent_call_log`

**当前实现（FastAPI 侧 SQLAlchemy）：**

- 位置：`agent-platform/app/db.py` 中的 `AgentCallLog` 模型
- 主要字段：

  - `id` `serial` PK
  - `trace_id` `varchar(64)`：一次完整请求的链路 ID（例如某次 `/api/plan` 请求）
  - `agent_name` `varchar(64)`：`GoalAgent / PlanAgent / RagAgent / DetailPlanAgent / TutorAgent / SupervisorAgent`
  - `request_payload` `text`：请求内容的 JSON 字符串
  - `response_payload` `text`：响应内容的 JSON 字符串或原始文本
  - `model_name` `varchar(64)`：使用的大模型名称，若未使用模型则可以为 `-` 或 `NULL`
  - `duration_ms` `int`：本次 Agent 调用耗时（毫秒）
  - `created_at` `timestamptz`：日志创建时间

**代码中的行为：**

- `save_agent_call(...)`：
  - 封装插入逻辑，在各 Agent 内部调用；
  - 即使写入失败也不会影响主流程（只记录异常日志）。
- 已接入日志记录的 Agent：
  - `GoalAgent`：LLM 拆解学习目标时记录；
  - `PlanAgent`：LLM 生成计划骨架时记录；
  - `DetailPlanAgent`：细化当日任务时记录；
  - `RagAgent`：完成一次资源推荐时记录（model_name 记为 `-`）；
  - `TutorAgent`：生成练习题时记录。
- Orchestrator：
  - `StudyOrchestrator` 为每次 `/api/plan` 请求生成 `trace_id` 并透传，用于将多条日志串成一条完整调用链。
- 对外查询接口：
  - `GET /api/agent/logs?trace_id&limit`（FastAPI）：
    - 对应 `app/routers/log.py` 与 `app/models/log.py`；
    - 支持按 `trace_id` 过滤与按时间倒序限制条数；
  - Java 后端通过 `AiProxyService.getAgentLogs(...)` 调用该接口，为前端的“Agent 调用日志调试页”提供数据。

状态：**已实现（FastAPI 侧）**。

---

## 10. 异步任务队列表 `async_job`（规划中）

> 与 Redis 队列配合，用于记录异步任务（RAG、URL 校验等）的状态。

**表名：** `async_job`

**主要字段（规划）：**

- `id` `bigserial` PK
- `job_type` `varchar(50)`：`RAG_RESOURCE_MATCH / URL_CHECK / QSCORE_UPDATE` 等
- `plan_id` `bigint` 可空
- `plan_day_id` `bigint` 可空
- `payload` `jsonb`
- `status` `varchar(20)`：`PENDING / RUNNING / SUCCESS / FAILED`
- `error_message` `text`
- `created_at` `timestamp`
- `updated_at` `timestamp`

状态：**规划中。**

---

## 11. 后续更新计划

- 当我们在代码中真正实现某个新表时，会在本文件中：
  - 标记其状态为「已实现」
  - 补充对应实体类名与关键接口说明
- 随着 Feature 增长（例如 RAG、Supervisor、Tutor 等），可以进一步把各表字段细化。

这份文档可以直接作为论文中“数据库设计”小节的基础素材使用。以后如果你有新的数据需求（例如新增某个统计图），我们可以先把字段设计补充到这里，再落地到代码中。 


