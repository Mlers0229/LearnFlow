# Sprint 3 M1 安全闭环交付证据

- 日期：2026-08-21
- 范围：M1-SEC-02、M1-SEC-04、M1-CI-01 的增量完善

## 已完成

- 建立 `anonymous`、`student`、`admin` 权限与资源归属矩阵。
- 增加 Controller HTTP 权限测试：匿名请求返回 401，student 访问管理面返回 403，admin 可访问管理面。
- 增加可信身份测试，证明计划生成、资源上传、资源反馈和练习查询忽略客户端身份并使用 JWT `sub`。
- 增加跨用户学习日与练习记录测试，越权资源返回 404 或拒绝写入。
- Refresh/Logout Cookie 端点要求请求携带 CORS 白名单中的明确 Origin；缺失、`null` 或非白名单 Origin 返回 403。
- Agent 调用日志改为结构化脱敏摘要；凭证、Prompt、题目、答案、评论、目标和模型文本只保留类型、长度与短指纹。
- Agent Payload 默认限制为 4096 bytes，日志默认保留 30 天；读取端过滤过期记录，写入时物理清理。
- Flyway V3 不可逆清除历史 Agent 请求与响应 Payload。
- 新增 CodeQL、Trivy 与 SBOM 安全工作流，覆盖 Java、Python、JavaScript/TypeScript 源码和仓库供应链。

## 本地自动验证

| 验证 | 结果 |
| --- | --- |
| Backend `mvn test` | 32 tests，0 failures |
| Agent `python -m pytest -q` | 10 passed |
| Agent `compileall` | 通过 |
| Frontend production build | 通过；2849 modules transformed |
| Workflow YAML 解析 | `ci.yml`、`security.yml` 均通过 |
| Flyway V3 PostgreSQL 演练 | 在会话级临时表中执行、验证脱敏结果并回滚，通过 |

前端原 `dist/assets` 被本机运行进程占用，标准输出目录无法清空；改用隔离输出目录完成同一 Vite 生产构建。主 Chunk 仍超过 500 kB，属于后续性能优化项。

## CI 门禁变化

- 原三端构建工作流升级到 Node 24 兼容的 Checkout、Setup Java、Setup Python、Setup Node 主版本。
- CodeQL 使用 `security-extended` 与 `security-and-quality` 查询分析三种语言。
- Trivy 对仓库执行 HIGH/CRITICAL 依赖漏洞、Secret 和配置错误扫描。
- Anchore/Syft 生成 CycloneDX JSON SBOM 并作为工作流产物保存。

远端工作流尚未在本地分支上触发，因此根据路线图证据规则，SAST/SCA/Secret 扫描与 SBOM 复合子项暂不勾选。容器镜像扫描也尚未实现。

## 尚未关闭的 M1 项目

- 未登录用户自助密码找回/重置及邮件交付。
- `admin_audit_log` 的独立保留周期和密钥轮换演练。
- 后端与 Agent 的独立数据库角色、最小权限和目标数据量查询计划。
- Testcontainers 集成测试、OpenAPI 兼容检查、Python 类型检查、Frontend 单测与 Lint。
- 安全工作流首次远端通过及容器镜像扫描。
