# API SLO 事故 Runbook

负责人：Backend；数据库或观测平台异常时分别升级给 Data/Platform。Dashboard：Grafana `LearnFlow / API and SLO`。关联告警：`LearnFlowApiFastBurn`、`LearnFlowApiSlowBurn`、`LearnFlowApiP95LatencyHigh`。

1. 确认告警窗口、受影响端点、版本和区域，排除健康检查与恶意扫描。
2. 检查 5xx、P50/P95/P99、错误预算 Burn Rate、线程池、数据库连接池、慢查询和下游 Agent 延迟。
3. 若与新版本相关，停止发布并在 10 分钟目标内回滚上一稳定版本。
4. 若数据库饱和，限制高成本入口、启用降级并检查锁等待/连接泄漏；不得直接终止未知事务。
5. 若 Agent/模型故障，执行 [AI 故障 Runbook](ai-slo-incident.md)。
6. 记录事件时间线、影响、临时措施和后续修复；恢复后观察至少 30 分钟。

升级条件：错误预算快速燃烧、影响超过 15 分钟或存在数据错误时，立即通知发布与数据负责人。
