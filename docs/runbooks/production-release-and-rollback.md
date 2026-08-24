# 生产发布与回滚 Runbook

## 当前状态

云厂商、区域和预算尚未选定，ops/deployment/runtime-contract.json 会阻断 production。以下流程用于平台选定后的 staging/production 发布；Compose 只用于 CI 冒烟。

## 发布前置条件

1. 执行 python tools/check_deployment_assets.py 和 python tools/check_flyway_contract.py。
2. CI、三语言测试、OpenAPI、RAG regression、SAST/SCA/Secret/容器扫描和 SBOM 全部通过。
3. 三个应用镜像均使用 repository@sha256:digest，记录当前稳定 Digest。
4. staging 使用与 production 相同的 Profile、Secret 来源、托管 PostgreSQL版本、健康检查和网络边界。
5. 新迁移先执行 Expand，旧应用和新应用均能运行；Contract 至少延后一轮稳定发布。
6. 完成备份/PITR 检查，指定发布、观察和回滚负责人。
7. `disaster-recovery` 报告必须来自同一不可变 staging 版本，六个必做场景全部通过且 RPO/RTO 达标。
8. release candidate 必须引用版本化 evidence manifest；每项门禁绑定同一不可变版本、指定环境、完成时间、有效期、Artifact 类型和 SHA-256。
9. `data-governance` 报告必须验证保留任务、账户/对象存储擦除、导出、备份边界、隐私审批、区域、子处理方和敏感日志审查。

## Staging 证据

必须记录：

- V1～最新版本迁移和 JPA validate 结果。
- Backend、Agent 数据库角色与 Agent 零写权限检查。
- 三服务 liveness/readiness、优雅停机和至少一次副本替换。
- 页面→Backend→任务→Agent→数据库/模型 Trace。
- Dashboard 有数据，至少一条测试告警完成 firing→routing→resolved。
- 上一稳定 Digest 回滚演练和实际用时。
- 自动备份/PITR、误删、数据库不可用、区域故障、模型故障和 Worker 全重启的灾备报告及清理记录。
- 数据治理报告：保留任务、账户擦除、对象存储擦除、数据导出、备份限制、隐私审批和数据处理方清单。

真实 release candidate 只引用 evidence manifest；证据文件必须与 manifest 同 Bundle 并匹配 SHA-256。不得修改 candidate 或 manifest 模板伪造通过状态。

## 金丝雀发布

1. 运行 python tools/check_release_candidate.py candidate.json；任何错误都停止。
2. 先部署迁移任务，成功后启动候选应用，但保持 0% 流量。
3. readiness 全部通过后切入 10% 流量，观察至少 15 分钟。
4. 检查 5xx、P95/P99、SLO Burn Rate、AI 降级、队列年龄、数据库池、Token/成本和业务成功率。
5. 无回归后依次提升至 50% 和 100%，每一步重复观察。
6. 观察窗口结束后再允许执行兼容的 Contract 迁移。

## 回滚触发条件

出现以下任一情况立即停止扩流并回滚：

- readiness 连续失败、5xx 或 Burn Rate 超过发布告警阈值。
- 数据错误、权限越界、迁移/JPA 校验异常。
- AI 成功率、RAG 回归或成本出现不可接受变化。
- 队列、线程、连接或内存持续增长。
- Trace/日志发现凭证或不必要个人数据。

## 应用回滚

1. 将流量切回上一稳定 Digest，停止候选版本扩流。
2. 保持 Expand Schema，不执行 DROP、降级迁移或恢复旧数据库快照。
3. 等待旧版本 readiness，通过关键登录、计划查询、RAG 和 Tutor 冒烟。
4. 目标 10 分钟内恢复；记录实际时间、影响请求和 Trace。
5. 若旧版本也失败，按数据库、模型或队列 Runbook 继续处置。

## 数据库异常

- 迁移尚未提交：停止迁移任务并保留日志。
- Expand 已提交：回滚应用并保留兼容 Schema，使用新迁移前向修复。
- 数据损坏：停止写流量，按数据库 Runbook 执行 PITR；不得在未确认恢复点时覆盖生产库。
