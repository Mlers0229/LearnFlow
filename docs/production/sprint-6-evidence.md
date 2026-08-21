# Sprint 6 M2 故障隔离交付证据

- 日期：2026-08-21
- 范围：M2-REL-02 有限重试、Bulkhead、熔断与半开恢复

## 已完成实现

- Backend 使用 Resilience4j 按 `ADMIN/RAG/TUTOR/PLAN/STREAM` 分别建立并发隔离与熔断器。
- 只有明确无业务副作用的 Agent 管理 GET 请求允许有限重试；默认最多 2 次尝试、间隔 100ms，仅重试连接/读取/I/O 故障及 502/503/504。
- 计划、Tutor、RAG 生成、管理写操作和 SSE 不自动重试，避免重复写入、重复模型调用和重复计费。
- 重试的所有尝试共享原调用截止时间，每次向下游传递剩余预算；整体超时、取消、序列化错误、4xx、Bulkhead 拒绝与熔断拒绝均不会重试。
- Backend 熔断器默认在至少 5 次调用、失败率达到 50% 后开启，20 秒后允许 2 个半开探测调用；只把瞬时网络故障、整体超时和 5xx 计为熔断失败。
- FastAPI 模型出口使用异步 Semaphore 隔离，默认最多 12 个并发模型调用、等待 100ms；连续 5 次瞬时故障后熔断，20 秒后进入单调用半开探测。
- LLM POST 在供应商未提供幂等契约时不做自动重试；Bulkhead 饱和、熔断开启和模型故障以不包含 Prompt/响应正文的结构化原因记录，并沿用现有规则降级。
- Backend Hikari 连接池显式限制为 15 条连接、最少 2 条空闲连接、3 秒获取超时；Agent SQLAlchemy 连接池限制为 5 条连接、无溢出连接、5 秒获取超时，并启用 `pool_pre_ping`。
- `.env.example`、Spring 配置与 Compose 已包含全部可靠性和连接池参数，默认值可由部署环境覆盖。

## 默认隔离参数

| Backend 操作 | 最大并发调用 |
| --- | ---: |
| ADMIN | 4 |
| RAG | 8 |
| TUTOR | 8 |
| PLAN | 4 |
| STREAM | 6 |

这些上限位于共享 OkHttp 30 连接总上限以内；后续由 M5 容量测试根据真实模型配额和延迟分布调优。

## 自动化验证

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 50 tests，0 failures/errors，2 个 Testcontainers 用例因本机无 Docker 跳过 |
| Agent `pytest` | 24 passed |
| Agent Ruff | `app` 与 `tests` 通过 |
| Agent Mypy | 33 个 `app` 源文件通过 |
| Frontend ESLint | 0 errors，24 个既有非阻断 warnings |
| Frontend Vitest | 1 passed |
| Frontend production build | 隔离输出目录构建通过，2851 modules transformed |
| Compose 配置 | PyYAML 解析通过，四个服务存在 |

专项测试覆盖：瞬时 GET 故障重试、POST 不重试、整体预算不超限、取消不重试、Bulkhead 饱和快速拒绝、熔断开启、OPEN 拒绝、HALF_OPEN 探测恢复、模型并发隔离和非瞬时错误不触发熔断。

## 环境限制与后续项

- 本机仍无 Docker，Sprint 4 的 Testcontainers 数据库 ACL 与三镜像扫描需在远端 GitHub Actions 首次成功后才能勾选。
- 本 Sprint 只提供结构化可靠性事件；跨服务 Metric/Trace、Dashboard 与告警由 M2-OBS-01/02 完成。
- 消息队列与长任务迁移仍按 M2-ASYNC-01 单独设计，不在本 Sprint 引入。
