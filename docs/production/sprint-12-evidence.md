# Sprint 12 M3 pgvector Dense Retrieval 交付证据

- 日期：2026-08-21
- 范围：M3-RAG-02 pgvector、版本化 Embedding、持久批处理、HNSW、权限过滤与安全降级

## 已实现

- Flyway V10 声明 `vector` 扩展并管理 `embedding_model_version`、`resource_chunk_embedding`、1536 维 cosine HNSW 和任务类型约束；Compose 由数据库 owner 预装扩展，避免提升 migrator 权限。
- Compose、GitHub Actions 与两个 Testcontainers 用例统一使用固定版本 `pgvector/pgvector:0.8.6-pg15`。
- `RESOURCE_EMBEDDING` 接入现有 PostgreSQL 持久任务队列；Payload 只含摄取 ID、资源 ID 和版本，不含 Chunk 正文或向量。
- 摄取成功后自动提交 Embedding 子任务；后台按页补齐当前摄取版本缺失的 Chunk，支持模型版本全量迁移。
- Backend 按 `(chunk_id, embedding_version)` 幂等写入，任务失败复用既有有限重试、租约、死信、取消和截止时间机制。
- 新版本全量覆盖后通过事务 advisory lock 原子切换 `BUILDING → ACTIVE` 并退役旧版本。
- FastAPI 新增内部批量 Embedding 入口，复用服务凭证、调用预算、共享连接池、Bulkhead、熔断、SSRF allowlist 和无正文遥测。
- Dense 查询使用数据库活动版本对应的模型生成查询向量，查询前过滤审核状态、当前成功摄取版本、领域和学习水平。
- Dense 失败、活动版本缺失或功能关闭时自动使用既有规则/关键词/确定性哈希检索，不返回由故障造成的空结果。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Java 编译 | 通过 |
| Java 测试 | 82 tests，0 failures/errors；3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Python | 43 passed；Ruff、Mypy 通过 |
| Frontend Vitest | 5 passed |
| Frontend ESLint | 0 errors（保留 24 条既有 warning） |
| Frontend 隔离目录生产构建 | 通过 |
| RAG regression | 连续两次排名一致；Recall@5 1.000、MRR 0.916667、NDCG@5 0.876743、空召回率 0 |

新增专项测试覆盖 V10 结构契约、任务 Payload 正文隔离、旧版本任务拒绝写入、Embedding 响应排序/维度/有限值校验、Dense 跨通道融合和 fallback 保留。Docker 可用时，集成测试还会验证真实 V10、pgvector 扩展、HNSW 和 Backend/Agent ACL。

## 配置与运维

- 架构决策：[`../adr/0008-pgvector-dense-retrieval-and-embedding-versions.md`](../adr/0008-pgvector-dense-retrieval-and-embedding-versions.md)
- Runbook：[`../runbooks/dense-retrieval.md`](../runbooks/dense-retrieval.md)
- 默认 `LEARNFLOW_EMBEDDING_ENABLED=false`。启用时必须显式注入端点、密钥、模型、版本和 allowlist。
- V10 固定 1536 维；更换为不同维度的模型必须新增 Flyway Expand/Contract 迁移。

## 剩余环境证据

- 本机无 Docker CLI/Daemon，尚未实际运行 V10、pgvector HNSW 和数据库 ACL Testcontainers。
- 未使用真实 Embedding 供应商和生产规模 Chunk 执行吞吐、费用、召回与 P95 延迟测试。
- 首次 staging 验证需覆盖全量 backfill、BUILDING→ACTIVE 切换、旧版本回滚和供应商故障降级。
- M3-RAG-03 的 PostgreSQL FTS、Sparse Retrieval 与 RRF 尚未开始。

## 2026-08-22 CI 与安全基线修复

- 新增 Flyway V11，将 `async_task.progress` 从 `SMALLINT` 安全扩展为 `INTEGER`，并在 PostgreSQL 集成测试中校验真实列类型。
- Agent 可选本地数据库配置改为运行时加载，干净检出不再依赖被忽略的 `db_settings_local.py`。
- 后端升级到 Spring Boot 3.5.16，并固定 PostgreSQL 42.7.12、Netty 4.1.136.Final、Protobuf Java 3.25.8；前端 npm 官方审计为 0 vulnerabilities。
- Backend、Agent、Frontend 镜像均以非 root 用户运行；运行时基础镜像和可观测性组件更新到当前受支持版本。
- 自有代码、依赖和应用镜像继续以 High/Critical 为阻断阈值。第三方基础设施镜像报告 High/Critical，但仅由 Critical 阻断，避免上游最新镜像中无法在本仓库修复的 High 漏洞阻塞所有提交，同时保留完整可见性。
