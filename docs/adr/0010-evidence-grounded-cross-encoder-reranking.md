# ADR-0010：证据约束的 Cross Encoder 二阶段重排

- 状态：Accepted
- 日期：2026-08-22
- 范围：M3-RAG-04 二阶段重排、引用与低置信度降级

## 背景

Sprint 13 已实现 pgvector Dense、PostgreSQL FTS Sparse 与确定性 RRF，但现有 `feedback-rerank` 只是业务规则排序，不是真正的语义二阶段重排。同时，旧响应只返回资源级 URL，无法证明结果具体命中了哪个 Chunk。

当前评测集仍是 36 条 `pilot-unreviewed` 查询，不能据此宣称生产质量提升，也不能直接默认启用新的模型路径。

## 决策

1. 保留 `Dense/Sparse -> RRF` 作为稳定主路径，在最多 20 个候选上可选执行 Cross Encoder。
2. Cross Encoder 默认关闭；模型、候选上限、1.2 秒独立预算和最低分数全部通过环境变量配置。
3. 模型依赖通过 `requirements-rerank.txt` 和镜像构建参数显式安装，默认生产镜像不承担 PyTorch 等大型依赖。
4. 重排超时、依赖缺失、输出数量错误或非有限数值时保留 RRF 顺序，并记录低基数降级原因。
5. 启用重排时，只返回具有当前审核资源 Chunk 证据且达到最低置信度的结果；全部不合格时返回空结果和 `low_confidence`，不以无证据内容伪装成功。
6. Dense/Sparse SQL 返回实际命中的 Chunk ID、受限摘录、内容哈希和来源 URL；同一 Chunk 的通道信息在 RRF 阶段合并。
7. 检索正文只作为 Cross Encoder 文本对的第二项，模型只能输出数值，不进入 Chat/System Prompt，也不能调用工具。暂不引入 LLM Reranker。
8. 只有在人工复核数据集和 staging 上证明质量收益超过延迟与镜像成本后，才允许生产启用。

## 取舍

- 优点：引用可验证；模型失败不破坏现有排序；Prompt Injection 没有指令执行入口；模型成本可被独立评估。
- 代价：启用本地 Cross Encoder 会显著增加镜像和内存；当前 pilot 只能验证工程链路，不能证明生产收益。

## 回滚

将 `LEARNFLOW_CROSS_ENCODER_ENABLED=false` 后重启 Agent，即恢复 RRF 结果。若镜像过大，使用 `LEARNFLOW_INSTALL_RERANK=false` 重建；无需回滚数据库结构，Chunk 引用字段对旧客户端向后兼容。
