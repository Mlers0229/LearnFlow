# ADR-0015：平台无关的生产运行与发布契约

- 状态：Accepted
- 日期：2026-08-23
- 决策人：LearnFlow 维护者

## 背景

M0～M4 的主要工程路径已经落地，但 ADR-0001 要求正式环境运行在支持托管 PostgreSQL、Secret 注入、健康检查、至少双副本、灰度和回滚的 OCI 容器平台。当前云厂商、区域和预算尚未选定，本机也没有 Docker，因此不能把本地 Compose 或静态配置当作生产部署证据。

## 决策

1. Backend、Agent、Frontend 分别提供独立 liveness/readiness。liveness 只表示进程存活；readiness 才检查接流量条件，Agent 与 Backend readiness 包含数据库可用性。
2. 三个应用镜像必须以非 root 用户运行。生产运行时使用只读根文件系统、临时 tmpfs、无新增权限并丢弃 Linux capabilities。
3. Dockerfile 允许从构建参数注入带 Digest 的基础镜像；正式发布的应用镜像必须使用 repository@sha256:digest，禁止 latest。
4. development、staging、production 使用显式环境与 Spring Profile。production 启动时必须拒绝开发 Profile、弱数据库凭证、非 HTTPS CORS、关闭鉴权、关闭遥测、可变发布版本、Flyway 自动 baseline 和公开 OpenAPI。
5. Flyway V1～V17 记录 SHA-256 并视为不可变。后续迁移必须连续编号，并由自动门禁拒绝 DROP、TRUNCATE、RENAME 和直接类型替换，采用 Expand/Contract。
6. CI 增加 hardened 容器构建和四服务 readiness 冒烟。正式发布还必须提供 staging 迁移、回滚、Dashboard/告警和安全扫描证据。
7. 发布采用 10%→50%→100% 金丝雀步骤，每步至少观察 15 分钟；任一门禁失败自动停止。应用回滚目标不超过 10 分钟，数据库使用向前修复而不是破坏性回滚。
8. 在平台选型关闭之前，生产发布前置检查必须失败。Docker Compose 仍只用于本地开发和单机集成验证。

## 取舍

平台无关契约可以立即固定健康、安全、镜像、迁移和证据边界，减少未来云平台迁移成本；代价是目前只能达到“可进入 staging 验证”，不能完成真实蓝绿/金丝雀部署和一键回滚。

## 风险

- 只读文件系统可能暴露应用对隐式写目录的依赖，必须由远端 Docker 冒烟验证。
- 平台健康检查、终止信号和 Ingress 行为可能与 Compose 不同，选型后必须补充真实演练。
- 锁定 Flyway 校验和意味着已发布迁移只能通过新迁移修正，不能原地编辑。

## 回滚

若运行加固导致启动失败，可在 staging 暂时关闭只读文件系统定位写路径，但不得以此配置进入 production。发布失败时重新部署上一稳定 Digest；数据库只执行与旧应用兼容的 Expand 阶段或新的前向修复迁移。
