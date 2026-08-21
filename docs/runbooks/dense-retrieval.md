# Dense Retrieval 运行手册

## 适用范围

用于处理 Embedding 任务失败、Dense Retrieval 降级、版本切换未完成、HNSW 查询异常和模型供应商故障。资源摄取本身仍由 `resource-ingestion.md` 处理。

## 启用前检查

1. PostgreSQL 镜像必须包含 pgvector；Compose、CI 和 Testcontainers 固定使用 `pgvector/pgvector:0.8.6-pg15`。Compose 初始化脚本由数据库 owner 预装扩展；托管 PostgreSQL 需由平台 DBA 预启用，禁止把 migrator 提升为超级用户。
2. Flyway V10 成功，且数据库存在 `vector` 扩展、两个版本化表和 `idx_resource_chunk_embedding_hnsw`。
3. Backend 与 Agent 使用相同的 `LEARNFLOW_EMBEDDING_PROVIDER/MODEL/VERSION/DIMENSIONS`；维度必须为 1536。
4. 仅向 Agent 注入 `LEARNFLOW_EMBEDDING_API_BASE` 和 `LEARNFLOW_EMBEDDING_API_KEY`，并把目标主机加入 `LEARNFLOW_LLM_ALLOWED_HOSTS`。
5. 先在 staging 保持 `LEARNFLOW_EMBEDDING_ENABLED=false` 完成迁移，再同时启用 Backend 和 Agent。

## 健康检查

- `GET /api/v2/rag/index/status` 的 `dense_ready=true`、`dense_vector_count>0` 且 `embedding_version` 等于预期活动版本。
- `embedding_model_version` 只能存在一个 `ACTIVE` 版本。
- `RESOURCE_EMBEDDING` 任务应从 `PENDING` 进入 `RUNNING/SUCCEEDED`，Payload 不包含正文。
- `learnflow.resource.embedding.tasks`、`learnflow.rag.dense.requests`、模型调用和队列指标没有持续失败或积压。

建议只在受控数据库会话执行以下只读检查：

```sql
select version, provider, model_name, dimensions, status, activated_at
from embedding_model_version
order by created_at;

select embedding_version, count(*)
from resource_chunk_embedding
group by embedding_version;

select count(*) as missing
from resource_bank r
join resource_ingestion_chunk ic on ic.ingestion_id = r.current_ingestion_id
left join resource_chunk_embedding e
  on e.chunk_id = ic.chunk_id and e.embedding_version = '<building-version>'
where r.ingestion_status = 'SUCCEEDED' and e.chunk_id is null;
```

## 故障处置

### Embedding 任务持续失败

1. 查看任务 `error_code`、Agent 模型调用指标、Bulkhead/熔断状态和 Trace，不读取或复制 Chunk 正文。
2. 检查端点 allowlist、凭证、模型名称、1536 维支持、限额和超时。
3. 修复后由管理员重放死信任务；同一 Chunk/版本的写入是幂等的。
4. 不要手工把覆盖不完整的 BUILDING 版本改为 ACTIVE。

### Dense 查询降级

1. 确认规则/哈希 RAG 仍有结果；Dense 故障不应造成空响应。
2. 检查是否存在 ACTIVE 版本以及活动版本向量数量。
3. 检查 Agent 是否仍能调用活动版本对应的模型，而不是只保留正在构建的新模型。
4. 数据库压力异常时检查 HNSW 索引、候选上限和领域/水平过滤是否进入查询。

### 紧急回滚

1. 同时把 Backend 和 Agent 的 `LEARNFLOW_EMBEDDING_ENABLED` 设为 `false` 并滚动重启。
2. 保留 V10、向量数据和失败任务，不删除 Flyway 历史。
3. 验证 `/api/v2/rag/resources` 返回 `metadata-index+keyword-vector-fusion+feedback-rerank` 降级策略。
4. 记录影响窗口、失败原因、活动版本和恢复证据。
