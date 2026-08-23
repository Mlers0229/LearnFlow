# Hybrid Retrieval 运行手册

## 适用范围

用于处理 PostgreSQL FTS、Dense/Sparse 单通道故障、RRF 结果异常、GIN 索引不可用和召回质量回归。Embedding 与 HNSW 故障仍参考 `dense-retrieval.md`。

## 启用前检查

1. Flyway V13 已成功，`resource_chunk.search_vector` 为生成列，`idx_resource_chunk_search_vector_gin` 存在。
2. `learnflow_agent` 对 `resource_bank`、`resource_ingestion`、`resource_chunk`、`resource_ingestion_chunk` 只有所需的读取权限。
3. `LEARNFLOW_SPARSE_RETRIEVAL_ENABLED=true`；Dense 是否开启由独立的 `LEARNFLOW_EMBEDDING_ENABLED` 控制。
4. `GET /api/v2/rag/index/status` 显示 `sparse_ready=true` 且 `sparse_chunk_count>0`。没有已审核摄取内容时，计数为 0 是预期行为。

只在受控数据库会话执行以下只读检查：

```sql
select attname, attgenerated
from pg_attribute
where attrelid = 'resource_chunk'::regclass and attname = 'search_vector';

select indexname, indexdef
from pg_indexes
where schemaname = 'public' and tablename = 'resource_chunk';

select count(c.search_vector) as current_searchable_chunks
from resource_chunk c
join resource_ingestion_chunk ic on ic.chunk_id = c.id
join resource_bank r
  on r.id = c.resource_id and r.current_ingestion_id = ic.ingestion_id
where r.status = 'ACTIVE' and r.ingestion_status = 'SUCCEEDED';
```

## 健康信号

- `learnflow.rag.sparse.requests` 按 `success/empty/fallback` 统计结果。
- `learnflow.rag.sparse.candidates` 与 `learnflow.rag.sparse.duration` 反映候选规模和延迟。
- `learnflow.rag.rrf.requests` 只允许 `hybrid/single/fallback` 三种正常结果。
- `learnflow.rag.rrf.dense_candidates`、`sparse_candidates` 和 `results` 不应长期单边为零。
- v2 响应的 `retrieval_channels` 只能包含 `dense`、`sparse`、`fallback`，不得写入遥测标签。

## 故障处置

### Sparse 持续 fallback

1. 检查 V13、GIN、Agent 数据库连接和 SELECT ACL，不复制 Chunk 正文到工单或日志。
2. 使用 `EXPLAIN (ANALYZE, BUFFERS)` 检查受控测试查询是否使用 GIN，禁止在生产记录查询原文。
3. 索引损坏时先关闭 Sparse，再在维护窗口 `REINDEX`；完成后以小流量恢复。
4. Sparse 故障期间确认 Dense 或规则/哈希路径仍能返回结果。

### RRF 排序异常

1. 检查响应 `retrieval_channels`、两个通道候选数量和 Retriever 版本 `postgres-hybrid-rrf-v1`。
2. RRF 固定使用 `K=60`；不要直接调整 cosine 或 FTS 原始分数来修复融合排序。
3. 用冻结输入复现排名，确认同分最终按资源 ID 稳定排序。
4. 若质量下降，关闭 Sparse 并保存版本、指标和回归报告；不要删除索引或 Chunk。

### 紧急回滚

1. 设置 `LEARNFLOW_SPARSE_RETRIEVAL_ENABLED=false`，滚动重启 Agent。
2. 验证 Dense 正常时策略为 `pgvector-dense-rrf+feedback-tiebreak`；Dense 也不可用时策略为旧 metadata fallback。
3. 保留 V13、GIN 和 Flyway 历史，记录影响窗口与恢复证据。

## 质量门禁

正式宣称 Hybrid 优于基线前，必须在 150～300 条已人工复核查询上运行同版本 Dense/Sparse/RRF，达到冻结阈值并记录 P95 延迟。当前 pilot 只能用于回归确定性，不能替代正式效果评估。
