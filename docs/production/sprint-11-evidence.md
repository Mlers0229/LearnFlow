# Sprint 11 M3 资源摄取流水线交付证据

- 日期：2026-08-21
- 范围：M3-RAG-01 URL/文本/文档摄取、版本化 Chunk、持久任务、安全抓取和来源治理

## 已实现

- Flyway V9 增加 `resource_ingestion`、`resource_chunk`、`resource_ingestion_chunk`，扩展资源摄取状态和 `RESOURCE_INGESTION` 任务类型。
- URL、直接文本、PDF/Word/TXT/HTML 等 allowlist 内容通过统一解析器生成确定性 Chunk。
- Chunk 使用资源内内容哈希复用；新版本只新增发生变化的 Chunk，并在成功后原子切换当前版本。
- 原件存储提供文件系统开发适配和 S3 兼容生产适配；production 配置校验拒绝文件系统模式。
- URL 抓取逐跳执行协议、端口、DNS、公网 IP、重定向、大小、MIME 和预算检查。
- 用户上传页支持三类来源与版权确认；管理端可重新摄取 URL，管理员 API 支持 URL/文本/文档新版本。
- 任务 Payload 仅包含摄取 ID/资源 ID；指标标签只记录低基数来源、结果和错误分类。
- 旧规则/哈希 RAG 不变，作为 M3 后续 pgvector 接入前的降级路径。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Java 编译 | 通过 |
| Java 测试 | 79 tests，0 failures/errors；3 个 PostgreSQL/Testcontainers 用例因本机无 Docker 跳过 |
| Python | 38 passed；Ruff、Mypy 通过 |
| Frontend Vitest | 5 passed |
| Frontend ESLint | 0 errors（保留 24 条既有 warning） |
| Frontend 隔离目录生产构建 | 通过 |

专项覆盖纯文本/Word 解析、Chunk 确定性、公网地址判断、非法 URL/端口、对象键越界、任务正文隔离、生产 S3 门禁和 V9 结构断言。V9、ACL 与真实 S3/URL 端到端运行证据仍需远端 Docker/受控环境首次成功。

## 配置与运维

- 架构决策：[`../adr/0007-resource-ingestion-ownership-and-storage.md`](../adr/0007-resource-ingestion-ownership-and-storage.md)
- Runbook：[`../runbooks/resource-ingestion.md`](../runbooks/resource-ingestion.md)
- 生产必须配置 `LEARNFLOW_RESOURCE_STORAGE_TYPE=s3`、Bucket、Region 和部署平台注入的 AWS 凭证。
- 紧急回滚通过 `LEARNFLOW_RESOURCE_INGESTION_ENABLED=false` 停止新提交，不删除新表或 Flyway 历史。

## 后续边界

- 本 Sprint 不创建 pgvector/Embedding/HNSW/FTS/RRF。
- 150～300 条真实评测查询和领域复核仍未完成，不能用 pilot 指标宣称生产 RAG 质量。
- 原件对象生命周期策略、真实 S3 权限演练和 V9 数据库角色 ACL 需要在 staging/远端 CI 关闭证据缺口。
