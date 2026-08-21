# ADR-0001：系统边界与初期生产平台

- 状态：Accepted
- 日期：2026-08-21
- 决策人：LearnFlow 维护者

## 背景

LearnFlow 当前由 Vue 前端、Spring Boot 后端、FastAPI Agent 平台和 PostgreSQL 组成。开发环境通过 Docker Compose 启动，但 Compose 单机部署不满足 99.9% 可用性、滚动发布和数据库恢复要求。

## 决策

1. Vue 只负责展示与交互，不作为身份、角色或资源归属的可信来源。
2. Spring Boot 是唯一公网业务 API 和授权执行点，负责认证、RBAC、资源归属、事务和 Agent 调用编排。
3. FastAPI 仅作为内网 Agent 服务，由 Spring Boot 使用独立服务凭证调用；浏览器不得直连。
4. PostgreSQL 是业务 Schema 的唯一事实源。业务写入优先经过 Spring Boot；Agent 仅获得完成任务所需的最小权限。
5. 外部模型服务只允许从 Agent/后端通过受控出口访问，调用必须设置超时、预算、审计和降级。
6. 初期生产平台采用托管 OCI 容器平台：公网负载均衡/Ingress、至少两个 Backend 副本、至少两个 Agent Worker 副本，以及支持 PITR 的托管 PostgreSQL。云厂商保持可替换，平台必须满足健康检查、Secret 注入、滚动/灰度发布和一键回滚契约。
7. Docker Compose 仅用于本地开发和单机集成验证，不作为正式生产高可用方案。

## 取舍

- 保留现有技术边界，降低迁移成本。
- 暂不绑定具体云厂商，减少尚未确定预算和区域带来的返工；代价是 M5 前必须完成平台选型和容量报价。
- FastAPI 不直接对浏览器提供流式接口；流式响应由 Spring Boot 代理，增加一跳但统一了权限和审计边界。

## 风险

- 后端代理 AI 流量可能成为瓶颈，需要在 M2 配置连接池、超时、取消和 Bulkhead。
- 云厂商未最终选定可能影响日志、Secret 和发布实现，M5 前必须关闭该决策项。

## 回滚

组件边界不依赖特定平台。如果托管容器平台不满足成本或区域要求，可迁移到另一满足同一运行契约的平台；数据库通过标准 PostgreSQL 备份/PITR 迁移。
