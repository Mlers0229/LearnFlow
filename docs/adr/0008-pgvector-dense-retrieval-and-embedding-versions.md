# ADR-0008：pgvector Dense Retrieval 与 Embedding 版本切换

- 状态：Accepted
- 日期：2026-08-21

## 背景

Sprint 11 已将受控资源转换为不可变摄取版本和确定性 Chunk，但现有 RAG 仍只使用元数据、关键词和 64 维确定性哈希向量。M3-RAG-02 需要引入真实语义向量，同时保持资源写入所有权、任务可靠性、权限边界和可回滚能力。

## 决策

1. Flyway V10 声明并校验 `vector` 扩展，管理 `embedding_model_version`、`resource_chunk_embedding` 和 HNSW 索引；ORM 不创建或修改这些对象。由于 pgvector 不是普通运行角色可安装的可信扩展，Compose 初始化由数据库 owner 预装，托管 PostgreSQL 由平台 DBA 预启用，不提升 migrator 权限。
2. 第一版固定使用 `vector(1536)` 与 cosine HNSW。Embedding 维度变化必须通过新的 Expand/Contract 迁移建立并行结构，不能只改环境变量。
3. Spring Backend 继续拥有任务编排和向量写入权。FastAPI 提供受内部凭证保护的有界批量 Embedding 入口，并只读活动向量执行检索；`learnflow_agent` 不获得向量表写权限。
4. `RESOURCE_EMBEDDING` 复用 PostgreSQL 持久任务队列。Payload 只保存摄取 ID、资源 ID 和 Embedding 版本，不保存 Chunk 正文或向量。
5. Chunk 以 `(chunk_id, embedding_version)` 幂等写入；只有内容哈希发生变化时才更新既有记录。队列提供有限重试、租约恢复、取消、截止时间和死信重放。
6. 模型版本状态为 `BUILDING/ACTIVE/RETIRED/FAILED`。后台为当前全部摄取版本补齐新模型向量后，使用事务级 advisory lock 原子退役旧版本并激活新版本。
7. 查询端先从数据库读取活动版本及其模型名称，再生成查询向量。这避免在新版本仍处于 BUILDING 时，用新模型查询旧模型向量。
8. Dense SQL 只读取 `ACTIVE`、摄取成功且属于 `current_ingestion_id` 的资源，并在向量排序前应用领域和学习水平过滤。当前资源权限模型中，管理员审核后的 `ACTIVE` 即全局可推荐权限边界。
9. Embedding、pgvector 或活动版本不可用时，保留现有元数据、关键词和确定性哈希向量路径作为降级，不把 Dense 故障转换为空结果。
10. Embedding 请求复用既有 HTTP 连接池、截止时间、并发 Bulkhead、熔断和遥测策略。日志、指标和 Trace 不记录查询、Chunk 或向量正文。

## 取舍

固定 1536 维允许 HNSW 使用明确 typmod 并保持查询计划稳定，但不同维度的模型需要数据库迁移。Backend 写入减少 Agent 授权面，代价是批量向量需要经过内部 HTTP 返回。版本切换假设新旧模型可由同一个受控 OpenAI 兼容端点调用；若更换供应商端点，应在切换窗口同时保留旧端点能力或先完成显式迁移演练。

## 回滚

将 `LEARNFLOW_EMBEDDING_ENABLED=false` 会停止新 Embedding 任务和 Dense 查询，RAG 自动恢复到既有规则/哈希路径。V10 仅追加扩展、表、索引和任务类型，不应在应用回滚时删除；已有活动向量可保留供恢复后继续使用。若新模型效果异常，将目标旧版本重新标记为 `ACTIVE` 前必须先确认其向量覆盖完整，并在事务锁内完成状态切换。
