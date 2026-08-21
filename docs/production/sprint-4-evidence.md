# Sprint 4 M1 收口交付证据

- 日期：2026-08-21
- 范围：M1-SEC-01/03/04、M1-DATA-02、M1-CI-01 增量完善

## 已完成实现

- 自助密码重置：统一响应、请求限流、哈希 Token、20 分钟有效期、单次消费、SMTP 适配和重置后会话吊销；前端新增找回/确认页面。
- JWT 使用 `kid` 签发并支持当前/前一密钥双验证；Agent 内部凭证支持新旧凭证重叠轮换。
- LLM 出站地址限制为 HTTP(S)，生产要求域名白名单，拒绝凭证型 URL 与非公网解析结果；资源链接只接受无内嵌凭证的 HTTP(S) URL。
- `admin_audit_log` 增加独立保留期、定时清理、detail 长度上限和索引。
- Flyway V4～V6 增加密码重置表、运行角色授权和旧 baseline 数据库索引回填。
- 分离 migrator、Backend、Agent 数据库角色；Agent 权限收敛到资源/反馈读取与 Agent 日志维护。
- CI 增加 Testcontainers 权限/迁移测试、OpenAPI 必需路径检查、Ruff、Mypy、ESLint、Vitest 和三镜像 Trivy 扫描。

## 本地验证

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 42 tests，0 failures/errors；本机无 Docker，2 个 Testcontainers 测试按设计跳过 |
| Agent `pytest` | 14 passed |
| Agent Ruff | 通过 |
| Agent Mypy | 30 个源文件通过 |
| Frontend ESLint | 0 errors，保留 24 个非阻断样式/安全提示 warning |
| Frontend Vitest | 1 passed |
| Frontend production build | 通过，2851 modules transformed |
| Flyway V1～V6 + OpenAPI | 临时 Schema 完整迁移，11 个必需 API 路径通过，验证 Schema 已删除 |
| 查询计划 | 旧库 891 行 `study_plan_day` 原为 Seq Scan；事务内应用 V6 后变为 Index Scan，执行约 4.27ms → 0.06ms，随后回滚 |
| 目标量级查询计划 | 事务内生成 10k plans、100k days、10k resources；三个关键查询均使用目标复合索引，执行约 0.02～0.04ms，随后整体回滚 |

## 尚需远端证据

- 本机没有可用 Docker，因此数据库角色 Testcontainers 用例只完成编译，需 GitHub Actions 首次运行验证实际 ACL。
- CodeQL、仓库 Trivy、SBOM 与三镜像 Trivy 已配置，但在当前分支未推送前没有远端成功记录。
- 因证据规则，依赖远端 Docker/Actions 的复合任务仍保持未勾选。
