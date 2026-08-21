# LearnFlow 生产级完善规划

> 文档状态：In Progress
> 适用范围：Frontend、Spring Boot Backend、FastAPI Agent Platform、PostgreSQL 与部署基础设施
> 目标周期：单人约 18～24 周；2～3 人团队约 10～14 周
> 更新日期：2026-08-21

## 1. 目标

将 LearnFlow 从功能完整的学习系统原型升级为可安全上线、可持续交付、可观测、可回滚、可量化评估的生产系统。

本规划默认面向初期正式生产规模：

- 1,000～10,000 DAU。
- 月度可用性目标 99.9%。
- 允许采用托管 PostgreSQL、对象存储、Secret Manager 和容器平台。
- 保留 Vue + Spring Boot + FastAPI + PostgreSQL 的现有技术边界。
- 不以引入更多框架或 Agent 数量作为完成标准。

## 2. 生产级完成标准

| 类别 | 目标 |
| --- | --- |
| 可用性 | 月度 99.9% |
| 普通 API | P95 < 500ms，5xx < 0.5% |
| AI 任务 | 成功率 > 98%，全部配置超时、取消和降级 |
| 安全 | 无未鉴权管理接口，不信任客户端传入的用户身份 |
| 数据 | RPO ≤ 15 分钟，RTO ≤ 60 分钟，通过恢复演练 |
| 发布 | 测试、迁移、漏洞扫描、AI 回归全部通过后才允许部署 |
| 回滚 | 10 分钟内恢复上一稳定版本 |
| RAG | Recall@5 ≥ 0.80，NDCG@5 ≥ 0.75，具体阈值允许按基线调整 |
| 可观测性 | 任一失败请求可沿 trace 定位到 API、Agent、模型和数据库阶段 |

## 3. 实施原则

1. 安全、数据正确性和可验证交付优先于新增 AI 功能。
2. 先建立基线和评测，再决定是否引入复杂组件。
3. 确定性业务逻辑保留在普通服务中，只有动态语义决策才使用 Agent。
4. 所有模型、Prompt、Embedding 和索引变更必须版本化。
5. 所有生产变更必须可观测、可灰度、可回滚。
6. 数据库结构只能由版本化迁移管理，禁止运行时自动改表。

## 4. 总体里程碑

| 阶段 | 周期 | 核心结果 | 上线关系 |
| --- | ---: | --- | --- |
| M0 基线定义 | 1 周 | SLO、威胁模型、现状基线、ADR | 前置 |
| M1 安全与数据基座 | 3～4 周 | 服务端鉴权、RBAC、Secret、Flyway、CI | P0，公开上线前必须完成 |
| M2 可靠性与可观测性 | 3～4 周 | 超时熔断、异步任务、Telemetry、告警 | P0/P1 |
| M3 Hybrid RAG | 4～5 周 | 评测集、pgvector、FTS、RRF、引用 | P1 |
| M4 状态化 Agent 与自适应学习 | 4～5 周 | Checkpoint、重规划闭环、Mastery | P1/P2 |
| M5 上线加固 | 2～3 周 | 压测、恢复演练、灰度、回滚、合规 | 正式发布门禁 |

### 当前实施进度（2026-08-21）

| 阶段 | 状态 | 已完成范围 | 交付证据 |
| --- | --- | --- | --- |
| M0 基线定义 | 已完成 | ADR、SLI/SLO、容量假设、威胁模型、Runbook | [`docs/production/sprint-1-evidence.md`](production/sprint-1-evidence.md) |
| M1 安全与数据基座 | 进行中 | JWT/Refresh Token、RBAC、资源归属、自助密码重置、SSRF 防护、密钥轮换、审计保留、Flyway、数据库最小权限、三端质量门禁与安全工作流 | [`docs/production/sprint-4-evidence.md`](production/sprint-4-evidence.md) |
| M2 可靠性与可观测性 | 进行中 | 分场景调用预算、截止时间与取消传播、有限重试、Bulkhead、熔断/半开恢复、数据库连接隔离、持久任务状态机、计划异步生成、W3C Trace Context、Agent/模型/数据库遥测、Prometheus/Grafana/Alertmanager 配置、Dashboard/告警规则与优雅停机 | [`docs/production/sprint-9-evidence.md`](production/sprint-9-evidence.md) |
| M3 Hybrid RAG | 进行中 | 版本化 pilot 与评测 CLI；安全摄取、版本化 Chunk、对象存储、pgvector、版本化 Embedding、持久批处理和 Dense Retrieval 降级路径 | [`docs/production/sprint-12-evidence.md`](production/sprint-12-evidence.md) |
| M4 状态化 Agent 与自适应学习 | 未开始 | — | — |
| M5 上线加固 | 未开始 | — | — |

记录规则：只有已经落地且具备测试、构建或演练证据的子项才标记为 `[x]`；部分完成的复合子项仍保留为 `[ ]`，并在进度记录中说明已完成范围。

本次核验结果：Java 82 tests（0 failures/errors，3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过）、Python 43/43、Frontend Vitest 5/5 与隔离目录生产构建通过；Ruff、Mypy、ESLint 通过；固定 regression 集连续两次排名一致，实际指标为 Recall@5 1.000、MRR 0.916667、NDCG@5 0.876743、空召回率 0。V10 结构契约、Embedding 响应校验、任务正文隔离、版本拒绝和 Dense 融合测试已通过。一次性 PostgreSQL 18 的既有证据已验证 Flyway V1～V7、数据库角色 ACL、基础 `SKIP LOCKED` 领取和幂等索引；V8～V10 迁移、pgvector/HNSW/ACL、Collector/Jaeger 端到端抓取、完整 OpenAPI、Promtool/Amtool、Dashboard/告警运行态、真实 S3/URL、真实 Embedding 供应商和远端 RAG regression CI 仍等待 Docker/CI/staging 首次成功证据。

---

## 5. M0：基线定义

### M0-ARCH-01 建立架构决策记录

- [x] 记录前端、后端、Agent、数据库的职责边界。
- [x] 确定身份认证采用 OIDC 还是自建 JWT。
- [x] 确定初期部署平台和高可用方式。
- [x] 确定 PostgreSQL 为业务 Schema 的唯一事实源。
- [x] 确定消息队列、Redis 的引入条件，不预先堆叠基础设施。

验收标准：

- `docs/adr/` 下存在经确认的架构决策记录。
- 每项决策包含背景、方案、取舍、风险和回滚方式。

### M0-SLO-01 定义 SLI/SLO

- [x] 定义普通 API、计划生成、RAG、Tutor 的延迟与成功率。
- [x] 定义 AI 降级率、空召回率、Token 成本指标。
- [x] 定义 RPO、RTO 和备份保留周期。
- [x] 定义容量假设：DAU、峰值 QPS、并发 AI 请求数。

验收标准：

- SLO 可由监控指标直接计算。
- 每个 SLO 有负责人、告警阈值和应急 Runbook。

### M0-SEC-01 完成威胁建模

- [x] 绘制外部用户、管理员、后端、Agent、数据库和模型服务的信任边界。
- [x] 分析越权、Prompt Injection、SSRF、密钥泄漏、日志泄漏和资源滥用。
- [x] 对威胁按影响与发生概率分级。
- [x] 将高风险威胁映射到 M1/M2 的具体任务。

验收标准：

- 所有公网入口和敏感数据流均被覆盖。
- 高风险问题有对应修复任务，且不能延后至公开上线之后。

---

## 6. M1：安全与数据基座

### M1-SEC-01 引入服务端身份认证

- [x] 添加 Spring Security 配置。
- [x] 实现 OIDC，或实现短期 Access Token + Refresh Token。
- [x] 增加登录失败限流、密码策略、密码重置与 Token 吊销。
- [x] 将认证用户写入统一安全上下文。
- [x] 禁止从请求体或查询参数中获取可信用户身份。

进度说明：登录失败限流、12 位密码策略、登录后改密、管理员重置、自助邮件找回、一次性重置 Token 和 Refresh Token 吊销已完成；生产启用时强制 HTTPS 重置链接与非 localhost SMTP。

依赖：M0-ARCH-01。

验收标准：

- 未登录请求无法访问受保护接口。
- 过期、伪造、吊销的凭证均被拒绝。
- 自动化测试覆盖登录、刷新、退出和失败路径。

### M1-SEC-02 实现服务端 RBAC 与资源归属校验

- [x] 定义 `student`、`admin` 权限矩阵。
- [x] 管理接口增加方法级授权。
- [x] 计划、学习日、练习、资源反馈全部按当前用户查询。
- [x] 删除或忽略前端提交的 `userId` 身份字段。
- [x] 为跨用户访问建立专门的越权测试集。

进度说明：权限矩阵见 `docs/security/access-control-matrix.md`；Controller HTTP 测试覆盖匿名、student、admin，可信身份测试覆盖计划、资源、反馈和练习入口，服务层测试覆盖跨用户学习日和练习记录。

验收标准：

- 修改浏览器 `localStorage` 不能获得管理员权限。
- 用户 A 无法读取、修改、删除用户 B 的资源。
- IDOR/BOLA 自动化测试全部通过。

### M1-SEC-03 保护 Agent 平台

- [x] FastAPI 不直接暴露公网。
- [x] 后端到 Agent 使用内部服务凭证或 mTLS。
- [x] 索引重建、模型配置、日志查询等管理接口只允许管理员或内部服务访问。
- [x] FastAPI CORS 改为明确白名单（当前实现为移除浏览器跨域入口，仅允许后端内部调用）。
- [x] 对外部 URL 获取能力增加 SSRF 防护和域名策略。

验收标准：

- 绕过 Spring Boot 不能直接调用敏感 Agent 接口。
- 未授权请求、过期凭证和错误来源均被拒绝并记录审计事件。

### M1-SEC-04 密钥与日志治理

- [x] 停止把 LLM API Key 写入 `llm_runtime.json`。
- [x] 使用部署平台 Secret、Vault 或云 Secret Manager。
- [x] 实现日志字段脱敏和最大 Payload 限制。
- [x] 禁止日志记录密码、Token、API Key、完整用户答案和敏感 Prompt。
- [x] 配置审计日志保留周期与访问权限。
- [x] 建立密钥轮换流程。

进度说明：Agent 调用日志已实现写入/读取双重脱敏、默认 4096 bytes 上限和 30 天保留过滤；`admin_audit_log` 默认保留 365 天、每日清理且仅 admin 可读；JWT 与 Agent 内部凭证支持当前/前一密钥重叠轮换。

验收标准：

- Git 仓库、镜像和运行目录中均不存在明文生产密钥。
- Secret 扫描通过。
- 日志抽查不包含敏感凭证和不必要的个人数据。

### M1-DATA-01 引入 Flyway

- [x] 为当前生产 Schema 创建基线迁移。
- [x] 将 JPA `ddl-auto` 改为 `validate`。
- [x] 生产环境关闭 `show-sql`。
- [x] Python Agent 停止通过 `create_all()` 创建业务表。
- [x] 为迁移增加前向兼容和失败回滚说明。

验收标准：

- 空数据库可以仅通过迁移构建完整 Schema。
- 已有数据库可以安全升级。
- 旧应用与扩展阶段 Schema 可以短期共存。

### M1-DATA-02 强化数据完整性和权限

- [x] 补充外键、唯一约束、非空约束和状态值约束。
- [x] 为高频查询建立索引并记录查询计划。
- [x] 后端和 Agent 使用不同数据库角色。
- [x] Agent 只获得业务所需的最小表权限。
- [x] 评估按 Schema 隔离 Agent 数据和业务数据。

验收标准：

- 数据库能够阻止孤儿数据和非法状态。
- 关键查询在目标数据规模下没有明显全表扫描。

### M1-CI-01 建立持续集成

- [ ] Java：编译、单测、Testcontainers 集成测试。
- [x] Python：格式检查、类型检查、`pytest`。
- [x] Frontend：Lint、单测、生产构建。
- [x] 增加 OpenAPI 契约兼容检查。
- [ ] 增加 SAST、SCA、Secret 扫描和容器扫描。
- [ ] 生成 SBOM。

进度说明：已新增 CodeQL 三语言 SAST、Trivy 依赖/Secret/配置扫描、Anchore SBOM 与三服务容器镜像扫描；Java Testcontainers 包含空库迁移和数据库 ACL 用例。因本机无 Docker且未推送远端，Java/Testcontainers 与安全扫描复合子项需首次远端成功后再勾选。

验收标准：

- 所有 PR 必须通过 CI 才能合并。
- CI 使用干净环境，不依赖开发者机器缓存。
- 失败步骤能够给出明确日志和复现命令。

---

## 7. M2：可靠性与可观测性

### M2-REL-01 设置服务调用预算

- [x] Java HTTP Client 配置连接、读取和整体请求超时。
- [x] Python LLM/Embedding Client 配置超时和连接池。
- [x] 为计划、RAG、Tutor 定义不同时间预算。
- [x] 客户端取消请求时向下游传播取消信号。
- [x] 配置优雅停机和连接耗尽时间。

验收标准：

- 下游不可用时请求不会无限挂起。
- 超时错误能够被监控区分，不被统一吞为普通降级。

进度说明：Java 使用共享连接池并按 ADMIN/RAG/TUTOR/PLAN/STREAM 配置读取与整体预算，通过 `X-LearnFlow-Timeout-Ms` 向 Agent 传递截止时间；FastAPI 为模型调用预留回退窗口并返回可区分的 `overall_timeout`。当前外部 Embedding Client 尚不存在，M3 接入时必须复用同一预算与连接池策略。浏览器显式取消的 SSE 链路会关闭 Java→Agent 和 Agent→模型连接；普通 AI 请求通过整体截止时间执行取消。Spring、Uvicorn 与 Compose 已配置 30s/40s 优雅停机窗口。证据见 Sprint 5。

### M2-REL-02 增加重试、熔断和隔离

- [x] 使用 Resilience4j 或等价方案实现有限重试。
- [x] 仅对幂等请求或带幂等键的操作重试。
- [x] 为 LLM、Agent 和数据库连接配置 Bulkhead。
- [x] 增加熔断器和半开恢复策略。
- [x] 记录重试次数、熔断状态和降级原因。

进度说明：Backend 使用 Resilience4j 按操作隔离 Agent 调用，仅管理 GET 允许最多 2 次有限尝试，所有尝试共享原始截止时间；生成、写入与流式调用不自动重试。FastAPI 模型出口使用异步并发隔离和熔断/半开探测，LLM POST 因无供应商幂等契约不重试。Hikari 与 SQLAlchemy 连接池均配置显式上限和获取超时。重试、状态转换、隔离拒绝和降级原因只记录结构化元数据。证据见 [`docs/production/sprint-6-evidence.md`](production/sprint-6-evidence.md)。

验收标准：

- 模型服务故障不会耗尽后端线程和连接。
- 故障注入测试能触发预期熔断与恢复。

### M2-ASYNC-01 建立异步任务模型

- [x] 确定消息队列实现。
- [ ] 将批量 Embedding、索引、评测和长时间计划任务迁入队列。
- [x] 定义 `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED` 状态。
- [x] 增加幂等消费、有限重试和死信队列。
- [x] 增加进度查询、取消和超时回收。

进度说明：Sprint 7 通过 ADR-0006 与 Flyway V7 落地 PostgreSQL 持久任务队列，计划生成已迁入队列；任务领取采用 `FOR UPDATE SKIP LOCKED` 短事务、租约恢复和至少一次投递，计划以 `source_task_id` 保证幂等。成功/取消清除 Payload，失败任务最多保留 7 天并仅允许管理员审计重放。Sprint 12 已将批量 Embedding 与版本 backfill 接入同一队列；批量评测尚未迁移，因此复合子项继续保留未完成。

验收标准：

- Worker 重启不会导致任务丢失或重复写入。
- 死信任务可以定位原因并安全重放。

### M2-OBS-01 贯通 OpenTelemetry

- [x] Gateway 生成或透传 Request ID/Trace ID。
- [ ] Spring Boot、FastAPI、数据库和外部模型调用加入 Span。
- [x] Agent 节点记录状态转换、模型和 Prompt 版本。
- [x] 记录 Token、费用、延迟、错误、超时、重试和降级。
- [x] 对用户内容和模型内容进行脱敏或摘要化记录。

进度说明：Sprint 8 已实现 Nginx Request ID、W3C `traceparent`、Spring/FastAPI/HTTPX/SQLAlchemy/Repository/任务/模型 Span、持久任务父 Trace 恢复、Token 与显式单价费用估算，以及低基数可靠性指标；敏感内容、异常消息和原始身份不进入遥测。因本机无 Docker，Collector + Jaeger 真实链路抓取和 V8 Testcontainers 迁移尚待远端首次成功，复合 Span 子项继续保留未完成。证据见 [`docs/production/sprint-8-evidence.md`](production/sprint-8-evidence.md)。

验收标准：

- 可以从一个失败页面请求追踪至具体 Agent 节点和下游调用。
- Trace 不包含密钥及不必要的个人数据。

### M2-OBS-02 建立监控、告警和 Runbook

- [ ] 建立 API、Agent、数据库、队列和成本 Dashboard。
- [ ] 监控 P50/P95/P99、错误率、饱和度和连接池。
- [ ] 监控 RAG 空召回、Validator 失败和 AI 降级率。
- [ ] 建立 SLO Burn Rate 告警。
- [x] 为数据库故障、模型故障、队列积压和成本异常编写 Runbook。

进度说明：Sprint 9 已补齐队列状态/年龄/租约/Worker、Agent 数据库池、RAG 空结果、Validator 结果、AI Token/费用的低基数指标，新增 Prometheus、Grafana、Alertmanager、五个 Dashboard、API 99.9% 多窗口 Burn Rate 与全套告警规则。所有告警均包含影响、负责人、Dashboard 和 Runbook；配置结构、指标隐私单测和三端质量门禁已通过。因本机无 Docker，Promtool/Amtool、Dashboard 实际加载、真实指标查询和 firing→路由→resolved 演练等待远端 CI/受控环境证据，相关复合子项继续保留未完成。证据见 [`docs/production/sprint-9-evidence.md`](production/sprint-9-evidence.md)。

验收标准：

- 告警包含影响范围、Dashboard、Runbook 和负责人。
- 演练时能够根据告警完成定位和恢复。

---

## 8. M3：Hybrid RAG

### M3-EVAL-01 建立 RAG 评测集

- [ ] 从真实学习场景整理 150～300 条查询。
- [ ] 标注相关资源、相关等级、用户水平、领域和任务类型。
- [x] 划分开发集、测试集和回归集。
- [x] 测量当前规则/哈希方案的 Recall@K、MRR、NDCG 和延迟。
- [x] 保存每次评测的代码、数据、模型和索引版本。

进度说明：Sprint 10 建立了 11 条冻结资源与 36 条查询的版本化 pilot、数据完整性/分集泄漏校验、纯代码指标 Runner 和 CI regression 入口。pilot 全部明确标记为 `pending_domain_review`，不是生产用户查询；在扩充到 150～300 条、完成人工复核并冻结阈值前，前两项继续保持未完成，也不以当前小资源池指标宣称 Hybrid RAG 效果。证据见 [`docs/production/sprint-10-evidence.md`](production/sprint-10-evidence.md)。

验收标准：

- 基线可以在 CI 或独立评测任务中稳定复现。
- 示例指标不能手工填写，必须由脚本真实计算。

### M3-RAG-01 建立资源摄取流水线

- [x] 支持 URL、文本和文档资源。
- [x] 实现抓取、解析、清洗、Chunk、去重和内容哈希。
- [x] 记录来源、语言、难度、领域、权限和有效状态。
- [x] 记录解析器、Chunk、Embedding 版本。
- [x] 文档更新时仅重建受影响 Chunk。
- [x] 对外部资源抓取增加安全与版权策略。

进度说明：Sprint 11 已记录 Parser/Chunk 版本并以稳定内容哈希复用未变化 Chunk；Sprint 12 通过 `embedding_model_version` 和 `(chunk_id, embedding_version)` 补齐 Embedding 版本、全量 backfill 与活动版本原子切换。原件通过文件系统开发适配或生产 S3 适配保存，任务 Payload 与遥测不含正文。公网抓取执行逐跳 SSRF、重定向、大小、MIME、超时和禁止索引检查，并要求提交人确认内容处理权限。

验收标准：

- 同一内容不会产生不可控重复 Chunk。
- 任一向量能够追溯到原始资源、文本和处理版本。

### M3-RAG-02 接入 pgvector Dense Retrieval

- [x] 通过 Flyway 安装和管理 `vector` 扩展。
- [x] 创建 Chunk 与 Embedding 表。
- [x] 建立 HNSW 索引。
- [x] 实现批量 Embedding、失败重试和版本迁移。
- [x] 检索前应用用户、状态、领域等权限过滤。

进度说明：Sprint 12 通过 V10、1536 维 cosine HNSW、持久 `RESOURCE_EMBEDDING` 任务和 `BUILDING/ACTIVE/RETIRED` 版本状态完成代码落地。Backend 保持向量写所有权，Agent 只读活动版本；新版本全量覆盖后原子切换，Dense 异常自动退回规则/哈希路径。当前资源权限模型以管理员审核后的 `ACTIVE` 为全局可推荐边界，并在 SQL 中同时过滤当前成功摄取版本、领域和学习水平。真实 V10/pgvector/ACL、供应商调用、目标规模延迟与质量提升仍需 staging/Docker 证据；因此不能据此宣称 Hybrid 已优于基线。证据见 [`docs/production/sprint-12-evidence.md`](production/sprint-12-evidence.md)。

验收标准：

- Dense Retrieval 在目标数据规模下满足延迟和召回要求。
- Embedding 模型升级可以并行重建并安全切换。

### M3-RAG-03 实现 Sparse Retrieval 与 RRF

- [ ] 使用 PostgreSQL Full Text Search 建立 Sparse 路径。
- [ ] 分别获取 Dense Top-K 和 Sparse Top-K。
- [ ] 使用 RRF 融合结果。
- [ ] 保留现有规则召回作为降级方案。
- [ ] 对不同领域和用户水平调优过滤策略。

验收标准：

- Hybrid 相比现有基线在测试集上有可重复的指标提升。
- 线上请求可以记录每个结果来自哪些召回通道。

### M3-RAG-04 二阶段重排与引用

- [ ] 先评估 Cross Encoder，再考虑 LLM Reranker。
- [ ] 为重排设置候选上限、延迟预算和成本预算。
- [ ] 返回命中 Chunk、来源 URL 和推荐理由。
- [ ] 对无证据回答和低置信度结果实现拒答或降级。
- [ ] 防止被检索内容中的 Prompt Injection 改写系统指令。

验收标准：

- 重排只有在评测收益超过延迟和成本代价时启用。
- 所有生成型回答能够提供可验证引用。

---

## 9. M4：状态化 Agent 与自适应学习

### M4-AGENT-01 建立显式状态模型

- [ ] 定义 `StudyPlanState` 及 Schema 版本。
- [ ] 节点拆分为 Goal、Schedule、Plan、Validate、Replan、Save。
- [ ] 定义节点输入、输出、超时、失败和重试策略。
- [ ] 将状态 Checkpoint 持久化到 PostgreSQL。
- [ ] 支持暂停、恢复、取消和幂等重放。

验收标准：

- 服务重启后可以从 Checkpoint 恢复未完成任务。
- 同一任务重放不会产生重复计划和重复学习日。

### M4-AGENT-02 打通 Validator → Replan 闭环

- [ ] Validator 失败时进入 Replan。
- [ ] 限制最大自动修复次数，建议不超过 2 次。
- [ ] 超过阈值时进入人工确认或明确失败状态。
- [ ] 保存每次修改前后的计划及验证报告。
- [ ] 对死循环、重复计划和负载漂移建立测试。

验收标准：

- 不合格计划不会直接保存为成功结果。
- 自动修复过程可追踪、可解释、可终止。

### M4-TOOL-01 引入受控 Tool Calling

- [ ] 只暴露检索计划、掌握度、错题、资源和保存练习等明确工具。
- [ ] 工具层执行参数校验、身份校验、超时和审计。
- [ ] 数据写入工具默认要求明确意图，敏感操作要求确认。
- [ ] 禁止模型直接执行任意 SQL、Shell 或外部 URL。
- [ ] 为 Tool Choice 和 Tool Result 建立评测。

验收标准：

- 模型不能绕过权限边界调用工具。
- 工具失败不会破坏 Agent 状态和业务事务。

### M4-LEARN-01 建立学习事件与 Mastery Profile

- [ ] 统一记录计划完成、练习作答、复习、资源反馈和延期事件。
- [ ] 建立用户－知识点掌握度表。
- [ ] 第一版采用可解释加权模型或 BKT。
- [ ] 保存计算版本、置信度和更新时间。
- [ ] 避免样本不足时产生过度确定的掌握度结论。

验收标准：

- 任一掌握度变化能够追溯到学习事件。
- 旧算法结果可以重新计算和迁移。

### M4-LEARN-02 让掌握度驱动计划

- [ ] 影响下一阶段任务难度。
- [ ] 影响复习间隔和错题优先级。
- [ ] 影响资源难度与展示顺序。
- [ ] 影响练习题型和覆盖知识点。
- [ ] 影响 Replan 的任务增删与时间分配。
- [ ] 建立 A/B 或准实验评估学习效果。

验收标准：

- 个性化结果不是只展示分数，而是真正改变后续计划。
- 能证明自适应策略相对固定策略的效果或行为差异。

---

## 10. M5：上线加固

### M5-DEPLOY-01 完善生产部署

- [ ] 建立 dev、staging、production 独立环境。
- [ ] 基础镜像固定版本或 Digest。
- [ ] 容器使用非 root 用户和只读文件系统。
- [ ] 配置 liveness、readiness 和优雅停机。
- [ ] 使用蓝绿或金丝雀发布。
- [ ] 数据迁移采用 Expand/Contract。
- [ ] 部署失败自动停止并允许一键回滚。

验收标准：

- 发布过程不依赖开发者本机状态。
- 应用和数据库变更均有回滚或前向修复方案。

### M5-PERF-01 容量与压力测试

- [ ] 对登录、计划查询、计划生成、RAG、Tutor 分别压测。
- [ ] 测量数据库连接池、Agent 并发、队列和模型配额上限。
- [ ] 建立峰值流量和突发流量模型。
- [ ] 验证限流、背压、降级和恢复行为。
- [ ] 输出容量报告和扩容阈值。

验收标准：

- 在目标峰值的 1.5～2 倍下系统能够受控降级。
- 不出现线程、连接、内存或队列无限增长。

### M5-DR-01 灾难恢复演练

- [ ] 验证自动备份和 PITR。
- [ ] 演练数据库误删、数据库不可用和区域故障。
- [ ] 演练模型供应商不可用。
- [ ] 演练队列积压和 Worker 全部重启。
- [ ] 记录实际 RPO、RTO 和改进项。

验收标准：

- 恢复演练达到约定的 RPO/RTO。
- Runbook 由非开发者本人也可以执行。

### M5-RELEASE-01 正式上线门禁

- [ ] 功能、权限、契约和 AI 回归测试通过。
- [ ] 无 Critical/High 未处理安全漏洞。
- [ ] 数据迁移在 staging 完整演练。
- [ ] 备份恢复、回滚和故障演练通过。
- [ ] Dashboard、告警和 Runbook 就绪。
- [ ] 成本预算和费用告警就绪。
- [ ] 数据保留、删除和隐私说明完成。
- [ ] 发布负责人、观察窗口和回滚负责人明确。

验收标准：

- 所有门禁有自动化证据或演练记录。
- 不允许用口头确认替代测试、扫描和恢复报告。

---

## 11. 测试矩阵

| 层级 | 必须覆盖内容 |
| --- | --- |
| Java 单元测试 | 权限规则、计划状态、重规划、推荐分数、异常映射 |
| Java 集成测试 | PostgreSQL、Flyway、资源归属、事务、Agent 客户端降级 |
| Python 单元测试 | Agent 节点、状态迁移、Retriever、RRF、Reranker、降级 |
| 契约测试 | Spring Boot 与 FastAPI 请求/响应 Schema |
| 前端单元测试 | Auth Store、API 错误处理、关键 Composable |
| E2E | 注册登录、生成计划、执行任务、资源推荐、练习、管理员流程 |
| 安全测试 | IDOR/BOLA、RBAC、Token、限流、SSRF、Prompt Injection |
| 性能测试 | API、Agent 并发、数据库、队列积压、模型超时 |
| AI Evals | Goal、Plan、RAG、Tutor、Tool Calling、回归集 |

建议门槛：

- 核心领域服务分支覆盖率 ≥ 80%。
- 权限和资源归属路径必须 100% 覆盖关键角色组合。
- 覆盖率不能替代异常、并发和故障场景测试。

## 12. 关键监控指标

### API

- 请求量、错误率、P50/P95/P99。
- 401/403、429、超时和取消数量。
- 数据库连接池、慢查询、事务回滚。

### Agent 与模型

- Agent 节点耗时和失败率。
- LLM/Embedding 模型、Prompt 版本和 Token。
- 单次请求成本、每日成本、预算消耗。
- 超时、重试、熔断、规则降级率。
- Validator 不通过率和 Replan 次数。

### RAG

- Dense/Sparse 候选数。
- RRF 排名贡献。
- 空召回率、引用覆盖率。
- Recall、MRR、NDCG 回归。
- 索引延迟、失败数和 Embedding 队列积压。

### 学习效果

- 计划完成率和延期率。
- 练习正确率、复习后正确率。
- Mastery 变化与置信度。
- 推荐点击、完成、评分和失效反馈。

## 13. 风险登记

| 风险 | 影响 | 缓解措施 |
| --- | --- | --- |
| 同一数据库由 JPA 与 SQLAlchemy 自动建表 | Schema 漂移、发布失败 | Flyway 单一管理，ORM 仅验证/映射 |
| LLM 请求长时间占用同步线程 | 级联故障 | 超时、Bulkhead、队列、取消 |
| Agent 日志保存完整 Payload | 隐私泄漏、数据库膨胀 | 脱敏、摘要、采样、TTL |
| 引入框架过早 | 复杂度上升、交付延迟 | 以用例和评测作为引入门槛 |
| RAG 只有演示数据 | 指标失真 | 建立真实标注集和独立测试集 |
| 用户反馈形成错误强化 | 推荐偏差 | 置信度、时间衰减、异常反馈治理 |
| 模型供应商故障或涨价 | 服务中断、成本失控 | 模型网关、配额、降级、预算告警 |
| 单机 Docker Compose 故障 | 整站不可用 | 托管数据库、双副本、备份和恢复演练 |

## 14. 前两个 Sprint 建议

### Sprint 1

- [x] M0-ARCH-01 架构决策记录。
- [x] M0-SLO-01 SLI/SLO。
- [x] M0-SEC-01 威胁模型。
- [x] M1-CI-01 最小 CI：Java/Python/Frontend 构建。
- [x] M1-SEC-01 Spring Security 骨架。
- [x] M1-DATA-01 Flyway 基线设计。

Sprint 1 交付物：生产目标、威胁模型、可复现构建、认证技术方案和数据库迁移方案。

### Sprint 2

- [x] 完成登录、Token/OIDC 和注销。
- [x] 完成管理员 RBAC。
- [x] 完成计划、学习日、练习和资源的归属校验。
- [x] 保护 FastAPI 内部接口。
- [x] API Key 迁移到 Secret。
- [x] Flyway 基线迁移落地。
- [x] 添加权限隔离集成测试。

Sprint 2 交付物：不能通过前端篡改身份、不能跨用户访问、不能匿名修改模型或索引。

### Sprint 3

- [x] 建立 student/admin 权限矩阵与 Controller 级 RBAC 测试。
- [x] 补齐计划、学习日、练习、资源入口的可信身份与越权测试。
- [x] 为 Refresh/Logout Cookie 增加白名单 Origin 防护。
- [x] Agent 日志改为脱敏摘要、限长和保留期过滤。
- [x] 通过 Flyway V3 清理历史 Agent Payload。
- [x] 增加 CodeQL、Trivy 与 SBOM 工作流配置。
- [ ] 在远端 GitHub Actions 完成安全工作流首次通过，并补充容器镜像扫描。

Sprint 3 交付物：权限边界具备自动回归证据，Cookie 会话不接受跨站请求，Agent 日志不再保存完整用户或模型内容，安全扫描具备可执行工作流。

### Sprint 4

- [x] 完成未登录用户自助密码找回、一次性 Token 与前端重置流程。
- [x] 完成 LLM 出站地址 SSRF 防护、生产域名白名单和安全资源 URL 校验。
- [x] 完成 JWT/Agent 内部凭证双密钥轮换与 Runbook。
- [x] 完成管理审计日志保留、限长、清理和访问权限。
- [x] 完成 migrator、Backend、Agent 数据库角色和 Agent 最小权限设计。
- [x] 通过 V6 补齐 baseline 旧库索引，并记录事务内查询计划证据。
- [x] 增加 Ruff、Mypy、ESLint、Vitest 与 OpenAPI 必需路径门禁。
- [ ] 在远端 Docker 环境通过 Testcontainers 数据库 ACL 测试和三镜像 Trivy 扫描。

Sprint 4 交付物：M1 安全与数据项本地收口，三端质量门禁可执行；剩余阻塞只涉及远端 Docker/Actions 首次成功证据。

### Sprint 5

- [x] 完成 Java→Agent 分场景连接、读取与整体调用预算。
- [x] 完成 Agent→模型共享异步连接池和嵌套截止时间。
- [x] 完成 SSE 取消传播、超时分类与优雅停机。

Sprint 5 交付物：M2-REL-01 全部完成；证据见 [`docs/production/sprint-5-evidence.md`](production/sprint-5-evidence.md)。

### Sprint 6

- [x] 完成 Agent 调用有限重试、幂等边界与共享截止时间。
- [x] 完成 Backend 分操作 Bulkhead、熔断与半开恢复。
- [x] 完成 FastAPI 模型出口并发隔离、熔断与半开恢复。
- [x] 完成 Backend/Agent 数据库连接池边界。
- [x] 完成重试、饱和拒绝、熔断和恢复的故障注入测试。

Sprint 6 交付物：M2-REL-02 全部完成；证据见 [`docs/production/sprint-6-evidence.md`](production/sprint-6-evidence.md)。

### Sprint 7

- [x] 确定 PostgreSQL 持久任务队列与独立消息中间件升级门槛。
- [x] 建立任务状态、租约、有限重试、死信、取消和超时回收。
- [x] 将用户侧长时间计划生成迁入异步任务并保留同步回滚路径。
- [x] 通过任务 ID、请求指纹和 `study_plan.source_task_id` 保证提交与消费幂等。
- [x] 完成所有者任务 API、管理员死信重放、审计和前端进度/取消流程。
- [ ] 在远端 Docker 环境通过 V7、数据库 ACL 与过期租约 `SKIP LOCKED` 集成测试。

Sprint 7 交付物：M2-ASYNC-01 的任务基座与长计划迁移完成；批量 Embedding、持久索引和批量评测留待 M3 实现后接入。证据见 [`docs/production/sprint-7-evidence.md`](production/sprint-7-evidence.md)。

### Sprint 8

- [x] Nginx 生成受信 Request ID，Backend/Agent 返回 Request ID 与有效 Trace ID。
- [x] Backend→Agent 传播 W3C Trace Context，并为持久任务保存与恢复父 Trace。
- [x] Spring HTTP、Repository、队列租约、计划持久化和 Agent 调用建立 Span/指标。
- [x] FastAPI、HTTPX、SQLAlchemy、Agent 节点与模型调用建立 Span/指标。
- [x] 记录 Prompt 版本、Token、显式单价费用估算、超时、重试、熔断和降级原因。
- [x] 禁止遥测记录 Prompt、回答、SQL 参数、异常消息、凭证与原始用户身份。
- [x] 增加可选 Collector + Jaeger Profile、Trace 排障 Runbook 与传播/脱敏测试。
- [ ] 在远端 Docker 环境完成 V8 迁移和页面→Backend→任务→Agent→模型/数据库端到端 Trace 抓取。

Sprint 8 交付物：M2-OBS-01 代码与本地自动化证据完成，运行态端到端抓取等待远端 Docker/CI；Dashboard、Burn Rate 告警和告警路由留待 M2-OBS-02。证据见 [`docs/production/sprint-8-evidence.md`](production/sprint-8-evidence.md)。

### Sprint 9

- [x] 补齐队列状态、最老等待、过期租约、Worker 容量和指标新鲜度。
- [x] 补齐 Agent 数据库池、RAG 空结果和 Validator 结果指标，并保持标签低基数。
- [x] 新增 Prometheus、Grafana、Alertmanager 与五个自动预置 Dashboard。
- [x] 新增 API P50/P95/P99、99.9% 多窗口 Burn Rate、AI、数据库、队列、质量和成本告警规则。
- [x] 为每条告警配置负责人、影响、Dashboard、Runbook 和严重度路由。
- [x] 完成观测平台与成本异常 Runbook，并补强 API、AI、数据库和队列处置入口。
- [x] 增加观测资产契约、Promtool/Amtool CI 门禁和基础设施镜像扫描。
- [ ] 在远端 Docker/受控环境完成 Dashboard 数据验证和 firing→路由→确认→resolved 演练。
- [ ] 为生产 Alertmanager 挂载真实值班接收器并验证送达、抑制与恢复通知。

Sprint 9 交付物：M2-OBS-02 的指标、Dashboard、规则、路由骨架、Runbook 与 CI 配置完成；运行态 Dashboard/告警闭环和生产接收器等待远端环境证据。证据见 [`docs/production/sprint-9-evidence.md`](production/sprint-9-evidence.md)。

### Sprint 10

- [x] 为现有规则/哈希检索器建立冻结资源快照与显式检索器、索引版本。
- [x] 建立 RAG 数据集 Manifest、相关等级、dev/test/regression 分集和完整性校验。
- [x] 实现 Recall@K、MRR、NDCG@K、空召回率和延迟的可复现 CLI 报告。
- [x] 增加手算指标、数据错误、分集泄漏、数据库隔离和排名确定性测试。
- [x] 在 Agent CI Job 增加固定 regression 集评测。
- [ ] 将数据集扩充至 150～300 条真实学习场景，完成领域复核并冻结正式阈值。
- [ ] 在远端 GitHub Actions 获得 regression 评测首次成功证据。

Sprint 10 交付物：M3-EVAL-01 的评测代码、版本化 pilot、可复现报告与 CI 入口完成；真实数据规模、人工复核和正式阈值仍未关闭。证据见 [`docs/production/sprint-10-evidence.md`](production/sprint-10-evidence.md)。

### Sprint 11

- [x] 建立 URL、直接文本和文档摄取 API 与前端入口。
- [x] 建立安全抓取、Tika 解析、清洗、确定性 Chunk、内容哈希和去重。
- [x] 通过 V9 建立不可变摄取版本、可复用 Chunk 和版本顺序/偏移映射。
- [x] 将资源摄取迁入 PostgreSQL 持久任务队列并保持 Payload 只含资源引用。
- [x] 建立文件系统开发存储与 S3 兼容生产存储，production 拒绝本地存储。
- [x] 增加版权确认、禁止索引信号、SSRF/DNS/重定向/大小/MIME 防护。
- [x] 增加低基数摄取指标、状态 API、管理端重新摄取和故障 Runbook。
- [ ] 在远端 Docker/staging 验证 V9、Agent ACL、真实 S3 和受控公网 URL 端到端摄取。

Sprint 11 交付物：M3-RAG-01 的安全摄取、版本化 Chunk 与持久任务基座完成；其后 Sprint 12 已补齐 Embedding 版本，Sparse Retrieval、RRF 与引用仍留在后续 Sprint。证据见 [`docs/production/sprint-11-evidence.md`](production/sprint-11-evidence.md)。

### Sprint 12

- [x] 通过 Flyway V10 管理 pgvector、Embedding 模型版本、Chunk 向量和 HNSW。
- [x] 将批量 Embedding 与版本 backfill 接入 PostgreSQL 持久任务队列。
- [x] 完成 Chunk/版本幂等写入、有限重试、取消、死信和截止时间复用。
- [x] 完成 BUILDING 全量覆盖检查、活动版本事务切换和旧版本查询兼容。
- [x] 完成 FastAPI 内部 Embedding 入口、活动模型查询向量和 Dense SQL。
- [x] 完成审核状态、当前摄取版本、领域和学习水平过滤及规则/哈希降级。
- [x] 完成版本/维度/有限值校验、低基数指标、Trace 和正文隔离测试。
- [ ] 在远端 Docker/staging 运行 V10、pgvector/HNSW/ACL、真实供应商、版本切换和目标规模性能/质量验证。

Sprint 12 交付物：M3-RAG-02 代码、配置、测试和运维文档完成；真实 pgvector 与 Embedding 供应商的运行态证据仍待远端环境关闭。M3-RAG-03 的 FTS、Sparse Retrieval 与 RRF 留待后续 Sprint。证据见 [`docs/production/sprint-12-evidence.md`](production/sprint-12-evidence.md)。

## 15. 暂不优先事项

- 不为了技术栈数量立即引入 Kubernetes。
- 不继续增加新的 Agent 类。
- 不在没有评测集时直接宣称 Hybrid RAG 效果提升。
- 不在没有状态恢复需求时先整体迁移 LangGraph。
- 不在没有动态工具选择需求时强行加入 Tool Calling。
- 不在没有真实外部知识源时优先接入 MCP。
- 不在没有缓存、限流或分布式状态需求时引入 Redis。
- 不在样本量不足时直接实现 DKT。

## 16. Definition of Done

任何生产任务只有同时满足以下条件才能完成：

- [ ] 代码通过评审。
- [ ] 正常、异常、权限和回归测试通过。
- [ ] 数据库变更包含版本化迁移。
- [ ] 新增配置支持安全注入且有示例说明。
- [ ] 新增外部调用具有超时、取消和错误分类。
- [ ] 新增功能具有日志、指标和 Trace。
- [ ] 文档、Runbook 和回滚方案已更新。
- [ ] CI、漏洞扫描和发布门禁通过。
- [ ] 不记录不必要的个人数据或敏感凭证。
- [ ] 相关 SLO 和 AI Eval 没有出现不可接受回归。
