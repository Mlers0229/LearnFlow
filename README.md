## LearnFlow 智能学习系统（基于多 Agent 的 AI 学习规划平台）

> 面向不会写代码的用户的说明书 + 整个项目的详细规划文档  
> 技术栈：SpringBoot3 + Vue3 + FastAPI + RAG（FAISS/Milvus）+ 多 Agent + Postgres + Redis + MinIO

---

## 1. 项目简介

**LearnFlow** 是一个基于 AI Agent 的智能学习系统，帮助用户：

- **自动拆解学习目标**（比如“3 个月学会 Java 后端开发”）
- **生成个性化学习计划**（按周、按天生成任务和时间安排）
- **自动推荐高质量学习资源**（视频/文章/文档，保证链接可用、内容相关）
- **监督学习进度并动态调整计划**
- **解释知识点、出题练习、错题讲解**

系统整体采用「前后端分离 + AI Agent 平台」的三层架构：

- `Frontend（Vue3）`：学习界面、计划展示、资源展示、聊天互动、进度图表
- `Backend（SpringBoot3）`：用户系统、学习计划管理、对接 AI Agent 平台
- `AI Agent Platform（FastAPI）`：多 Agent 决策与执行、RAG 检索、异步任务处理

你可以把它理解成：  
**一个会帮你自动排课、找资源、监督你学习，还能讲题答疑的“AI 班主任 + 教研组 + 学习助教”组合。**

---

## 1.1 当前实现进度（概要）

- **前端（Vue3）**
  - 已实现：
    - 用户端：学习计划生成页 + 历史计划页 + 关于页，支持调用后端 `/api/plan` 生成计划，并在「历史计划」中查看最近生成的计划列表与详情；整体 UI 已基于 Naive UI 按论文/答辩展示需求完成重构（统一导航、卡片式布局、统计卡片等）。
    - 计划详情组件：支持展示每日任务、**标记完成**、**查看计划完成率进度条**，以及为整份计划和某一天加载推荐资源列表，并提供“细化今日任务”“生成当日练习题”、**保存本次练习作答**、**对资源进行“有帮助 / 不相关”反馈**等交互。
    - 历史计划页：按登录用户展示最近（最多 50 条）生成的学习计划列表，只能看到自己的计划；右侧支持按天查看任务与资源，并提供**重命名计划 / 删除计划（软删）**按钮。
    - 用户认证与导航：登录页 + 注册页 + 简易 `auth` 状态管理，所有业务页面通过路由守卫要求先登录（未登录访问会自动跳转到登录）；登录后导航栏展示当前登录用户及角色（student/admin），管理员登录后会自动跳转到管理端首页。
    - **AI 对话页（流式）**：新增 `/chat` 页面，用户可自由向 AI 提问，回答以流式逐字输出，支持一键停止与清空对话。
    - 资源上传与管理：用户端提供独立的“上传学习资源”页面，仅支持提交资源（进入 `PENDING` 状态，等待审核）；管理端提供“资源管理”页面，支持查看资源列表（标题、URL、水平、时长、标签、状态）、搜索过滤，并由 `admin` 角色执行“审核通过并上线 / 下线 / 拒绝”等操作；管理端页面顶部新增**资源质量统计看板**（总评分资源数、全局平均评分、举报次数），并在表格中展示每条资源的平均评分与反馈数。
- **后端（SpringBoot3）**
  - 已实现：
    - 用户与认证：
      - `User` / `UserRepository` / `AuthService` / `AuthController`：支持 `/api/auth/register`（注册）和 `/api/auth/login`（登录），密码使用 BCrypt 加密存储，区分 `student` 与 `admin` 角色（当前主要用于前端展示与路由守卫）。
    - 计划接口（已按用户隔离数据）：
      - `POST /api/plan`（生成并持久化计划）、`GET /api/plan/{id}`（按 `planId` 查询详情）、`GET /api/plan/recent`（最近计划列表，支持按 `userId` 过滤），使用 Postgres 持久化 `study_plan` / `study_plan_day`，并在生成计划时写入 `user_id` 字段。
      - `PATCH /api/plan/day/{dayId}/status`（更新每日任务状态）、`GET /api/plan/{id}/progress`（统计整份计划完成率）、`POST /api/plan/day/{dayId}/refine`（调用 DetailPlanAgent 细化某一天的任务）。
      - `GET /api/plan/day/{dayId}/exercises`：调用 FastAPI `TutorAgent` 为当日生成 1–2 道练习题，由前端展示题干与参考答案。
      - `POST /api/plan/day/{dayId}/exercise-records`：当前端学生写完某天的练习题答案后，将题干 + 参考答案 + 本次作答以记录形式写入 `exercise_record` 表，为后续 Supervisor 诊断与学习统计提供原始数据。
      - `PATCH /api/plan/{id}`：允许用户更新计划标题 / 状态（例如重命名计划、标记为完成），同时校验该计划属于当前用户。
      - `DELETE /api/plan/{id}`：支持用户删除自己的计划（将 `study_plan.status` 标记为 `cancelled`，并删除其下所有 `study_plan_day` 记录），内部通过事务保证删除一致性。
    - 资源接口（含质量统计与用户反馈）：
      - `POST /api/resources`：登录用户上传资源，写入 `resource_bank`，初始 `status=PENDING`。
      - `GET /api/resources`：管理端查看所有资源（`PENDING / ACTIVE / INACTIVE`）。
      - `PATCH /api/resources/{id}/status`：管理端审核通过或下线资源；只有 `ACTIVE` 资源会被 RAG 推荐使用。
      - `POST /api/resources/{id}/feedback`：当前登录用户在前端点击“有帮助 / 不相关”后，对某条资源打分或举报，写入 `user_resource_feedback` 表，为后续资源质量评分（Qscore）和资源优化提供数据支撑。
      - `GET /api/resources/quality-stats`：聚合 `user_resource_feedback` 表中各资源的平均评分、反馈次数与举报次数，为管理端资源质量 Dashboard 提供数据。
    - AI 代理调用：
      - `AiProxyService` 封装对 FastAPI 平台 `/api/plan`、`/api/rag/resources`、`/api/plan/day/refine`、`/api/tutor/exercise` 的调用，并负责把计划结构写入数据库；同时在资源推荐结果中透传 `resource_bank.id`，以支持前端进行资源反馈与质量统计。
- **AI Agent 平台（FastAPI）**
  - 已实现：
    - `GoalAgent + PlanAgent + Orchestrator` 的多 Agent 调度链路，并通过 `ask_llm` 接入 DeepSeek / OpenAI 兼容接口（可在 `agent-platform/app/config/llm_settings.py` 中直接配置 API Key 和模型名）。
    - `PlanAgent`：首次只生成 3–7 天的“粗略示例天”（每天 2–3 个大任务），后端再根据学习周期（周）扩展为完整的按天计划。
    - `GoalAgent` / `PlanAgent` 均增加了对 LLM 返回结果的 JSON 子串提取与鲁棒解析逻辑，显著降低了因模型输出包含说明文字或 Markdown 而导致的 `JSONDecodeError`，在解析失败时仍会退回本地兜底策略。
    - `DetailPlanAgent`：提供 `/api/plan/day/refine` 接口，可在用户点击“细化今日任务”时，根据整份计划目标 + 当日主题 + 当前任务列表生成更细的任务；同样内置 JSON 子串提取和兜底逻辑。
    - 简易版 `RagAgent`：从 Postgres 的 `resource_bank` 中加载 `ACTIVE` 状态资源，根据整份计划或某一天的主题文本和 level 做关键词匹配，返回推荐资源列表。
    - **Chat 流式接口**：新增 `/api/chat/stream`，面向前端聊天页输出 OpenAI 兼容流式增量文本，未配置 LLM 时会回显兜底，内置 CORS 允许前端直连。

详细的阶段性说明见 `docs/progress-report.md`。下面章节仍以「规划 + 已实现」的混合视角描述整体设计。

---

## 2. 整体架构设计（总览）

### 2.1 系统三层架构

- **前端（Vue3）**
  - 展示：学习计划（日历视图 / 列表视图）、每日任务、资源列表、知识点解释、练习题
  - 交互：输入学习目标、标记完成/未完成、反馈资源质量、答题交互
  - 通信：使用 `REST API` 获取数据，使用 `SSE / WebSocket` 监听异步任务结果（例如资源匹配完成）

- **后端（SpringBoot3）**
  - 负责用户注册、登录、权限校验
  - 管理学习计划、每日任务、学习资源的增删改查
  - 作为 **AI Proxy**，把请求转发给 FastAPI Agent 平台，并把返回结果存入数据库
  - 持久化：使用 `Postgres`

- **AI Agent Platform（FastAPI）**
  - 实现多 Agent：`Goal / Plan / RAG / Tutor / Supervisor / Orchestrator`
  - 提供对外 HTTP API，供 SpringBoot 调用
  - 异步任务（资源检索、验证、打分）通过 `Redis 队列 + Worker` 实现
  - 使用 `FAISS / Milvus` 做向量检索，配合 RAG 策略

### 2.2 外部依赖

- **Postgres**：存储用户、计划、任务、资源等结构化数据
- **FAISS / Milvus**：向量数据库，存储学习资源向量，用于相似度检索
- **Redis**：消息队列（存放异步资源匹配任务），也可做缓存
- **MinIO**：存储 PDF、文档等文件资源
- **LLM 模型**：DeepSeek / QwQ / ChatGPT 等，通过统一 `ask_llm` 接口调用

### 2.3 非功能性目标（性能 / 可用性 / 安全性）

- **性能目标**
  - 学习计划生成（Goal + Plan，不含资源）：**P95 延迟 ≤ 2 秒**
  - 单日资源匹配任务完成：**通常在 2～8 秒内完成，P95 ≤ 10 秒**
  - 单用户常见查询接口（`GET /api/plan/{id}`、`GET /api/plan/{id}/days`）：**P95 ≤ 300ms**

- **可用性目标**
  - 核心接口（计划查询、任务更新）可用性 ≥ 99%
  - 系统异常时给出清晰的错误提示，不出现“白屏”或无反馈状态

- **安全性目标**
  - 用户数据需要登录后才能访问，计划、进度等只能本人查看
  - 密码使用加盐哈希存储，不在日志中输出明文密码
  - 与 Agent 平台之间的调用需要配置访问密钥或内网访问，避免被随意调用

这些指标可以在论文 / 答辩中直接作为“系统目标与约束条件”进行说明。

### 2.4 用户计划隔离设计（已实现）

- **核心思路**：所有学习计划都通过 `study_plan.user_id` 绑定到具体用户，后端在查询 / 更新 / 删除时一律带上 `userId` 参数进行校验，实现“每个用户只能看到和操作自己的计划”。
- **后端实现要点**：
  - 在 `GoalRequest` / 生成计划入口中增加 `userId` 字段，`AiProxyService.persistPlan(...)` 将其写入 `StudyPlan.userId`。
  - 在 `StudyPlanRepository` 中提供 `findByUserIdAndId(...)`、`findAllByUserIdOrderByCreatedAtDesc(...)` 等方法。
  - `PlanQueryService` 的 `getPlanById`、`getRecentPlans`、`updatePlan`、`deletePlanForUser` 等方法统一基于上述仓库方法，只要 `userId` 不匹配就不会返回或修改任何计划。
- **前端约束**：
  - 所有与计划相关的请求（生成、列表、详情、更新、删除）都会从当前登录态中取出 `currentUser.id` 作为 `userId` 传给后端。
  - “历史学习计划”页面只展示当前用户自己的计划，且删除 / 重命名操作只作用于本人计划。

---

## 3. 目录结构规划

> 说明：目前项目根目录是空的，下面是**规划中的目录结构**，后续逐步按阶段创建文件夹与代码。

```text
LearnFlow/
├─ README.md                      # 项目说明 + 规划（本文件）
├─ backend/                       # SpringBoot3 后端工程
│  ├─ src/main/java/com/learnflow/
│  │  ├─ controller/             # 控制器（对前端暴露的 REST 接口）
│  │  ├─ service/                # 业务逻辑层
│  │  ├─ repository/             # 数据访问层（JPA/MyBatis）
│  │  ├─ client/                 # 调用 FastAPI Agent 平台的 HTTP Client
│  │  ├─ config/                 # 配置类（安全、跨域、SSE/WebSocket 配置等）
│  │  └─ dto/                    # 请求 / 响应 DTO
│  └─ src/main/resources/
│     └─ application.yml         # 数据库、Redis、Agent 平台地址等配置
│
├─ agent-platform/                # FastAPI 多 Agent 平台
│  ├─ app/
│  │  ├─ main.py                 # FastAPI 入口
│  │  ├─ routers/                # 对外 API 路由（/plan, /goal, /rag ...）
│  │  ├─ agents/                 # 各类 Agent 实现（GoalAgent, PlanAgent ...）
│  │  ├─ orchestrator/           # 调度器 Orchestrator 实现
│  │  ├─ rag/                    # RAG 检索相关（向量化、检索、排序）
│  │  ├─ models/                 # Pydantic 数据模型（请求 / 响应 Schema）
│  │  ├─ workers/                # 异步任务 Worker（Celery/RQ/自定义）
│  │  ├─ tools/                  # 各种工具（URL 校验、文档解析、MinIO 工具）
│  │  └─ core/llm.py             # ask_llm 封装
│  └─ requirements.txt           # Python 依赖
│
├─ frontend/                      # Vue3 前端工程
│  ├─ src/
│  │  ├─ pages/                  # 页面（首页、学习计划页、资源页、个人中心）
│  │  ├─ components/             # 组件（计划卡片、资源卡片、进度条、聊天框等）
│  │  ├─ api/                    # 封装调用 SpringBoot 后端的接口
│  │  ├─ store/                  # 状态管理（Pinia/Vuex）
│  │  ├─ router/                 # 路由配置
│  │  └─ utils/                  # 工具（SSE/WebSocket 封装等）
│  └─ vite.config.ts             # 构建配置
│
├─ docs/                          # 文档（接口文档、部署文档、论文材料）
│  ├─ api-design.md
│  ├─ db-design.md
│  ├─ agent-design.md
│  └─ deploy-guide.md
└─ scripts/                       # 初始化脚本 / 数据导入脚本等
   └─ init_resource_bank.py
```

---

## 4. 模块功能规划（用“人话”解释）

### 4.0 系统中的角色与权限（规划）

为了后续扩展更容易，这里先规划三个角色（第一版可以只实现“学习者”角色）：

- **学习者（Student）**
  - 可以：注册 / 登录 / 修改个人信息
  - 可以：创建、查看和删除自己的学习计划
  - 可以：更新自己任务的完成状态、对资源进行评分与反馈
  - 不可以：查看他人的计划数据，不可以直接修改资源库

- **教师 / 教研角色（Teacher / Curator）（可选，后续扩展）**
  - 可以：维护 `resource_bank` 中的资源（新增 / 修改 / 下架）
  - 可以：查看资源的整体使用情况统计
  - 不可以：查看具体某个学生的详细学习记录（除非学生授权）

- **管理员（Admin）（可选，后续扩展）**
  - 可以：查看系统整体运行情况（队列长度、失败率等）
  - 可以：管理用户账号、配置 LLM 模型 Key 等
  - 不可以：直接读取用户隐私内容（如聊天详细内容），可只看脱敏统计

在第一版 MVP 中，可以只实现“学习者”权限，其他角色作为后续扩展方向写在论文中体现系统的可扩展性。

### 4.1 前端（Vue3）

**核心页面**

- **目标输入页**
  - 输入学习目标（例如：“2 个月入门 Java 后端开发，每天 1 小时”）
  - 选择基础水平（零基础 / 有一点基础 / 进阶）
  - 提示生成时间（例如：“预计 3 秒内生成计划结构，资源稍后补全”）

- **学习计划总览页**
  - 展示一个「按周/按天」的课表
  - 每天显示：学习主题 + 预计用时 + 任务状态（未开始 / 进行中 / 已完成 / 延迟）
  - 支持点击某一天进入「每日任务详情」

- **每日任务详情页**
  - 展示当日需要学的知识点
  - 列出 AI 推荐好的视频、文章、文档链接
  - 支持：
    - 标记“已完成 / 未完成”
    - 给资源打分（1~5 星）
    - 举报“无效链接”或“不相关”

- **学习助手（聊天 + 练习）页**
  - 内置 Tutor Agent：
    - 解释知识点
    - 根据当前学习章节出练习题
    - 对用户回答进行点评

- **进度与统计页**
  - 展示完成率、连续学习天数、各模块掌握程度
  - Supervisor Agent 生成“学习诊断报告”和“下周建议”

**前端和后端的交互方式**

- 普通数据（计划、任务、资源） → 使用 `REST API`
- 异步任务更新（资源补全、进度提醒） → 使用 `SSE 或 WebSocket`

---

### 4.2 后端（SpringBoot3）

**主要职责**

- 用户注册 / 登录 / 个人信息管理
- 学习目标、学习计划的创建和查询
- 学习任务状态更新（完成、延迟、重新安排）
- 资源信息的增删改查（主要由异步 Worker 写入）
- 和 FastAPI Agent 平台通信（HTTP 调用）

**示例接口（规划）**

- `POST /api/goal`  
  提交学习目标（目标描述、时间周期、每天可用时间、当前水平），调用 Agent 平台，返回拆解后的 Goal 结构。

- `POST /api/plan`  
  根据用户的 Goal 信息，调用 Agent 平台生成学习计划（Plan），并存入数据库。

- `GET /api/plan/{planId}`  
  获取学习计划（包含按周 / 按天的任务，不一定立即包含全部资源）。

- `GET /api/plan/{planId}/days`  
  获取某个计划下的每日任务列表。

- `POST /api/task/{taskId}/status`  
  更新任务状态（完成 / 跳过 / 推迟）。

- `GET /api/resources?taskId=xxx`  
  查询任务对应的学习资源列表。

- `GET /api/progress/overview`  
  查询当前用户的学习进度统计。

后端会把 AI 相关的调用统一封装在 `aiProxy` 中，例如：

```java
@PostMapping("/plan")
public Result generatePlan(@RequestBody GoalRequest req){
    return aiProxy.generatePlan(req);
}
```

---

### 4.3 AI Agent 平台（FastAPI）

**多 Agent 架构**

- `Goal Agent`：理解并拆解用户学习目标（例如从“3 个月学会 Java”拆出：语法基础、面向对象、集合、多线程、Spring 基础等）
- `Plan Agent`：根据 Goal 和用户时间安排，生成按周按天的课表结构（不阻塞资源检索）
- `RAG Agent`：从资源库中找合适的资源（视频、文章等）
- `Tutor Agent`：做“老师”，讲解知识点、出题、讲题
- `Supervisor Agent`：监督和调整，分析学习进度和问题，输出调整建议
- `Orchestrator`：像“调度中心”，决定什么时候调用哪个 Agent，组合调用链

**Orchestrator 调度示例（只描述逻辑）**

- 输入：用户学习目标（文本 + 参数）
- 流程：
  1. 调用 `Goal Agent` → 得到结构化的知识点树
  2. 调用 `Plan Agent` → 把知识点 + 用户时间，排成计划（按周 / 按天）
  3. 把“资源匹配任务”批量丢到 Redis 队列（每个任务对应一个或一组知识点）
  4. 立即返回计划结构给 SpringBoot（不包含或只包含少量资源）
  5. Worker 异步执行：`RAG Agent → 质量评估 → URL 校验 → 入库 → 通知前端`

**Agent 标准化设计**

每个 Agent 需要：

- 独立的 Prompt 模板
- 支持传入结构化输入（JSON）
- 明确的输出 Schema（Pydantic 模型）
- 统一的日志记录（方便论文解释“决策过程”）

示例统一 LLM 调用接口（逻辑说明）：

- `ask_llm(prompt, model="deepseek-chat")`  
  对 DeepSeek / QwQ / ChatGPT 等 OpenAI 兼容服务做统一封装。

**当前实现（落地情况）**

- `agent-platform/app/core/llm.py` 中提供了真实可用的 `ask_llm`：
  - 基于 `httpx` 调用 OpenAI 兼容接口 `POST /v1/chat/completions`；
  - 优先从 `agent-platform/app/config/llm_settings.py` 读取配置（`LLM_API_BASE` / `LLM_API_KEY` / `LLM_API_MODEL`），
    若未配置则退回读取环境变量，再退回到本地 echo 行为保证不崩溃。
- `agent-platform/app/config/llm_settings.py`：
  - 用于在本地直接写入 LLM 的 base URL、模型名和 API Key（不建议提交真实 Key 到公共仓库，可只提交模板）。
- `GoalAgent`：通过 `ask_llm` 让模型按约定 JSON 结构输出 4–6 个学习主题，失败时退回本地规则。
- `PlanAgent`：在 `ENABLE_LLM_PLAN` 开启时，优先请模型直接生成多天计划结构，解析失败时退回 3 天规则示例计划。

---

## 5. Plan Agent & 资源质量体系规划

### 5.1 Plan Agent 职责拆解

Plan Agent 的输入：  

- Goal Agent 拆解好的知识点树（包含优先级、前置依赖）
- 用户信息：每天可用时间、可用周数、当前基础水平

Plan Agent 的输出：  

- 一份结构化的“课表”：
  - `weeks`：每周的学习主题
  - `days`：每天的任务（知识点 + 预计时长）
  - `milestones`：关键里程碑（例如完成 Java 基础语法、掌握集合框架等）

Plan Agent **不负责实时查找资源**，只负责把“学习计划骨架”搭好，然后触发异步资源任务。

### 5.2 五层资源质量保障体系

**第 1 层：资源来源控制（Resource Bank）**

- 建一个本地资源库（`resource_bank` 表 + MinIO 文件）
- 每条资源结构大致如下：

```json
{
  "title": "Java 基础入门视频",
  "url": "https://www.bilibili.com/xxx",
  "level": "beginner",
  "duration": 120,
  "rating": 4.7,
  "tags": ["java", "basic"],
  "qscore": 0.92
}
```

- 所有资源 **先由人工/教研人员审核一次**，再进入 Resource Bank。

**第 2 层：语义向量匹配（RAG）**

- 使用向量库（FAISS/Milvus）存储资源向量
- RAG Agent 通过知识点文本 + 用户水平，检索候选资源
- 打分公式（可在 `rag` 模块实现）：

\[
\text{Score} = 0.5 \times semantic + 0.3 \times suitability + 0.2 \times qscore
\]

**第 3 层：URL 有效性校验**

- Python 工具模块定期/实时检查：
  - HTTP 状态码是否为 200
  - 页面是否可以正常打开
  - 页面标题是否和资源标题大致匹配

**第 4 层：质量评分 Qscore**

- Qscore 综合：
  - 人工初始评分（H）
  - 用户评分（U）
  - 点击率（Clicks）
  - 完成率（Completion）
  - LLM 验证评分（LLM）

\[
Qscore = 0.3H + 0.3U + 0.2Clicks + 0.1Completion + 0.1LLM
\]

**第 5 层：LLM 二次审核**

- 对每条候选资源，问模型：
  - “该资源对于知识点 A 是否适用？如果不适合，请拒绝并给出原因。”
- 只保留模型认可的资源。

---

## 6. 异步任务与 RAG 流程规划

### 6.1 为什么要异步？

- RAG 检索 + URL 校验 + Qscore 计算，整体可能需要 2～8 秒
- 如果全部同步执行，前端用户会卡在那里等很久
- 更好的体验：  
  - 计划（没有资源）**先出来** → 用户可以先看课表  
  - 资源**慢慢填充** → 前端实时刷新

### 6.2 异步流程（文字版）

1. 用户提交学习目标
2. Goal Agent 拆解目标
3. Plan Agent 生成课表结构
4. 系统立即返回计划给用户（0.8 ～ 2 秒内）
5. 后端将“资源匹配任务”写入 Redis 队列
6. Worker 消费任务：调用 RAG Agent → 质量评估 → URL 校验 → 结果写入数据库
7. 后端通过 WebSocket/SSE 通知前端：某个任务的资源已经准备好
8. 前端更新界面，显示新资源

### 6.3 RAG 检索子系统流程

1. 文档解析：支持 PDF / Markdown / 网页
2. 文本分块（chunking）
3. 对每个 chunk 做 Embedding，写入向量库（FAISS/Milvus）
4. 检索时根据知识点描述 + 用户背景做检索
5. 对候选结果打分、排序
6. 返回给 RAG Agent 作为“证据”，辅助生成最终推荐列表

---

## 7. 模型调用机制设计

- 统一封装一个 `ask_llm` 函数（在 `agent-platform/app/core/llm.py`）
- 支持不同厂商的模型（DeepSeek / QwQ / ChatGPT 等）
- 支持可选的 Schema（强制模型输出 JSON，方便解析）

伪代码示例（规划）：

```python
def ask_llm(prompt: str, model: str = "deepseek-chat", schema: dict | None = None) -> dict | str:
    """
    统一的大模型调用接口：
    - prompt: 提示词
    - model: 若不指定则使用默认模型
    - schema: 若提供则要求模型输出 JSON 并进行校验
    """
    ...
```

---

## 8. 数据库设计（初版）

> 实际字段可在开发阶段细化，这里只给出核心结构，详细版本可以拆到 `docs/db-design.md` 中。

- `user`
  - `id`
  - `username`
  - `password_hash`
  - `email`
  - `level`（基础水平）

- `study_plan`
  - `id`
  - `user_id`
  - `goal_text`（原始目标描述）
  - `goal_struct`（Goal Agent 输出的 JSON）
  - `start_date`
  - `end_date`
  - `status`（进行中 / 完成 / 终止）

- `study_plan_day`
  - `id`
  - `plan_id`
  - `date`
  - `title`（当日主题）
  - `tasks_json`（当日任务列表 JSON）
  - `status`

- `study_resource`
  - `id`
  - `plan_day_id`（或 task_id）
  - `resource_bank_id`
  - `recommend_reason`
  - `status`（待验证 / 可用 / 已失效）

- `resource_bank`
  - `id`
  - `title`
  - `url`
  - `level`
  - `duration`
  - `rating`
  - `tags`
  - `qscore`

后续可以根据 Supervisor Agent 的需要增加（部分已在实现中落地）：

- `user_resource_feedback`（用户对资源的评分与反馈，用于资源质量与 Qscore）
- `exercise_record`（练习题记录，用于学习效果分析与诊断）

---

## 9. 开发阶段规划（里程碑）

### 阶段一：最小可用版本（MVP）

- 前端：简单页面，可以输入学习目标，查看生成的计划（不要求资源）
- 后端：完成 `user`、`study_plan` 基本表；实现 `/api/plan` 接口；对接 Goal + Plan Agent
- Agent 平台：实现 `Goal Agent` + `Plan Agent`（先不用 RAG，简单规则/示例资源）
- 不做异步，先同步返回计划结构

> **当前状态速记：**  
> 这一阶段已经基本完成，并且已经在此基础上继续扩展（例如：用户登录、每日任务状态更新等）。

### 阶段二：引入 RAG 和资源质量体系

- 搭建 `resource_bank` 表和向量库
- 实现 `RAG Agent` + 质量计算 + URL 检查工具
- 实现 Redis 队列 + Worker，支持异步资源匹配
- 前端通过 SSE/WebSocket 实时更新资源列表

> **当前状态速记：**  
> 已经有了基础的「资源库 + 关键词匹配推荐」闭环（前端计划详情页 → 后端 `/api/plan/{id}/resources` → Agent 平台 `/api/rag/resources` → 数据库 `resource_bank`），  
> 但还没有真正上向量库（FAISS/Milvus）和完整的五层质量体系，后续可以作为“论文加分项”分步补齐。

### 阶段三：学习助手与监督功能

- 实现 `Tutor Agent`：知识点解释 + 练习题生成
- 实现 `Supervisor Agent`：进度分析 + 动态调整计划
- 前端增加聊天界面、练习题界面、统计/诊断页

> **当前状态速记：**  
> 已实现「简版 Tutor」链路：前端每日任务卡片中可以一键生成 1–2 道练习题，调用 Java 后端 `/api/plan/day/{dayId}/exercises` → FastAPI `TutorAgent` → LLM / 本地兜底题目；当前端学生作答后，可通过 `/api/plan/day/{dayId}/exercise-records` 将题干、参考答案与本次作答写入 `exercise_record` 表，为后续 Supervisor 与统计分析提供数据积累。  
> `Supervisor Agent` 和系统化的练习诊断/统计图表仍未实现，可作为下一步扩展方向。

### 阶段四：优化与论文相关内容

- 完善多 Agent 日志，输出可视化调用链
- 增加更多模型支持和参数配置（切换不同 LLM）
- 优化前端交互与 UI，增加移动端适配

> **当前状态速记：**  
> 已经有一条较完整的「前端 → Java 后端 → Python Agent 平台 → 数据库」调用链，并新增 `agent_call_log` 表记录 Goal/Plan/RAG/Detail/Tutor 等 Agent 的调用日志；前端提供 `/debug/agent-logs` 调试页用于查看多 Agent 调用链与耗时。  
> 后续可以在此基础上补充更直观的调用链可视化（例如时序图）、基础性能统计（P95 耗时）以及与 Supervisor 相关的指标展示，用较小开发量换取论文里的亮点。

---

## 10. 后续改进思路（会随着开发不断更新）

- 支持多学科 / 多目标并行（例如“Java + 英语”）
- 引入“学习伙伴”机制，让两个用户一起学习，有互相提醒
- 支持导入已有学习资源、题库
- 对接更多外部平台（B 站、Coursera、MOOC 等）的 API 做半自动资源同步

---

## 11. 你现在可以怎么用这份规划？

如果你是**写论文 / 做毕设**：

- 可以直接把这里的：
  - 系统目标
  - 多 Agent 架构
  - RAG 与质量保障
  - 异步设计
  - 数据库与接口设计
  整理进论文的系统设计章节。

如果你以后想**实际把系统做出来**：

- 按照“第 9 章 开发阶段规划”从 **MVP → 完整版** 一步一步来
- 我可以在你下一次提问时，帮你从某一个阶段开始：
  - 比如：先生成 **SpringBoot 后端项目骨架**
  - 或者：先实现 **FastAPI Agent 平台的基础结构**
  - 或者：先搭一个 **最简前端页面** 用来调用后端

后续我们每完成一个阶段，我都会再回到这个 `README.md`，更新实际完成情况和改进点，保证这份文档始终是**最新的项目说明书 + 施工蓝图**。


