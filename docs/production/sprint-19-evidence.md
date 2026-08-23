# Sprint 19 M5 生产部署基础加固交付证据

- 日期：2026-08-23
- 范围：M5-DEPLOY-01 平台无关工程基座

## 已实现

- Backend 提供 Actuator liveness/readiness 分组，readiness 包含数据库，健康详情不公开。
- Agent 提供公开但不泄漏依赖错误的 /health/live 与 /health/ready；数据库查询在线程池中执行且有 2 秒上限。
- Frontend Nginx 提供进程与静态资源健康端点。
- 三个应用镜像保持非 root，增加 HEALTHCHECK；基础镜像可由构建参数注入 Digest。
- Compose 中三个应用服务使用只读根文件系统、受限 tmpfs、no-new-privileges、丢弃 capabilities 和 readiness 依赖。
- 新增 staging/production Profile 与环境模板；production Backend/Agent 对开发配置、可变发布版本和关闭遥测执行失败关闭。
- 建立平台无关运行契约、金丝雀/回滚策略、生产发布候选阻断器和负责人/证据要求。
- 固化 Flyway V1～V17 SHA-256，后续迁移连续编号并禁止破坏性变更，要求 Expand/Contract。
- CI 新增部署/Flyway 静态门禁及 hardened 四服务 Compose 冒烟。

## 自动化结果

| 门禁 | 结果 |
| --- | --- |
| Backend mvn -q test | 112 tests，0 failures，0 errors，3 skipped（本机无 Docker） |
| Agent pytest | 75 passed，1 个既有 Starlette 弃用 warning |
| Agent Ruff | app、tests 与 3 个部署工具脚本全部通过 |
| Agent Mypy | 46 个 app 源文件与 3 个部署工具脚本，0 issues |
| Frontend ESLint | 0 errors，24 条既有 warning |
| Frontend Vitest | 4 个测试文件、10 tests 全部通过 |
| Frontend production build | 通过，仅保留既有大 chunk 提示 |
| RAG regression | Recall@5 1.000、MRR 0.916667、NDCG@5 0.876743、空召回率 0 |
| Deployment contract | 健康、只读运行、环境、金丝雀/回滚及发布候选正反路径通过 |
| Flyway contract | V1～V17 checksum 不可变、连续编号和 Expand/Contract 门禁通过 |
| Release preflight | 未选平台/非 Digest/缺 staging 证据与负责人时按设计拒绝 |
| GitHub Actions YAML | ci.yml 与 security.yml 静态解析通过 |
| git diff --check | 通过 |

## 未关闭

- 本机无 Docker，read-only/tmpfs、HEALTHCHECK、V1～V17、数据库 ACL 和四服务 Compose 冒烟等待远端 CI 首次成功证据。
- 托管 OCI 平台、区域和预算未选定；production preflight 按设计保持阻断。
- 尚未建立真实 dev/staging/production 环境，未执行实际金丝雀、上一 Digest 回滚、PITR、Dashboard/告警闭环。
- M5-PERF、M5-DR 与 M5-RELEASE 尚未开始，不能据此宣称可正式生产部署。

## 关联资料

- ADR：../adr/0015-platform-neutral-production-runtime-contract.md
- Runbook：../runbooks/production-release-and-rollback.md
- 运行契约：../../ops/deployment/runtime-contract.json
