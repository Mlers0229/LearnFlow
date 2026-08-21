# Sprint 5 M2 调用预算交付证据

- 日期：2026-08-21
- 范围：M2-REL-01 服务调用预算、取消传播和优雅停机

## 已完成实现

- Backend 新增专用 Agent HTTP Client，共享最多 30 条连接并保留 10 条空闲连接；连接、写入、读取和整体调用均有独立上限。
- ADMIN、RAG、Tutor、计划与 Chat SSE 使用不同预算；每次调用通过 `X-LearnFlow-Timeout-Ms` 向 FastAPI 传递剩余时间。
- 调用失败分类为连接超时、读取超时、整体超时、取消、下游 HTTP、序列化和普通 I/O 错误；结构化日志不记录请求或响应正文。
- SSE 在浏览器输出断开时立即取消 Java→Agent 调用；FastAPI 流生成器检测断开并关闭共享模型流。
- Agent LLM 调用链改为异步，FastAPI lifespan 管理共享 `httpx.AsyncClient`；默认连接池上限 30、Keep-Alive 连接 10。
- Agent 为上游和本层降级分别保留响应窗口；超过整体预算返回 `504` 和 `reason=overall_timeout`，模型超时不会被伪装成普通成功。
- Spring Boot 开启 graceful shutdown，单阶段耗尽上限 30 秒；Uvicorn 为 30 秒；Compose 为 Backend 和 Agent 配置 40 秒停止宽限期。

## 初始预算

| 操作 | Backend 整体预算 | Backend 读取/空闲上限 |
| --- | ---: | ---: |
| ADMIN | 10s | 10s |
| RAG | 15s | 15s |
| TUTOR | 45s | 45s |
| PLAN | 90s | 60s |
| STREAM | 300s | 60s 空闲 |

Agent 的模型调用默认连接 5 秒、读取 60 秒、写入 15 秒、连接池等待 5 秒；具体调用仍受上游剩余整体预算约束。

## 自动化验证

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 45 tests，0 failures/errors，2 个 Testcontainers 用例因本机无 Docker 跳过 |
| Agent `pytest` | 21 passed |
| Agent Ruff | 通过 |
| Agent Mypy | 32 个源文件通过 |
| Frontend ESLint | 0 errors，24 个既有非阻断 warnings |
| Frontend Vitest | 1 passed |
| Frontend production build | 隔离输出目录构建通过，2851 modules transformed |
| Compose 配置 | Docker CLI 不可用；使用 PyYAML 完成语法解析 |

专项测试覆盖：预算请求头、JSON 响应、整体超时分类、SSE 上游断开、共享 HTTP Client 生命周期、嵌套截止时间、模型超时、异步任务取消、FastAPI 504 分类以及计划规则回退契约。

## 环境限制与未完成项

- 本机仍无 Docker，因此 Sprint 4 的 Testcontainers 数据库 ACL 和三镜像扫描证据仍需远端 GitHub Actions 完成。
- Frontend 默认 `dist/assets` 被本机其他进程占用；本次未删除该目录，改用 `tmp/sprint5-frontend-dist-20260821` 完成等价生产构建。
- M2-REL-02 的重试、熔断和隔离尚未开始；本 Sprint 不对非幂等请求自动重试。
