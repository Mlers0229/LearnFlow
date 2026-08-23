# ADR-0009：PostgreSQL Sparse Retrieval 与 RRF

- 状态：Accepted
- 日期：2026-08-22

## 背景

Sprint 12 已提供版本化 pgvector Dense Retrieval，但推荐排序仍把 Dense 原始相似度与旧规则分直接相加，且没有基于 Chunk 正文的 Sparse 通道。M3-RAG-03 要求建立 PostgreSQL Full Text Search、独立 Dense/Sparse Top-K、确定性 RRF 和可审计的召回通道，同时保留无外部模型依赖的规则/哈希降级路径。

## 决策

1. Flyway V13 在不可变 `resource_chunk.content` 上增加 `search_vector` 生成列，显式使用 `simple` 配置并建立 GIN 索引。ORM 不创建、更新或回填该列。
2. Sparse SQL 只读取 `ACTIVE`、`SUCCEEDED` 且属于 `resource_bank.current_ingestion_id` 的 Chunk，并在排序前应用领域与学习水平过滤。
3. 查询通过绑定参数传给 `websearch_to_tsquery`，最多使用 24 个去重扩展词、2,000 个字符；单通道候选最多 100 个、参与资源去重的 Chunk 最多 400 个。
4. Dense 和 Sparse 分别产生有序候选，不混合不可比较的 cosine 与 `ts_rank_cd` 原始分数。融合使用 `RRF_K=60`，同分时依次按通道数、既有反馈分和资源 ID 稳定排序。
5. Dense 与 Sparse 独立失败：任一通道可单独返回；两者均为空、关闭或失败时使用现有元数据、关键词和确定性哈希路径。旧路径也用于填充现代通道未覆盖的返回名额。
6. `LEARNFLOW_SPARSE_RETRIEVAL_ENABLED` 是查询侧回滚开关，默认开启。关闭开关不删除 V13 结构，也不影响 Dense 或旧路径。
7. FastAPI 响应新增 `retrieval_channels`，Spring 以向后兼容方式映射为 `retrievalChannels`。指标只记录通道结果、候选数、耗时和融合结果数，不记录查询、Chunk、资源 ID 或用户身份。

## 取舍

PostgreSQL 内置 `simple` 配置避免英语词干规则误伤其他语言，但不提供专用中文分词。当前实现先利用安全查询扩展与不可变 Chunk 建立可运行基线；只有正式标注集证明收益后，才评估自定义词典、分词流水线或额外扩展。Sprint 13 不据此宣称 Hybrid 已优于基线。

生成列会在 V13 执行时计算既有 Chunk，GIN 创建也会消耗 I/O。首次生产迁移必须先在 staging 记录锁等待、迁移时长和索引大小，并选择低流量窗口执行。

## 回滚

紧急情况下设置 `LEARNFLOW_SPARSE_RETRIEVAL_ENABLED=false` 并滚动重启 Agent。应用会继续使用 Dense；Dense 同时不可用时回到规则/哈希路径。V13 是追加迁移，回滚应用时保留生成列与 GIN，禁止删除 Flyway 历史。若索引损坏，在受控维护窗口执行 `REINDEX INDEX idx_resource_chunk_search_vector_gin`，完成前保持 Sparse 关闭。
