# 资源摄取故障 Runbook

## 影响

资源提交停留在 `PENDING/PROCESSING`、进入 `FAILED`，或无法形成可供后续 Hybrid RAG 使用的 Chunk。既有规则/哈希推荐仍可作为降级路径。

## 首先检查

1. 查看 `learnflow.resource.ingestion` 的 `source.type/outcome/reason`，以及 `learnflow.resource.ingestion.duration`、`bytes`、`chunks`。
2. 检查 `async_task` 中 `RESOURCE_INGESTION` 的状态、尝试次数、租约和截止时间；不要输出 `request_payload`。
3. 查询摄取状态 API，确认 `errorCode`、解析器/Chunk 版本、内容哈希和 Chunk 数。
4. 检查对象存储连通性、桶策略、KMS/凭证及剩余容量，但不要下载或记录用户原件。

## 常见分类

| errorCode | 处理 |
| --- | --- |
| `UNSAFE_SOURCE_URL` | 检查协议、凭证、端口和来源地址，不加入私网例外。 |
| `SOURCE_TOO_LARGE` / `EXTRACTED_TEXT_TOO_LARGE` | 让提交人缩小文档；提高上限前先评估内存和版权风险。 |
| `UNSUPPORTED_CONTENT_TYPE` | 核对真实 MIME，不按文件扩展名强制绕过。 |
| `INDEXING_DISALLOWED` | 尊重来源策略，不重放或规避禁止索引信号。 |
| `PARSE_FAILED` | 确认文件未损坏、未加密；必要时在隔离样本上升级解析器版本。 |
| `SOURCE_STORAGE_FAILED` | 恢复对象存储权限/网络后，由管理员重放失败任务。 |

## 恢复

- 暂停新摄取：`LEARNFLOW_RESOURCE_INGESTION_ENABLED=false`，已有规则 RAG 继续服务。
- 修复短暂依赖故障后，只通过管理端死信重放入口恢复；同一 Idempotency-Key 和来源指纹不会生成重复任务。
- URL 内容变化可由资源管理页“重新摄取”；文本/文档新版本使用管理员版本 API。成功版本会复用未变化 Chunk。
- 不要将失败任务改成成功，也不要手工切换 `current_ingestion_id`。

## 验证

任务应变为 `SUCCEEDED`，资源的 `ingestionStatus` 为 `SUCCEEDED`，`currentIngestionId` 指向该版本，Chunk 数大于零，日志/Trace 中没有正文、对象存储凭证或原始文件。
