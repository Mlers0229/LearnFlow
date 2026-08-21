# Sprint 10 M3 RAG 可复现评测基线交付证据

- 日期：2026-08-21
- 范围：M3-EVAL-01 数据契约、冻结资源快照、真实指标计算、CI 回归入口和 pilot 基线

## 实现范围

- `RagAgent` 新增冻结资源快照模式，可在不访问数据库、不按 TTL 刷新且不写调用日志的情况下运行与线上相同的召回和重排代码。
- 检索器和索引具有显式版本：`metadata-hybrid-v1`、`deterministic-hash-vector-v1-d64`。
- 新增版本化数据集加载器，校验 Schema、声明计数、资源引用、重复 ID、重复相关性判断与跨分集查询泄漏。
- 新增纯 Python 指标实现和 CLI，计算 Recall@K、MRR、NDCG@K、空召回率及 nearest-rank 延迟分位数。
- 报告记录数据文件 SHA-256、数据集/检索器/索引版本、运行环境和逐查询排名；指标由代码生成，不接受手工填写。
- GitHub Actions Agent Job 新增固定资源快照上的 regression 分集评测。

## Pilot 数据边界

- 资源快照：11 条，与开发环境 `ResourceSeedInitializer` 的种子资源对应，使用评测专用稳定 ID。
- 查询：36 条，其中 dev 15、test 15、regression 6。
- 所有查询来源均为 `repository_curated_pilot`，复核状态均为 `pending_domain_review`。
- 当前数据不是生产用户查询，未达到路线图要求的 150～300 条真实学习场景，不能冻结正式阈值或宣称生产质量。
- 快照不包含用户反馈，报告明确记录 `feedback_snapshot_count = 0`。

## 本次真实运行结果

完整 pilot 使用 `K=5`，报告见 `agent-platform/evals/rag/reports/sprint-10-baseline.json`。

| 指标 | 结果 |
| --- | ---: |
| 查询数 | 36 |
| Recall@5 | 1.000000 |
| MRR | 0.824074 |
| NDCG@5 | 0.824616 |
| 空召回率 | 0.000000 |
| P50 | 0.390 ms |
| P95 | 0.867 ms |

延迟为本机进程内基线，只用于同配置对比，不代表生产端到端 SLO。高 Recall 也受到资源快照仅 11 条的影响，不能外推到生产规模。

## 自动化验证

| 验证 | 结果 |
| --- | --- |
| Python `pytest` | 38 passed |
| RAG 评测专项测试 | 7 passed |
| Ruff | `app` 与 `tests` 通过 |
| Mypy | 38 个 `app` 源文件通过 |
| 完整 pilot CLI | 成功生成包含文件哈希和逐查询排名的 JSON 报告 |

专项测试覆盖手算指标对照、nearest-rank 分位数、数据集声明、冻结快照不访问数据库、排名/质量指标可复现、Manifest 数量错误拒绝以及跨分集泄漏拒绝。

## 尚未关闭的范围

- 收集并匿名化 150～300 条真实学习场景。
- 完成领域、水平、任务类型和相关等级的人工复核与分歧处理。
- 冻结正式测试集和获批质量阈值。
- 在远端 GitHub Actions 获得新增 regression 评测步骤的首次成功证据。
- 评测任务当前是独立 CLI；批量评测迁入持久任务队列留待 M3 批任务整合。

因此 M3-EVAL-01 只部分完成，路线图中的真实查询与完整标注子项继续保持未勾选。

## 使用入口

- 评测规范与数据治理：[`rag-evaluation.md`](rag-evaluation.md)
- 数据集 Manifest：`agent-platform/evals/rag/v1/manifest.json`
- 评测 Runner：`agent-platform/app/evaluation/rag_runner.py`
