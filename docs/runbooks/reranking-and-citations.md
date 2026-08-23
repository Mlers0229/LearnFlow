# Cross Encoder 重排与引用 Runbook

## 影响

- 重排超时或依赖缺失：请求自动保留 RRF 顺序，响应标记降级。
- 全部候选低于门槛或没有可验证 Chunk：返回空列表并标记 `low_confidence`。
- 引用覆盖下降：前端仍可展示资源，但会明确提示证据不足；不得将无证据结果描述为已验证答案。

## 诊断

1. 查看 `learnflow.rag.rerank.requests` 的 `outcome/reason`，重点关注 `reranker_timeout`、`reranker_unavailable`、`invalid_reranker_output` 和 `low_confidence`。
2. 查看 `learnflow.rag.rerank.duration`、候选数和结果数，确认是否达到 1.2 秒预算或候选上限。
3. 检查镜像是否以 `LEARNFLOW_INSTALL_RERANK=true` 构建，并确认运行环境中可导入 `sentence_transformers`。
4. 检查 `LEARNFLOW_CROSS_ENCODER_MODEL` 是否与已批准评测报告完全一致。
5. 抽查响应中的 `chunk_id`、`content_hash`、`source_url` 与当前摄取版本；不要把摘录、查询或用户身份放入指标标签和日志。

## 止血与恢复

1. 设置 `LEARNFLOW_CROSS_ENCODER_ENABLED=false` 并滚动重启 Agent，RRF 和旧降级路径继续服务。
2. 不删除 Chunk、Embedding 或 FTS 索引；引用字段为兼容扩展。
3. 修复模型或预算后，先在 dev/staging 运行正式复核数据集对照评测。
4. 只有 Recall、MRR、NDCG、P95 延迟和资源成本均满足批准门槛时再灰度开启。

## 验证

- 重排关闭时排名与 Sprint 13 RRF 回归一致。
- 模型超时、依赖缺失和非法输出均回落 RRF。
- 恶意 Chunk 只能作为不可信文本被评分，不能改写系统指令或触发工具。
- 前端引用使用文本插值并以 `noopener noreferrer` 打开来源链接。
