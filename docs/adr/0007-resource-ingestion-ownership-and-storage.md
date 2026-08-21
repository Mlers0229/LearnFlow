# ADR-0007：资源摄取所有权、版本与原件存储

- 状态：Accepted
- 日期：2026-08-21

## 背景

现有 RAG 只读取 `resource_bank` 元数据，并在进程内建立规则/哈希索引。M3 需要把 URL、文本和文档转为可追溯 Chunk，同时不能让不可信内容扩大 Agent 权限、占满同步请求或进入日志。

## 决策

1. Spring Backend 负责资源元数据、摄取版本、原件引用、Chunk 和异步任务；Flyway 仍是 Schema 唯一管理者。
2. 抓取、解析、清洗、哈希和确定性 Chunk 属于普通服务逻辑，不交给 LLM/Agent。FastAPI 只读已批准的数据，且本阶段不新增写库权限。
3. 每次摄取建立不可变 `resource_ingestion` 版本；`resource_bank.current_ingestion_id` 只在完整处理成功后原子切换。
4. `resource_chunk` 以 `(resource_id, content_hash)` 去重，版本通过映射表记录顺序和字符偏移。更新时复用内容未变化的 Chunk ID，后续 Embedding 只需处理新 Chunk。
5. 原件通过 `ResourceSourceStore` 保存。开发环境可以使用受限文件系统；production 必须使用 S3 兼容对象存储和部署平台凭证。
6. 队列 Payload 只保存摄取 ID 与资源 ID，正文和文件不进入 `async_task`、日志、指标或 Trace。
7. 外部 URL 只允许无凭证 HTTP(S) 及 80/443 端口；每次 DNS 解析必须全部为公网地址，逐跳校验重定向，并限制超时、大小、MIME 和重定向次数。
8. 提交人必须明确确认处理权限；抓取尊重 `X-Robots-Tag` 和页面 robots meta 的 `noindex/noarchive`。
9. 现有规则/哈希 RAG 保持降级路径。pgvector、Embedding、FTS 和 RRF 不属于本决策范围。

## 取舍

使用 Backend 做确定性摄取减少跨服务事务和数据库授权面，但会增加 Java 文档解析依赖。保存原件提高可追溯性与重放能力，同时要求对象生命周期、访问控制和删除策略。

## 回滚

关闭 `LEARNFLOW_RESOURCE_INGESTION_ENABLED` 可停止新提交；关闭任务 Worker 后不再领取摄取任务。V9 是追加式迁移，旧应用仍可使用 `resource_bank` 既有字段和元数据 RAG。回滚应用时保留新表，恢复后可继续处理；不得手工删除 Flyway 历史。
