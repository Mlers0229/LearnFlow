# Sprint 1 交付证据

- 日期：2026-08-21
- 范围：M0 + Sprint 1 基础工作

## 已完成

- 建立四项 ADR：系统边界与生产平台、JWT/Refresh Token、Flyway Schema 所有权、基础设施引入门槛。
- 建立 SLI/SLO、容量假设、Error Budget 与 API/AI/数据库 Runbook。
- 完成威胁模型，识别 14 项主要威胁并映射至 M1～M5。
- 新增 GitHub Actions 三端最小 CI。
- Spring Boot 引入统一 Security Filter Chain、显式 CORS 白名单、无状态会话配置、方法级授权能力和统一 PasswordEncoder。
- 移除 Controller 上的宽松 `@CrossOrigin`，统一由安全配置管理跨域。
- 引入 Flyway PostgreSQL 依赖并保持默认关闭，记录两阶段接管步骤。
- 新增 Python 开发测试依赖清单。

## 验证结果

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 通过；3 tests，0 failures |
| Agent `python -m pytest -q` | 通过；3 passed |
| Frontend production build | 通过；Vite 2848 modules transformed |
| CORS 配置测试 | 通过；只接受显式 Origin，不包含 `*` |
| BCrypt 测试 | 通过；明文不落库，正确/错误密码匹配行为符合预期 |

前端构建仍报告主 Chunk 超过 500 kB，这是性能优化项，不阻断本 Sprint。

## Sprint 1 结束时保留的兼容窗口

- Sprint 1 结束时曾使用兼容开关；Sprint 2 已完成会话迁移并将 `learnflow.security.enforce-authentication` 切换为 `true`。
- Sprint 1 时 Flyway 暂未启用，JPA 与 FastAPI 仍保留自动建表。Sprint 2 已完成两条迁移路径演练，现已切换为 Flyway、JPA `validate`，并移除 FastAPI 启动建表。

## 下一批验收目标

1. 实现 Access/Refresh Token、轮换、吊销和登录失败限流。
2. 所有业务身份改从安全上下文读取，完成 IDOR/BOLA 测试。
3. 管理接口实施服务端 `admin` 授权。
4. FastAPI 增加内部服务认证并取消公网映射。
5. LLM Key 停止写入运行时 JSON。
6. 获取实际 Schema，生成并演练 Flyway V1。
