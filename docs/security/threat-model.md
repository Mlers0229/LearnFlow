# LearnFlow 威胁模型

- 版本：1.0
- 日期：2026-08-21
- 范围：浏览器、Spring Boot、FastAPI、PostgreSQL、模型服务与部署入口

## 资产与信任边界

关键资产包括账号凭证、Token、用户计划与练习答案、资源反馈、Prompt/模型配置、LLM API Key、审计日志和数据库备份。

1. 公网浏览器与公网入口之间是不可信边界。
2. 公网入口与 Spring Boot 之间是受控入口边界。
3. Spring Boot 与 FastAPI 之间是内部服务身份边界。
4. 应用与 PostgreSQL 之间是数据权限边界。
5. 应用与外部模型、URL 和文档来源之间是第三方内容边界。
6. 运维人员、CI 和生产 Secret/日志平台之间是管理权限边界。

## 主要数据流

- 用户登录：浏览器 → Spring Boot → PostgreSQL。
- 业务请求：浏览器 → Spring Boot → PostgreSQL。
- AI 请求：浏览器 → Spring Boot → FastAPI → 模型服务/ PostgreSQL。
- 管理操作：管理员浏览器 → Spring Boot → FastAPI/ PostgreSQL。
- 资源摄取：受控 Worker → 外部 URL/文档 → 清洗与索引 → PostgreSQL/对象存储。

## 风险登记

| ID | 威胁 | 影响 | 可能性 | 等级 | 对应任务 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 伪造请求中的 `userId` 访问他人资源 | 数据泄漏/篡改 | 高 | Critical | M1-SEC-01/02 |
| T-02 | 篡改 `localStorage` 获得管理员界面并调用未授权接口 | 完全管理权限 | 高 | Critical | M1-SEC-01/02 |
| T-03 | 绕过 Spring Boot 直接调用 FastAPI 管理/日志接口 | 模型配置和敏感日志泄漏 | 高 | Critical | M1-SEC-03 |
| T-04 | API Key 写入运行时 JSON、镜像或日志 | 供应商账户滥用 | 高 | Critical | M1-SEC-04 |
| T-05 | 全开放 CORS 或缺少 CSRF 控制 | 跨站调用与会话滥用 | 中 | High | M1-SEC-01/03 |
| T-06 | JPA 与 SQLAlchemy 自动改表导致 Schema 漂移 | 数据损坏/发布失败 | 高 | High | M1-DATA-01 |
| T-07 | Agent 保存完整请求/响应 Payload | 隐私泄漏和存储膨胀 | 高 | High | M1-SEC-04、M2-OBS-01 |
| T-08 | 资源抓取接受内网/云元数据 URL | SSRF、凭证泄漏 | 中 | High | M1-SEC-03、M3-RAG-01 |
| T-09 | 检索内容或用户 Prompt 改写系统指令 | 错误工具调用/越权 | 中 | High | M3-RAG-04、M4-TOOL-01 |
| T-10 | 无请求预算、并发隔离和限流 | 资源耗尽、费用失控 | 高 | High | M2-REL-01/02 |
| T-11 | Refresh Token 重放或签名密钥泄漏 | 账号接管 | 中 | High | M1-SEC-01/04 |
| T-12 | 依赖或容器镜像供应链被篡改 | 远程代码执行 | 低/中 | High | M1-CI-01 |
| T-13 | 数据库角色权限过大 | Agent 漏洞扩大为全库泄漏 | 中 | High | M1-DATA-02 |
| T-14 | 备份不可恢复或区域单点 | 长时间停机/数据丢失 | 中 | High | M5-DR-01 |

## 首轮控制要求

- 在公开部署前关闭 T-01～T-07、T-10、T-11 的 Critical/High 暴露面。
- 安全测试必须覆盖匿名访问、角色矩阵、跨用户读取/修改/删除、Token 过期/吊销/重放和 Agent 直连。
- 日志默认不保存密码、Token、API Key、完整用户答案和完整 Prompt；确需诊断时使用脱敏摘要、采样和 TTL。
- 任一管理接口都必须经过服务端管理员授权，前端路由守卫只用于体验优化。

## 复审触发条件

新增公网入口、外部 URL 抓取、Tool Calling、文件上传、身份供应商、支付功能或新的敏感数据类别时，必须更新本模型。
