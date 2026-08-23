# Sprint 14 M3 二阶段重排与引用交付证据

- 日期：2026-08-22
- 范围：M3-RAG-04 Cross Encoder 评测入口、候选预算、Chunk 引用、低置信度降级与 Prompt Injection 边界

## 已实现

- 新增可选 Cross Encoder 适配器，默认关闭，候选上限默认 20、独立预算默认 1.2 秒、最低分默认 0.35。
- 依赖延迟加载；未安装、超时或非法数值输出均安全回落 RRF，不执行模型重试。
- 新增独立对照评测 CLI，报告 baseline/reranked 的 Recall、MRR、NDCG、差值、模型版本、数据集状态和延迟。
- Dense/Sparse SQL 返回实际命中的 Chunk ID、受限摘录、内容哈希和来源 URL；RRF 合并同一 Chunk 的召回通道。
- 启用重排后过滤无证据或低于门槛的候选；全部失败返回明确 `low_confidence`，不产生无证据成功结果。
- Python、Java 和 Vue 契约贯通引用、置信度和证据状态；计划卡片与历史页展示可点击引用。
- Cross Encoder 只接收不可信文本对并输出数值，不构造 Chat/System Prompt；恶意指令边界有自动化测试。
- 新增重排请求、候选、结果、耗时和降级原因指标，不记录查询、摘录、Chunk ID 或用户身份。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Java | 87 tests，0 failures/errors，3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Python | 57 passed；Ruff、Mypy 通过 |
| Frontend | ESLint 0 errors（24 条既有 warning）、Vitest 5 passed、生产构建通过 |
| 引用契约 | snake_case Chunk 引用、置信度、旧响应空集合兼容通过 |
| 重排降级 | 默认关闭、候选/预算边界、非法输出回落 RRF、低置信度过滤通过 |
| 注入边界 | 恶意检索文本仅作为 Cross Encoder 文本对输入，无法成为系统指令 |
| 评测门禁 | pilot 报告显式输出 `production_enablement_allowed=false` |

## 仍未关闭

- 没有安装或下载真实 Cross Encoder 模型，也没有以未复核 pilot 指标宣称质量提升。
- 正式 150～300 条数据集、人工领域复核、模型选择、生产阈值和真实 CPU/GPU P95 仍待后续环境与领域工作。
- 本机仍无 Docker，V13/GIN/Agent ACL、可选重排镜像大小与运行态链路尚无 staging 证据。

## 关联资料

- ADR：[`../adr/0010-evidence-grounded-cross-encoder-reranking.md`](../adr/0010-evidence-grounded-cross-encoder-reranking.md)
- Runbook：[`../runbooks/reranking-and-citations.md`](../runbooks/reranking-and-citations.md)
