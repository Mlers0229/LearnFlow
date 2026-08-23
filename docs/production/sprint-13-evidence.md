# Sprint 13 M3 Sparse Retrieval 与 RRF 交付证据

- 日期：2026-08-22
- 范围：M3-RAG-03 PostgreSQL FTS、Dense/Sparse Top-K、RRF、通道溯源和独立降级

## 已实现

- Flyway V13 为不可变 Chunk 正文增加 `simple` FTS 生成列和 GIN 索引，并保持 Agent 只读权限。
- Sparse 查询只读取已审核、摄取成功且属于当前摄取版本的资源，领域和学习水平过滤在 FTS 排序前执行。
- 查询词、候选数和 Chunk 数均设置硬上限，所有用户派生文本使用绑定参数进入 `websearch_to_tsquery`。
- Dense 与 Sparse 分别返回有序 Top-K，使用固定 `K=60` 的 RRF 融合；原始 cosine 与 `ts_rank_cd` 分数不直接相加。
- 同分依次使用通道数、既有反馈分和资源 ID 稳定处理；响应记录 `dense/sparse/fallback` 通道。
- Dense、Sparse 可独立失败；两者都不可用时回到既有规则/关键词/确定性哈希路径，旧路径也用于安全填充。
- 新增 Sparse/RRF 低基数指标、聚合 Trace 属性、索引健康字段和显式回滚开关；遥测不包含查询、Chunk、资源 ID 或用户身份。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Java 测试 | 85 tests，0 failures/errors，3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Python | 52 passed；Ruff、Mypy 通过 |
| Frontend | ESLint 0 errors（24 条既有 warning）、Vitest 5 passed、生产构建通过 |
| RRF 专项 | 手算公式、跨通道奖励、稳定同分、legacy 填充通过 |
| 降级专项 | Dense 失败保留 Sparse、双通道失败回到 legacy 通过 |
| Sparse 专项 | 查询上限、绑定参数、当前版本/状态/领域/水平过滤通过 |
| 遥测隐私 | 非法原因归一化、仅聚合候选数与结果数通过 |
| 冻结 regression | 连续两次排名一致；Recall@5 1.000、MRR 0.916667、NDCG@5 0.876743、空召回率 0 |

## 配置与运维

- 架构决策：[`../adr/0009-postgresql-sparse-retrieval-and-rrf.md`](../adr/0009-postgresql-sparse-retrieval-and-rrf.md)
- Runbook：[`../runbooks/hybrid-retrieval.md`](../runbooks/hybrid-retrieval.md)
- `LEARNFLOW_SPARSE_RETRIEVAL_ENABLED=true` 默认开启；紧急回滚只关闭查询通道，不删除 V13。

## 剩余环境与质量证据

- 本机无 Docker CLI/Daemon，V13 生成列、GIN、真实 Agent ACL 和查询计划尚未在隔离 PostgreSQL 中执行。
- 现有 36 条 pilot 明确未完成领域复核，冻结 regression 使用内存快照，因此只能证明旧基线未回归，不能证明生产 Hybrid 指标提升。
- 仍需在 staging 记录 V13 锁等待、迁移时长、GIN 大小、目标规模 P95，以及 Dense/Sparse/RRF 的 Recall、MRR、NDCG 对照。
- 不同领域、学习水平和中文分词策略尚未基于正式标注集调优，M3-RAG-03 的最终验收保持未关闭。
