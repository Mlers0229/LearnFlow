# LearnFlow RAG 评测规范

- 当前数据集：`agent-platform/evals/rag/v1`
- 当前状态：`pilot-unreviewed`
- 当前检索器：`metadata-hybrid-v1`
- 当前索引：`deterministic-hash-vector-v1-d64`

## 1. 目的与边界

该评测用于冻结现有关键词、元数据和确定性哈希向量方案的可复现基线，再判断 pgvector、FTS、RRF 或 Reranker 是否带来真实收益。

当前 36 条查询由仓库现有学习流程和 11 条开发种子资源整理，只是工程 pilot，不是生产用户查询，全部标记为 `pending_domain_review`。在扩充至 150～300 条、完成领域复核并冻结测试集之前：

- 不将 pilot 指标解释为生产效果。
- 不以 pilot 指标宣称 Hybrid RAG 已提升。
- 不把测试集用于权重调优或规则开发。
- 不启用正式质量阈值门禁。

## 2. 数据结构

数据集目录包含：

- `manifest.json`：Schema、数据集版本、状态、来源说明和文件计数。
- `resources.jsonl`：具有稳定 ID 的资源快照，避免评测依赖本地数据库状态。
- `queries.dev.jsonl`：开发集，仅用于诊断和调优。
- `queries.test.jsonl`：测试集，只用于冻结方案的最终比较。
- `queries.regression.jsonl`：CI 使用的小型确定性回归集。

每条查询必须包含查询 ID、分集、`ResourceQueryContext`、相关资源等级以及来源和复核状态。相关等级定义：

| 等级 | 含义 |
| ---: | --- |
| 3 | 直接满足当前学习任务的首选资源 |
| 2 | 明显相关且可作为替代或补充 |
| 1 | 有一定帮助，但不是该任务的主要答案 |
| 0 | 不相关；JSONL 中不保存零等级判断 |

加载器会拒绝文件计数不一致、重复查询 ID、重复资源 ID、未知资源引用、重复判断和跨分集完全相同的查询 Payload。报告保存全部输入文件的 SHA-256，以便定位实际运行的数据版本。

## 3. 指标定义

- `Recall@K`：Top-K 中召回的已标注相关资源数，占该查询全部相关资源数的比例。
- `MRR`：第一个相关结果排名的倒数，再对查询取平均。
- `NDCG@K`：使用 1～3 级相关性和指数增益计算的归一化折损累计增益。
- `empty_retrieval_rate`：没有返回任何资源的查询比例。
- 延迟：评测进程内围绕单次 `recommend_v2` 的耗时，报告 nearest-rank P50/P95 与最大值。

延迟只适合相同机器和进程配置下比较，不等同于页面到服务端的生产 SLO。

## 4. 运行方式

在 `agent-platform` 目录执行完整 pilot：

```powershell
python -m app.evaluation.rag_runner `
  --dataset-dir evals/rag/v1 `
  --splits dev test regression `
  --k 5 `
  --output evals/rag/reports/local-baseline.json
```

CI 只执行回归集：

```powershell
python -m app.evaluation.rag_runner `
  --dataset-dir evals/rag/v1 `
  --splits regression `
  --k 5 `
  --output tmp/rag-regression.json
```

Runner 支持 `--min-recall-at-k` 和 `--min-ndcg-at-k`，但只有测试集完成复核且基线获批后才可在 CI 配置正式阈值。

### Cross Encoder 对照评测

Cross Encoder 使用独立可选依赖，不进入默认 Agent 镜像：

```powershell
pip install -r requirements-rerank.txt
python -m app.evaluation.rag_rerank_runner `
  --dataset-dir evals/rag/v1 `
  --splits dev test regression `
  --model cross-encoder/ms-marco-MiniLM-L-6-v2 `
  --candidate-k 10 `
  --k 5 `
  --output evals/rag/reports/local-cross-encoder.json
```

报告同时保存 baseline、reranked、差值、延迟、模型版本和数据集状态。当前数据集为 `pilot-unreviewed`，报告会明确输出 `production_enablement_allowed=false`；该结果只能验证评测链路，不能作为生产启用依据。

## 5. 正式数据集冻结流程

1. 从经允许的真实学习任务、访谈或人工场景设计中收集 150～300 条查询；删除身份、答案正文和其他不必要个人数据。
2. 由领域复核者检查查询的水平、领域、任务类型和候选资源，再由第二人处理有分歧的相关等级。
3. 去重后划分开发、测试和回归集；测试集在方案比较期间保持冻结。
4. 运行现有基线并保存数据哈希、检索器、索引、代码版本与报告。
5. 根据冻结基线和业务目标批准正式阈值，再将阈值加入 CI 或独立评测任务。

任何资源快照或标签变更都必须升级数据集版本并重新生成报告，不能覆盖既有版本。
