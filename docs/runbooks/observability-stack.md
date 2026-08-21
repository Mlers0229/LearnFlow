# 可观测性平台 Runbook

## 责任与适用范围

- 默认负责人：Platform；API 指标由 Backend 负责人协同，Agent/模型指标由 Agent 负责人协同。
- 适用于 Prometheus、Grafana、Alertmanager、OpenTelemetry Collector 或指标采集链路异常。
- 本仓库的 `observability` Profile 面向本地与受控演练。生产环境必须配置 TLS、认证、持久备份、访问控制和真实通知接收器。

## 启动与入口

先设置独立的 Grafana 管理密码，再启动观测链路：

```powershell
$env:LEARNFLOW_OTEL_ENABLED = "true"
$env:GRAFANA_ADMIN_PASSWORD = "使用 Secret 注入的强密码"
docker compose --profile observability up -d --build
```

默认入口均只绑定回环地址：

- Grafana：`http://127.0.0.1:3000`
- Prometheus：`http://127.0.0.1:9090`
- Alertmanager：`http://127.0.0.1:9093`
- Jaeger：`http://127.0.0.1:16686`
- Collector Prometheus Exporter：`http://127.0.0.1:9464/metrics`

Grafana 会自动加载 API/SLO、Agent/模型、数据库、队列和成本五个 Dashboard。Prometheus 每 15 秒抓取 Collector，并每 30 秒计算 LearnFlow Recording Rules。

## 健康检查

1. `docker compose --profile observability ps`：确认四个观测服务和 Jaeger 均在运行。
2. Prometheus `/targets`：`learnflow-otel-collector` 必须为 `UP`。
3. Prometheus `/rules`：`learnflow-slo-recording` 与 `learnflow-alerts` 均成功加载。
4. Alertmanager `/status`：配置加载成功，Prometheus Alertmanager Discovery 可见。
5. Grafana Explore：查询 `up{job="learnflow-otel-collector"}` 应返回 1。
6. 触发一次普通 API、RAG 和计划生成，再确认对应 Dashboard 出现低基数指标。

配置静态校验：

```powershell
python tools/check_observability_assets.py
docker run --rm --entrypoint /bin/promtool -v "${PWD}/ops/observability/prometheus:/etc/prometheus:ro" prom/prometheus:v3.13.1 check config /etc/prometheus/prometheus.yml
docker run --rm --entrypoint /bin/amtool -v "${PWD}/ops/observability/alertmanager:/etc/alertmanager:ro" prom/alertmanager:v0.32.1 check-config /etc/alertmanager/alertmanager.yml
```

## 告警演练

本地演练可以向 Alertmanager 提交一条不含用户数据的合成告警：

```powershell
$body = @(
  @{
    labels = @{ alertname = "LearnFlowSyntheticExercise"; severity = "warning"; owner = "platform"; category = "exercise" }
    annotations = @{ summary = "Synthetic alert routing exercise"; impact = "No user impact"; dashboard = "http://localhost:3000"; runbook = "docs/runbooks/observability-stack.md" }
    startsAt = (Get-Date).ToUniversalTime().ToString("o")
  }
) | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:9093/api/v2/alerts" -ContentType "application/json" -Body $body
```

确认告警进入 `learnflow-warning` 路由后，用相同标签提交已过去的 `endsAt`，或等待演练告警过期。演练报告必须记录开始/发现/确认/恢复时间、接收路由和截图，不得使用真实用户标识或内容。

## 生产告警路由

仓库内接收器仅供 Alertmanager UI 演练，不向外部系统发消息。生产部署必须挂载环境专用配置，将：

- `critical` 路由到 7×24 值班渠道，重复间隔不超过 30 分钟；
- `warning` 路由到对应服务团队，默认重复间隔 4 小时；
- Secret 通过部署平台注入，不写入仓库或 Compose 环境输出；
- 每个通知保留 `owner`、`impact`、`dashboard` 和 `runbook`。

上线前必须实际验证通知送达、确认、抑制与恢复通知；只在 UI 中出现不等于生产告警闭环完成。

## 故障处置

1. Collector 不可用：业务应继续运行；修复 OTLP 地址或 Collector 配置，避免同步重试阻塞业务。
2. Prometheus 不可用：保留 Collector 与应用指标，恢复存储卷后检查规则加载和抓取间隙。
3. Alertmanager 不可用：直接使用 Prometheus `/alerts` 判断当前告警，同时恢复路由并通知值班人员。
4. Grafana 不可用：从 Prometheus 查询 Recording Rules；不要为恢复 Dashboard 删除指标数据卷。
5. 指标突然消失：先检查 `LEARNFLOW_OTEL_ENABLED`、Collector Target 和 `service_name` 标签，再检查应用发布版本。

## 回滚

观测栈不参与业务数据事务。配置异常时回滚 `ops/observability` 与 Compose 的上一稳定版本并重载 Prometheus；不得通过删除业务数据库或任务表恢复监控。必须保留故障期间的告警与时间线证据。
