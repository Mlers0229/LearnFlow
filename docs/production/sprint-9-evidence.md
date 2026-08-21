# Sprint 9 M2 监控、告警与 Runbook 交付证据

- 日期：2026-08-21
- 范围：M2-OBS-02 指标补齐、Prometheus/Grafana/Alertmanager、SLO Burn Rate、Dashboard、告警元数据与运维手册

## 指标与隐私边界

- Backend 定时缓存 `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED` 数量、最老等待时间、过期租约和快照新鲜度，Prometheus 抓取线程不会直接执行数据库查询。
- Worker 暴露配置容量与可用执行槽；Spring Boot 继续通过 Micrometer 导出 HTTP 分布直方图和 HikariCP 指标。
- Agent 暴露 SQLAlchemy 连接池、RAG 成功/空结果、Validator 有效/无效及问题数量直方图。
- 模型调用、降级、Token 和显式单价费用沿用 Sprint 8 指标。
- 所有新增标签均来自固定状态集合；未加入用户、查询、Prompt、计划、异常正文或资源内容。

## 可观测性平台

- Compose `observability` Profile 新增 Prometheus 3.13.1、Alertmanager 0.32.1 和 Grafana 13.1.0，保留 Collector 与 Jaeger。
- Collector 的 Prometheus Exporter 将受控 Resource 属性转换为指标标签，以 `service_name` 区分 Backend 与 Agent。
- Prometheus 每 15 秒抓取 Collector，使用 30 天本地保留策略，并加载 Recording/Alerting Rules。
- Grafana 自动加载五个只读 Dashboard：API/SLO、Agent/模型、数据库、队列和成本。
- Grafana、Prometheus、Alertmanager、Jaeger、Collector 管理端口默认只绑定 `127.0.0.1`；Grafana 禁止匿名访问和自助注册。
- 本地 Alertmanager 按 `critical`/`warning` 路由并提供 UI 演练，但不包含外部通知 Secret；生产必须挂载平台专用接收器。

## SLO 与告警

- API Recording Rules 计算请求速率、5xx 比例、P50/P95/P99，以及 5m/30m/1h/6h 的 99.9% Error Budget Burn Rate。
- AI Rules 计算 15 分钟模型成功率、降级率、RAG 空结果率和 Validator 失败率。
- 告警覆盖 API 快/慢燃烧、延迟、AI 成功与降级、Backend/Agent 数据库池、队列积压/死信/租约/指标新鲜度、RAG/Validator 质量和 24 小时费用。
- 每条告警均包含 `severity`、`owner`、`impact`、`dashboard` 和 `runbook`。
- 成本 USD 25/24h、RAG 50% 和 Validator 25% 均为低流量阶段的临时阈值，上线前必须以冻结基线和获批预算替换。

## Runbook 与供应链

- 新增可观测性平台启停、健康检查、合成告警演练、生产路由要求和回滚步骤。
- 新增 AI 成本异常定位、限流/降级、账单核验和升级流程。
- API、AI、数据库、队列 Runbook 增加负责人、Dashboard 与告警映射。
- 开源组件清单已记录 OTel、Prometheus、Alertmanager、Grafana 与 Jaeger。
- 安全工作流新增五个固定版本基础设施镜像的 Trivy High/Critical 扫描。
- CI 增加 Dashboard/告警元数据检查、`promtool check config` 和 `amtool check-config`。

## 本地自动化验证

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 70 tests，0 failures/errors，3 个 Testcontainers 用例因本机无 Docker 跳过 |
| Agent `pytest` | 31 passed |
| Agent Ruff | `app` 与 `tests` 通过 |
| Agent Mypy | 34 个 `app` 源文件通过 |
| Frontend ESLint | 0 errors，24 个既有非阻断 warnings |
| Frontend Vitest | 3 passed |
| Frontend production build | 隔离输出目录构建通过，2851 modules transformed |
| 配置结构 | 6 个观测 YAML、5 个 Dashboard JSON、安全工作流与 Compose 解析通过 |
| 观测资产契约 | 5 个固定 UID Dashboard、告警必需元数据和 Compose 服务引用通过 |

专项测试覆盖队列状态快照、数据库采集失败时的新鲜度降级、RAG 空结果标签和 Validator 结果不携带内容。

## 尚未关闭的范围

- 本机没有 Docker CLI/Daemon，无法实际启动 Prometheus/Grafana/Alertmanager，也无法运行容器内 `promtool`/`amtool`；上述步骤已加入 CI，等待首次远端成功日志。
- Dashboard 尚需在真实 OTLP 指标下确认所有查询产生数据，并抓取 API、Agent、数据库、队列和成本五类截图。
- SLO 告警尚需完成一次合成 firing→路由→确认→resolved 演练，并记录发现/确认/恢复时间。
- 仓库接收器只用于本地 UI。正式发布前必须接入真实值班渠道并验证送达、抑制和恢复通知。
- 因此除已有 Runbook 外，M2-OBS-02 的复合子项继续保持未完成，不以静态配置替代运行证据。

## 运维入口

- 平台启停、健康检查与告警演练：[`docs/runbooks/observability-stack.md`](../runbooks/observability-stack.md)
- API、AI、队列、数据库与成本处置：[`docs/runbooks/`](../runbooks/)
