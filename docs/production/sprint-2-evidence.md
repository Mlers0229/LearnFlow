# Sprint 2 安全与数据基座交付证据

- 日期：2026-08-21
- 范围：M1-SEC-01～04、M1-DATA-01 的首轮落地

## 已完成

- Spring Security 默认强制认证，公开端点仅保留注册、登录、刷新、注销和健康检查。
- 使用 HS256 短期 Access Token；校验 issuer、audience、有效期和角色，生产环境拒绝开发默认密钥。
- Refresh Token 使用 32 字节随机值、HttpOnly Cookie、数据库 SHA-256 哈希、会话族轮换和重放撤销。
- 密码、角色或账号状态变化会撤销该用户全部 Refresh Token。
- 登录失败按用户名和来源地址限流；新密码最短 12 位。
- 管理接口使用服务端 `ROLE_ADMIN` 方法授权。
- 计划、学习日、练习、资源上传和反馈的可信身份来自 JWT 安全上下文，不再采用客户端 `userId`。
- 前端 Access Token 仅保存在内存，刷新凭证仅使用 HttpOnly Cookie；页面刷新时自动恢复会话。
- FastAPI 删除浏览器 CORS 和公网 Compose 端口，仅接受 Spring Boot 的内部 Bearer 服务凭证。
- 流式聊天由 Spring Boot 代理，Nginx 不再暴露 `/agent/`。
- LLM API Key 仅从环境/Secret 读取；运行时 JSON 中的旧 Key 会被清除，管理页面不再接收密钥。
- Flyway V1/V2 已落地，JPA 改为 `validate`，FastAPI 启动不再调用 `create_all()`。
- 生产环境默认禁用 Flyway clean、运行时资源种子和 JPA Open Session in View。

## 自动验证

| 验证 | 结果 |
| --- | --- |
| Java `mvn test` | 10 tests，0 failures |
| Python `pytest -q` | 6 passed |
| Vue/Vite production build | 通过，2849 modules transformed |
| JWT | 身份、角色、issuer、audience 与 TTL 验证通过 |
| 登录限流 | 达到阈值返回 429 |
| 资源归属 | 非所有者访问学习日返回 404 |
| Refresh Token | 数据库仅保存哈希；旧运行时 LLM Key 自动清除测试通过 |

## HTTP 冒烟验证

在隔离 PostgreSQL 与本机后端上执行：

| 场景 | 结果 |
| --- | ---: |
| 注册并签发 Access Token | 201 |
| 匿名访问受保护计划接口 | 401 |
| 登录用户访问本人计划列表 | 200 |
| student 访问管理员总览 | 403 |
| 使用 HttpOnly Cookie 刷新并轮换 | 200 |
| 注销 | 204 |
| 注销后再次刷新 | 401 |

## Flyway 演练

- 空数据库：V1 → V2 → Hibernate validate 成功。
- 已有非空 Schema：一次性 baseline v1 → V2 → Hibernate validate 成功。
- CI 已增加 PostgreSQL 15 服务，每次运行自动验证 Flyway 与 JPA 映射。

## 尚未关闭的 M1 项目

- 登录限流当前是单实例内存实现；多副本共享限流留到 M2/Redis 引入门槛评估。
- 已支持登录后修改密码与管理员重置密码；面向未登录用户的自助找回/重置流程尚未实现。
- Refresh Token Cookie 已设置 HttpOnly、SameSite 与 Secure 生产默认值；仍需补充显式 Origin/CSRF 防护及对应测试。
- 还需扩展完整的 Controller 级 IDOR/BOLA 角色组合测试。
- Agent 调用日志仍需从完整 Payload 改为脱敏摘要、采样和 TTL。
- SAST、SCA、Secret 扫描、容器扫描和 SBOM 尚未加入 CI。
- 正式数据库接管前仍需目标环境备份、实际 Schema 差异报告和 staging 恢复演练。
