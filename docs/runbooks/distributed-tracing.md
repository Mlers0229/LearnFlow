# 分布式 Trace 排障 Runbook

## 适用范围

用于定位 LearnFlow 从 Nginx、Spring Boot、持久异步任务、FastAPI Agent、外部模型到 PostgreSQL 的失败链路。本文档中的 Collector 与 Jaeger Profile 只用于本地或受控测试环境；生产环境必须使用带认证、TLS、保留策略和访问控制的托管或自建观测后端。

## 启用本地观测链路

PowerShell：

```powershell
$env:LEARNFLOW_OTEL_ENABLED = "true"
$env:LEARNFLOW_TRACE_SAMPLE_PROBABILITY = "1.0"
docker compose --profile observability up -d --build
```

- Jaeger UI：`http://127.0.0.1:16686`
- Grafana：`http://127.0.0.1:3000`
- Prometheus：`http://127.0.0.1:9090`
- Alertmanager：`http://127.0.0.1:9093`
- Collector 暴露的 Prometheus 格式指标：`http://127.0.0.1:9464/metrics`
- OTLP/HTTP 仅绑定本机：`http://127.0.0.1:4318`

生产采样率不得直接照搬 `1.0`，应按流量、错误优先策略和费用预算配置。

## 定位步骤

1. 从浏览器响应头复制 `X-Trace-Id`；若请求未建立 Trace，则复制 `X-Request-Id`。
2. 在 Jaeger 中先按 Trace ID 查询；按 Request ID 查询时使用 Span 属性 `learnflow.request.id`。
3. 确认链路是否依次出现：
   - `learnflow-backend` HTTP Server Span；
   - `agent.<operation>` 下游调用 Span；
   - 长计划任务对应的 `async-task.plan_generation` Consumer Span；
   - `learnflow-agent` FastAPI Server Span；
   - `agent.<name>.<operation>` Agent 节点 Span；
   - `gen_ai.chat` 或 `gen_ai.chat_stream` 模型 Span；
   - `db.*` / SQLAlchemy 数据库 Span。
4. 查看 `error.type`、`learnflow.degradation.reason`、HTTP 状态、任务尝试次数和模型使用量属性，不依赖异常正文判断故障。
5. 将问题归类为入口、鉴权、队列、数据库、Agent、模型、超时、Bulkhead 或熔断，再转入对应 Runbook。

## 常用指标

- `learnflow_ai_agent_calls`：Backend 到 Agent 的调用结果与失败原因。
- `learnflow_ai_agent_resilience_events`：有限重试、Bulkhead 拒绝和熔断状态变化。
- `learnflow_async_tasks`：任务结果与失败类型。
- `learnflow_ai_model_calls`：模型成功、超时、降级或阻断。
- `learnflow_ai_tokens`：供应商返回 Usage 时记录的输入/输出 Token。
- `learnflow_ai_cost_estimated_usd`：仅在显式配置每百万 Token 单价后产生的估算值。

指标名称经 Collector 转换为 Prometheus 格式时会把点号转换为下划线。

## 数据安全检查

Span 和指标只能包含以下低风险元数据：服务、操作、Agent、模型、Prompt 版本、状态、错误类型、Token 数量、耗时和降级原因。

禁止写入：

- Authorization、Cookie、Access/Refresh Token、API Key；
- Prompt、用户答案、聊天内容、学习目标正文；
- SQL 参数、实体值、异常消息或响应正文；
- 原始用户名、邮箱或用户 ID。

发现敏感内容时，先关闭导出并限制观测后端访问，再按安全事件流程清理数据和轮换可能泄露的凭证。

## Collector 或 Jaeger 不可用

OTLP 使用批量异步导出，观测后端故障不应阻断业务请求。确认应用仍可服务后：

1. 检查 `otel-collector` 与 `jaeger` 容器状态和 Collector 日志。
2. 检查应用配置的 OTLP 地址是否为 `http://otel-collector:4318`。
3. 检查 Collector 到 Jaeger 的 `jaeger:4317` 网络连通性。
4. 恢复后执行一次带固定 `X-Request-Id` 的失败注入请求，确认 Trace 可查询。

## 回滚

```powershell
$env:LEARNFLOW_OTEL_ENABLED = "false"
docker compose up -d backend agent
docker compose --profile observability stop grafana prometheus alertmanager otel-collector jaeger
```

关闭导出不会移除 Request ID，也不会影响认证、任务状态或业务数据。
