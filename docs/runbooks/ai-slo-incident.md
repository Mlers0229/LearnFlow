# AI SLO 事故 Runbook

负责人：Agent；入口限流和数据库问题由 Backend 协同。Dashboard：Grafana `LearnFlow / Agent and Model`。关联告警：`LearnFlowAiSuccessRateLow`、`LearnFlowAiDegradationHigh`、`LearnFlowRagEmptyRateHigh`、`LearnFlowValidatorFailureHigh`。

1. 按计划生成、RAG、Tutor 区分影响，检查模型、Prompt、索引和应用版本。
2. 检查超时、429、5xx、Token、费用、重试、熔断、空召回和 Validator 失败率。
3. 单一模型异常时切换已验证备用模型或规则降级；禁止无限重试。
4. Agent 饱和时收紧并发和入口限流，保护普通 API 与数据库。
5. 索引异常时冻结新索引并切回上一已验证版本。
6. 恢复后运行冻结回归集，确认质量和费用没有不可接受回归。

升级条件：AI 成功率低于 96% 持续 15 分钟、费用异常增长或出现越权/敏感信息泄漏时，立即停用对应能力。
