# LearnFlow 敏感日志策略

更新日期：2026-08-21

## 规则

- 禁止记录密码、Access/Refresh Token、API Key、Authorization Header。
- 禁止持久化完整 Prompt、用户答案、题目、评论、目标文本和模型输出。
- Agent 调用日志只保留 trace、Agent、模型、耗时及脱敏后的结构元数据。
- 非 JSON Payload 只保留 UTF-8 长度和截断后的 SHA-256 指纹。
- 单个请求或响应摘要默认最多 4096 bytes；超限后只保存整体元数据。
- 默认保留 30 天。查询端排除过期记录，写入新日志时物理清理过期记录。
- Agent 日志读取接口仅允许 `ROLE_ADMIN`。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `LEARNFLOW_AGENT_LOG_MAX_PAYLOAD_BYTES` | 4096 | 单侧 Payload 摘要最大字节数，最小有效值 256 |
| `LEARNFLOW_AGENT_LOG_RETENTION_DAYS` | 30 | 日志保留天数，最小值 1 |

## 升级与回滚

- Flyway V3 会不可逆地清除旧 `agent_call_log` Payload，避免历史敏感内容继续留存。
- 应用回滚不恢复已清除内容；如确需保留审计元数据，应在迁移前导出不包含 Payload 的列。
- 调整保留期前先确认合规要求；延长保留期不会恢复已删除记录。
