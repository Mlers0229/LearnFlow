# LearnFlow 密钥轮换 Runbook

## JWT 签名密钥

1. 生成至少 32 bytes 的新随机密钥和新的 `LEARNFLOW_JWT_KEY_ID`。
2. 将当前密钥复制到 `LEARNFLOW_JWT_PREVIOUS_SECRET`，将新密钥写入 `LEARNFLOW_JWT_SECRET` 后部署 Backend。
3. 验证新签发 Token 的 `kid` 为新 Key ID，并验证轮换前 Token 在 10 分钟 Access Token 窗口内仍可使用。
4. 等待 `access-token-ttl` 加最大时钟偏差后，清空 `LEARNFLOW_JWT_PREVIOUS_SECRET` 再部署。
5. 若验证失败，恢复旧密钥为当前密钥；已签发的新 Token 将失效，Refresh Token 可重新签发访问凭证。

## Backend → Agent 内部凭证

1. 在 Agent 设置 `LEARNFLOW_INTERNAL_PREVIOUS_TOKEN=旧值`，并把 `LEARNFLOW_INTERNAL_TOKEN` 更新为至少 32 bytes 的新值，先部署 Agent。
2. 验证 Agent 同时接受新旧凭证，然后更新 Backend 的 `LEARNFLOW_AI_AGENT_INTERNAL_TOKEN` 并部署 Backend。
3. 确认 Agent 401 未增加、计划/RAG/Tutor 冒烟测试通过后，清空 Agent 的 previous token。
4. 回滚时先让 Agent 重新接受旧凭证，再回滚 Backend，禁止反向操作造成调用中断。

## LLM/SMTP 凭证

凭证只在部署平台 Secret Manager 中轮换。新旧凭证重叠期间先验证模型与邮件发送，再吊销旧凭证；不得写入 `.env.example`、运行时 JSON、日志或工单正文。
