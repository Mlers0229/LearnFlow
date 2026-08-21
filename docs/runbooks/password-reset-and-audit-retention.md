# 密码重置与审计保留 Runbook

## 启用密码重置

配置 SMTP Secret、`LEARNFLOW_PASSWORD_RESET_ENABLED=true`、HTTPS 的 `LEARNFLOW_PASSWORD_RESET_BASE_URL` 和发件地址。生产环境启用后若仍使用 localhost SMTP 或 HTTP 重置链接，Backend 将拒绝启动。

请求接口始终返回统一结果，避免枚举账号。Token 只以 SHA-256 摘要保存，20 分钟后过期、只能使用一次；成功重置会吊销该用户全部 Refresh Token。排障时不得记录邮件中的原始 Token。

## 审计保留

`LEARNFLOW_AUDIT_RETENTION` 默认 365 天，清理任务默认每日 UTC 03:17 执行。管理审计接口仅允许 admin，单条 detail 默认最多 2048 字符。调整保留期前应确认合规要求并记录审批。

误设过短保留期时应先停用调度并从备份恢复；已清理记录不能从应用数据库直接恢复。
