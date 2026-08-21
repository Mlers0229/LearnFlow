# LearnFlow SLI/SLO

- 版本：1.1
- 生效日期：2026-08-21
- 统计窗口：滚动 30 天
- 默认负责人：LearnFlow 维护者；形成团队后按 Backend、Agent、Platform 重新分配

## 服务目标

| 服务 | SLI 计算方式 | SLO | 告警阈值 | Runbook |
| --- | --- | --- | --- | --- |
| 月度可用性 | 成功或预期 4xx 请求数 / 有效请求总数 | ≥ 99.9% | 1h/6h Burn Rate 触发 | [API 故障](../runbooks/api-slo-incident.md) |
| 普通 API 延迟 | 服务端请求耗时直方图 | P95 < 500ms | 连续 10 分钟 P95 ≥ 500ms | [API 故障](../runbooks/api-slo-incident.md) |
| 普通 API 错误 | 非预期 5xx / 有效请求总数 | < 0.5% | 连续 5 分钟 ≥ 1% | [API 故障](../runbooks/api-slo-incident.md) |
| 计划生成 | 规定预算内得到成功或明确降级结果 / 有效任务 | ≥ 98% | 15 分钟成功率 < 96% | [AI 故障](../runbooks/ai-slo-incident.md) |
| RAG | 规定预算内返回结果或明确空召回 / 有效请求 | ≥ 98% | 空召回率或错误率异常翻倍 | [AI 故障](../runbooks/ai-slo-incident.md) |
| Tutor | 规定预算内返回题目/反馈或明确降级 / 有效请求 | ≥ 98% | 15 分钟成功率 < 96% | [AI 故障](../runbooks/ai-slo-incident.md) |
| 数据恢复 | 最近可恢复点与故障时刻差值 | RPO ≤ 15 分钟 | 备份/PITR 检查失败 | [数据恢复](../runbooks/database-recovery.md) |
| 服务恢复 | 宣布事故到核心服务恢复时间 | RTO ≤ 60 分钟 | 事故 30 分钟未恢复时升级 | [数据恢复](../runbooks/database-recovery.md) |

## AI 预算与质量指标

| 指标 | 初始目标 |
| --- | ---: |
| AI 任务超时上限 | 按用例配置，不允许无限等待 |
| AI 降级率 | < 5%，且必须记录原因 |
| RAG 空召回率 | 建立真实数据基线后设置阈值 |
| RAG Recall@5 | ≥ 0.80，允许按冻结基线调整 |
| RAG NDCG@5 | ≥ 0.75，允许按冻结基线调整 |
| Token/费用 | 按模型、功能、用户和日聚合；M2 建立预算告警 |

### 初始调用预算

| 场景 | Backend 整体预算 | 下游读取/空闲上限 | 超时后行为 |
| --- | ---: | ---: | --- |
| 模型目录与管理配置 | 10s | 10s | 返回明确 502/504，不重试 |
| RAG | 15s | 15s | 回退现有规则召回 |
| Tutor | 45s | 45s | 回退规则题目或规则评估 |
| 计划、细化与重规划 | 90s | 60s | 回退规则计划或保持原计划 |
| Chat SSE | 300s | 60s 空闲 | 关闭下游流并返回明确错误事件 |

Agent 从 Backend 预算中预留最多 2 秒用于返回降级结果；模型调用再预留最多 2 秒用于 Agent 清理和响应。连接超时默认 3 秒（Backend）/5 秒（Agent），所有值均可通过环境变量覆盖。重试和熔断不包含在本阶段，必须在 M2-REL-02 按幂等性单独引入。

## 事件分类

- 有效请求排除健康检查、明显恶意扫描和客户端主动取消。
- 401、403、422 等预期 4xx 不计入服务错误，但单独监控异常突增。
- 超时、熔断、依赖失败和未分类异常均计入失败；返回规则降级结果计为可用，但计入降级率。
- SLO 必须由指标直接计算，不允许人工补写成功数据。

## Error Budget

99.9% 月度可用性对应约 43.2 分钟不可用预算。30 天窗口消耗超过 50% 时暂停非可靠性发布；耗尽时只允许修复安全、数据和可靠性问题，直到 Burn Rate 恢复。

## M2 监控实现

- API Dashboard 直接展示请求速率、5xx 比例、P50/P95/P99 和 1h/6h Burn Rate。
- 可用性告警采用成对窗口：5m+1h 快速燃烧阈值 14.4，30m+6h 持续燃烧阈值 6。
- Agent Dashboard 展示模型成功/降级、RAG 空结果和 Validator 失败；低流量时要求窗口内至少 10 次调用，避免单个请求触发比例告警。
- 数据库 Dashboard 展示 Backend HikariCP 与 Agent SQLAlchemy 连接池；队列 Dashboard 展示状态、最老等待、租约和 Worker 容量。
- 成本 Dashboard 只展示供应商返回 Usage 且显式配置单价后产生的估算数据。USD 25/24h 是演练阈值，不代表已批准生产预算。
- 规则和 Dashboard 定义位于 `ops/observability/`，处置入口见 `docs/runbooks/observability-stack.md`。
