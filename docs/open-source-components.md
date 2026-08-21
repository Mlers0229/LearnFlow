# LearnFlow 开源代码与组件使用情况说明

## 一、总体说明

LearnFlow 项目为自主设计与开发的智能学习规划平台，系统业务代码、页面交互、数据模型、接口封装、多 Agent 编排逻辑及部署脚本均围绕本项目需求独立实现。项目开发过程中未直接复制或改造第三方开源项目的完整业务代码，也未将外部开源系统作为本项目主体功能直接套用。

为提高开发效率、保证系统稳定性和可维护性，项目通过 Maven、npm 和 pip 等标准包管理工具引入了若干成熟开源框架与基础组件。这些组件主要用于 Web 服务、前端渲染、数据库访问、参数校验、HTTP 调用、Markdown 渲染、内容净化和 Agent 服务搭建等通用技术能力，不涉及项目核心业务方案、学习计划生成流程、资源管理逻辑、练习评测流程和管理端功能设计的直接来源。

## 二、后端开源组件使用情况

后端位于 `backend/` 目录，采用 Java 17 与 Spring Boot 3 构建，主要开源组件如下：

| 组件 | 当前用途 | 许可证/开源协议 |
| --- | --- | --- |
| Spring Boot 3.3.4 | 后端应用启动、配置管理、Web 服务基础框架 | Apache License 2.0 |
| Spring Boot Starter Web | REST API、控制器、HTTP 请求处理 | Apache License 2.0 |
| Spring Boot Starter Validation | 请求参数校验与数据约束 | Apache License 2.0 |
| Spring Boot Starter Data JPA | ORM 持久化访问、Repository 抽象 | Apache License 2.0 |
| Spring Security Crypto | 密码哈希与安全加密能力 | Apache License 2.0 |
| Jackson Databind | JSON 序列化与反序列化 | Apache License 2.0 |
| PostgreSQL JDBC Driver | 连接 PostgreSQL 数据库 | PostgreSQL License / BSD-style |
| Spring Boot Starter Test | 单元测试与集成测试基础依赖 | Apache License 2.0 |
| Micrometer / OpenTelemetry | 指标、Trace 与 OTLP 导出 | Apache License 2.0 |
| Resilience4j | 有限重试、Bulkhead 与熔断 | Apache License 2.0 |
| Flyway | PostgreSQL Schema 版本化迁移 | Apache License 2.0 |

上述组件仅承担基础框架和通用技术支撑作用。项目中的用户认证、学习计划、学习资源、练习记录、后台管理、Agent 代理调用等业务服务和控制器均由项目自行编写。

## 三、前端开源组件使用情况

前端位于 `frontend/` 目录，采用 Vue 3 与 Vite 构建，主要开源组件如下：

| 组件 | 当前用途 | 许可证/开源协议 |
| --- | --- | --- |
| Vue 3 | 前端响应式视图层与组件化开发 | MIT License |
| Vue Router | 前端页面路由与导航管理 | MIT License |
| Vite | 前端开发服务器与构建工具 | MIT License |
| @vitejs/plugin-vue | Vue 单文件组件编译支持 | MIT License |
| Naive UI | 页面表单、按钮、布局、弹窗、表格等 UI 组件 | MIT License |
| marked | Markdown 内容解析与渲染 | MIT License |
| DOMPurify | HTML 内容净化，降低富文本渲染风险 | Apache License 2.0 / Mozilla Public License 2.0 |

前端页面结构、用户工作台、管理端页面、计划生成页面、练习回顾页面、资源上传与管理页面、模型配置页面等均为本项目根据业务需求自主实现。UI 组件库仅用于提供基础控件和样式能力。

## 四、Agent 平台开源组件使用情况

Agent 平台位于 `agent-platform/` 目录，采用 Python 与 FastAPI 构建，主要开源组件如下：

| 组件 | 当前用途 | 许可证/开源协议 |
| --- | --- | --- |
| FastAPI 0.115.0 | Agent 服务接口框架 | MIT License |
| Uvicorn 0.30.0 | ASGI 服务运行容器 | BSD 3-Clause License |
| Pydantic 2.9.0 | 请求/响应模型定义与数据校验 | MIT License |
| httpx 0.27.0 | 调用后端服务及外部模型 API | BSD 3-Clause License |
| SQLAlchemy 2.0.34 | Python 侧数据库访问与 ORM 支持 | MIT License |
| psycopg2-binary 2.9.9 | Python 连接 PostgreSQL 数据库 | LGPL with exceptions |
| OpenTelemetry Python 1.44.0 | FastAPI、HTTPX、SQLAlchemy、模型与 Agent 遥测 | Apache License 2.0 |

Agent 平台中的 GoalAgent、PlanAgent、RagAgent、DetailPlanAgent、TutorAgent 以及学习计划编排、资源推荐、练习生成与评测等逻辑均为本项目自主组织和实现。外部大模型接口采用 OpenAI-compatible API 形式接入，属于可配置的第三方服务调用能力，不属于本项目引入的开源代码组件。

## 五、数据库与部署相关组件

项目使用 PostgreSQL 作为关系型数据库，并提供 Linux 单机部署脚本、Nginx 配置模板和 systemd 服务模板。相关内容主要用于本地或服务器环境部署，不改变项目业务代码归属。

| 组件/工具 | 当前用途 | 说明 |
| --- | --- | --- |
| PostgreSQL | 业务数据持久化存储 | 开源关系型数据库 |
| Nginx 配置模板 | 前端静态资源与接口转发部署参考 | 仅提供部署配置 |
| systemd 服务模板 | 后端与 Agent 服务托管运行 | 仅提供部署配置 |
| OpenTelemetry Collector 0.134.0 | 汇聚 OTLP Trace 与指标 | Apache License 2.0 |
| Jaeger 2.20.0 | 本地 Trace 查询与排障 | Apache License 2.0 |
| Prometheus 3.13.1 | 指标存储、Recording Rules 与告警计算 | Apache License 2.0 |
| Alertmanager 0.32.1 | 告警分组、抑制与路由 | Apache License 2.0 |
| Grafana 13.1.0 | API、Agent、数据库、队列和成本 Dashboard | GNU Affero General Public License v3.0 |

## 六、开源组件使用边界

1. 本项目未将第三方开源项目的完整业务系统作为基础进行二次开发。
2. 本项目未直接复制第三方项目中的核心业务代码、页面实现或数据库设计。
3. 开源组件均通过标准依赖管理方式引入，用于通用技术能力支撑。
4. 项目核心功能，包括学习目标解析、计划生成、历史计划工作台、资源推荐、练习评测、后台管理与 Agent 编排流程，均由项目自行设计与实现。
5. 项目保留各开源组件原有版权和许可证声明，实际发布或交付时应随依赖清单一并保留对应许可证信息。

## 七、结论

LearnFlow 项目属于在开源基础框架和通用组件支撑下完成的自主开发系统。开源组件的使用范围主要集中在基础框架、前端控件、数据库访问、HTTP 通信、数据校验和服务运行等通用层面，未影响项目核心业务逻辑、功能设计和系统实现的原创性。
