# Sprint 8 M2 分布式可观测性交付证据

- 日期：2026-08-21
- 范围：M2-OBS-01 Request ID、W3C Trace Context、Spring/FastAPI/数据库/Agent/模型 Span、AI 使用量指标与脱敏策略

## 链路设计

- Nginx 在公网信任边界生成 `X-Request-Id`，Backend 和 Agent 只接受长度与字符集受限的关联 ID。
- Spring Boot 使用 Micrometer Tracing + OpenTelemetry OTLP；FastAPI 使用 OpenTelemetry SDK，并自动覆盖 FastAPI、HTTPX 与 SQLAlchemy。
- Backend 到 Agent 使用 W3C `traceparent`；响应暴露 `X-Request-Id` 与有效时的 `X-Trace-Id`。
- V8 为持久任务增加受约束的 `traceparent` 和 `request_id`。Worker 恢复远端父上下文后建立 Consumer Span，使任务跨事务、跨线程和重试后仍可关联提交请求。
- Spring Repository、任务租约和计划持久化具有数据库阶段 Span；Span 不保存 SQL 参数或实体内容。
- StudyPlan 编排记录 Goal、Scheduler、Plan、Validator；RAG、Tutor、Replan 和 DetailPlan 记录显式 Agent 节点与 Prompt 版本。

## AI 与可靠性遥测

- 模型 Span 记录模型安全标签、Prompt 版本、结果、超时和降级原因。
- 供应商响应包含 `usage` 时记录输入、输出和总 Token；没有 Usage 时明确标记不可用，不伪造数值。
- 费用只有在显式配置输入/输出每百万 Token 单价后才生成估算指标，默认值为 0 且不输出虚假费用。
- Backend 记录 Agent 调用耗时和结果，以及重试、Bulkhead 拒绝、熔断打开/半开/关闭事件。
- Agent 记录模型 Bulkhead、熔断拒绝和状态转换。

## 隐私与基数控制

- 不把 Prompt、答案、聊天正文、学习目标、SQL 参数、异常消息、凭证或原始用户身份写入 Span/指标。
- 模型名称、操作、错误类型、Prompt 版本和降级原因均限制为受控或安全标签。
- 异常只记录类型，不向遥测后端复制可能含输入片段的异常消息或堆栈事件。
- Actuator 只有 `/actuator/health` 匿名可用，其余 `/actuator/**` 要求管理员角色。

## 本地自动化验证

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 68 tests，0 failures/errors，3 个 Testcontainers 用例因本机无 Docker 跳过 |
| W3C 传播测试 | Backend→Agent `traceparent`、Request ID 传播通过 |
| 持久任务上下文测试 | 捕获、持久格式、Worker 恢复父 Trace 与 MDC 通过 |
| Agent `pytest` | 29 passed |
| Agent Ruff | `app` 与 `tests` 通过 |
| Agent Mypy | 34 个 `app` 源文件通过 |
| Frontend ESLint | 0 errors，24 个既有非阻断 warnings |
| Frontend Vitest | 3 passed |
| Frontend production build | 隔离输出目录构建通过，2851 modules transformed |
| Compose/Collector YAML | 结构解析通过，observability Profile、trace/metrics pipeline 存在 |

专项测试覆盖：不安全 Request ID 替换、W3C 上下文传播、持久任务父 Trace 恢复、模型 Usage/显式费用计算，以及敏感内容不进入遥测属性。

## 尚未关闭的范围

- 本机没有 Docker CLI/Daemon，无法启动 Collector + Jaeger 或执行 V8 Testcontainers 迁移；需要远端 Docker/CI 完成一次端到端 Trace 抓取后，才能证明页面失败请求在真实运行态贯通全部阶段。
- Collector 的 Prometheus Exporter 只提供本地查询入口。Dashboard、SLO Burn Rate 告警、负责人和告警路由属于 M2-OBS-02，未在本 Sprint 虚假完成。
- 本地 Jaeger 使用内存存储且仅绑定回环地址，不是生产部署方案；生产必须补充认证、TLS、持久存储和保留策略。

## 运维入口

排障与回滚步骤见 [`docs/runbooks/distributed-tracing.md`](../runbooks/distributed-tracing.md)。
