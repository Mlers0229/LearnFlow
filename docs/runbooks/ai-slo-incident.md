# AI SLO 事故 Runbook

负责人：Agent；入口限流和数据库问题由 Backend 协同。Dashboard：Grafana `LearnFlow / Agent and Model`。关联告警：`LearnFlowAiSuccessRateLow`、`LearnFlowAiDegradationHigh`、`LearnFlowRagEmptyRateHigh`、`LearnFlowValidatorFailureHigh`。

1. 按计划生成、RAG、Tutor 区分影响，检查模型、Prompt、索引和应用版本。
2. 检查超时、429、5xx、Token、费用、重试、熔断、空召回和 Validator 失败率。
3. 单一模型异常时切换已验证备用模型或规则降级；禁止无限重试。
4. Agent 饱和时收紧并发和入口限流，保护普通 API 与数据库。
5. 索引异常时冻结新索引并切回上一已验证版本。
6. 恢复后运行冻结回归集，确认质量和费用没有不可接受回归。

## 灾备演练：模型供应商不可用

1. 仅在 staging 的模型桩或受控路由中注入持续超时、429 或 5xx，不修改 production 模型配置。
2. 记录不可变应用版本、模型/Prompt/索引版本、演练开始时间和原始 SLO 基线。
3. 验证计划、RAG 和 Tutor 在各自截止时间内返回明确降级或分类错误，且普通 API、线程、连接和队列保持有界。
4. 验证熔断打开、半开探测和备用模型/规则路径；禁止通过无限重试掩盖故障。
5. 恢复供应商后确认熔断关闭、成功率和延迟回到基线，再运行冻结 RAG regression。
6. 归档脱敏的指标、告警、Trace、回归结果和开始/恢复时间；实际恢复时间不得超过 60 分钟。

出现敏感数据泄漏、越权、请求超过预算、普通 API 被拖垮或恢复后质量回归时，场景必须失败。

升级条件：AI 成功率低于 96% 持续 15 分钟、费用异常增长或出现越权/敏感信息泄漏时，立即停用对应能力。
