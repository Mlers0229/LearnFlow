# 数据库恢复 Runbook

负责人：Data；应用连接池处置由 Backend/Agent 协同。Dashboard：Grafana `LearnFlow / Database`。关联告警：`LearnFlowDatabasePoolSaturated`、`LearnFlowAgentDatabasePoolSaturated`、`LearnFlowQueueMetricsStale`。

1. 宣布数据事故并冻结写入，记录故障时刻和最后已知正常时刻。
2. 保存当前实例、日志和备份元数据，不在原实例上执行未经演练的修复。
3. 优先将 PITR 恢复到隔离实例，校验 Flyway 版本、关键表数量、外键和抽样业务数据。
4. 由应用负责人确认读写冒烟测试后切换连接；旧实例保持只读，直到观察窗口结束。
5. 记录实际 RPO/RTO、丢失范围和需要重放的幂等任务。

禁止事项：未经备份直接删表、手工改写 Flyway 历史、将未校验恢复实例接入生产流量。
