# LearnFlow 前端重做基线审计

> 审计日期：2026-08-26；最近更新：2026-08-27
> 对应路线图：`docs/frontend-redesign-roadmap.md`
> 基线分支：`codex/docker-deploy`

## 1. 质量基线

| 检查项 | 结果 |
| --- | --- |
| ESLint | PASS，0 error、6 warning（均为既有页面告警） |
| Vitest | PASS，20 个测试文件、66 个测试 |
| Vite production build | PASS |
| TypeScript | PASS，`vue-tsc --noEmit` |
| Backend Maven tests | PASS |
| Playwright | 既有桌面/移动 30 项 PASS；批次 6 管理总览、资源管理与模型配置完成；模型页已验证连接、同步、保存、Secret 脱敏及 390px 无溢出 |
| 改造前主入口 JS | 995.50 kB，gzip 264.56 kB |
| 批次 6 当前主入口 JS | 242.61 kB，gzip 72.34 kB |
| 最大业务路由 JS | Chat 82.40 kB，gzip 30.10 kB |
| 管理总览路由 | 16.24 kB，gzip 6.65 kB；按需图表 1.22 kB，gzip 0.71 kB |
| 资源管理路由 | 23.38 kB，gzip 8.13 kB |
| 模型配置路由 | 13.44 kB，gzip 5.35 kB |
| 资源上传路由 | 24.63 kB，gzip 9.56 kB |
| 个人设置路由 | 24.47 kB，gzip 8.94 kB |
| 关于路由 | 9.09 kB，gzip 4.32 kB |
| 生成计划路由 | 50.74 kB，gzip 16.78 kB |
| 历史计划路由 | 29.03 kB，gzip 10.45 kB |
| 练习回顾路由 | 21.53 kB，gzip 7.92 kB |

批次 1 已将 Naive UI 改为按需解析；批次 2 完成用户端、管理端与认证外壳；批次 3 完成生成计划与历史执行主流程；批次 4 完成练习回顾与 AI 对话；批次 5 完成全部用户端页面；批次 6 已完成管理总览、资源管理与模型配置。模型配置保持部署 Secret 边界，新增连接探测、目录同步、草稿保护与运行态生效说明。主入口 JS 为 242.61 kB，gzip 72.34 kB，构建无 500 kB Chunk 警告。

## 2. 路由与权限清单

| 路由 | 页面 | 当前角色 | 首批处理 |
| --- | --- | --- | --- |
| `/` | 生成学习计划 | 登录用户 | 保持 |
| `/history` | 历史计划 | 登录用户 | 保持 |
| `/exercise-review` | 练习回顾 | 登录用户 | 保持 |
| `/chat` | AI 对话 | 登录用户 | 保持 |
| `/upload-resource` | 上传资源 | 登录用户 | 保持 |
| `/profile` | 个人设置 | 登录用户 | 保持 |
| `/about` | 关于 | 登录用户 | 保持；公共介绍页待独立公共外壳 |
| `/debug/agent-logs` | Agent 日志 | 登录用户 | 已改为管理员权限 |
| `/admin` | 管理总览 | 管理员 | 已重做 |
| `/admin/resources` | 资源管理 | 管理员 | 已重做 |
| `/admin/models` | 模型配置 | 管理员 | 保持 |
| `/admin/logs` | Agent 日志 | 管理员 | 保持 |
| `/admin/users` | 用户管理 | 管理员 | 保持 |
| `/forbidden` | 403 | 公开系统页 | 已新增 |
| `/error` | 系统错误 | 公开系统页 | 已新增 |
| `/:pathMatch(.*)*` | 404 | 公开系统页 | 已新增 |

## 3. 页面规模与拆分优先级

| 页面 | 约行数 | 优先级 | 首要拆分方向 |
| --- | ---: | --- | --- |
| PlanHistoryPage | 353 | P0 | 已拆为状态 composable、计划导航、每日执行与资源面板，并支持指定学习日回跳 |
| PlanGeneratorPage | 219 | P0 | 已拆为页面编排、四步向导、任务监控与 composable |
| ResourceUploadPage | 1234 | P1 | 上传向导、资源预检、提交记录 |
| ResourceManagePage | 250 + 198 行组件 | Done | 风险优先筛选、标准表格、反馈详情侧栏、批量确认与 CSV |
| ExerciseReviewPage | 161 | P0 | 已拆为状态 composable、筛选、掌握度与练习记录组件 |
| AdminDashboardPage | 81 + 72 行数据层 | Done | 真实指标、风险下钻、持久任务状态与按需可访问图表 |
| AdminModelConfigPage | 73 + 153 行状态/组件 | Done | Provider、Secret 状态、连接探测、目录同步、默认模型与 Agent 策略 |
| ChatPage | 173 | P0 | 已拆为流式状态 composable、消息、输入区与学习上下文组件 |
| AboutPage | 159 | Done | 已压缩为产品能力、工作方式、数据、版本与联系信息 |

## 4. 全局交互基线

所有页面重做必须保留并统一以下状态：

- 加载中与 Skeleton。
- 首次空数据与筛选无结果。
- 网络失败、业务失败和权限失败。
- 明确的重试入口。
- 保存中、保存成功、保存失败和未保存修改。
- 危险操作影响说明和二次确认。
- 长任务进度、暂停、继续、取消、恢复和任务标识。
- 移动端操作区不被软键盘或安全区遮挡。

## 5. 批次 0 尚需补齐的真实浏览器证据

### 批次 2 已新增证据

- 用户端与管理端分别使用独立响应式侧边栏、顶部上下文栏和移动抽屉。
- 登录、注册、密码重置提升为顶级公开路由，不再嵌套业务工作台布局。
- 登录态用户导航、管理员入口、管理控制台导航和未登录权限回跳已覆盖桌面与移动端。
- 360px 与 768px 用户工作台无页面级横向溢出。
- 统一 API 错误对象、网络错误映射、MSW 测试服务器与网络状态组件测试已落地。
- 浏览器基线截图：
  - `output/playwright/baseline/login-batch2-desktop-1440.png`
  - `output/playwright/baseline/register-batch2-desktop-1440.png`
  - `output/playwright/baseline/reset-password-batch2-mobile-390.png`

本地静态预览没有后端代理时，`/api/auth/refresh` 会返回预期的 404；自动化场景通过网络 Mock 分别验证未登录态和管理员登录态。该错误不出现在真实前后端同源部署中。

### 批次 3 生成计划切片证据

- 生成页面从 1271 行降至 219 行。
- 新增四步向导：目标、时间条件、学习偏好、最终确认。
- 新增独立持久化任务 composable，统一轮询、暂停、继续、取消、失败恢复和计时。
- 输入草稿按账号自动保存在本机，取消或刷新后可以恢复。
- CLI 真实浏览器验收发现并修复“暂停响应被旧轮询覆盖”的竞态，已增加单元回归测试。
- 桌面和移动端 E2E 覆盖：生成成功、暂停、继续、取消、刷新恢复草稿。
- 浏览器基线截图：
  - `output/playwright/baseline/plan-generator-batch3-success-desktop-1440.png`
  - `output/playwright/baseline/plan-generator-batch3-mobile-390.png`

生成计划切片使用隔离的浏览器网络 Mock 验证任务状态机，不包含真实 Token、API Key 或用户数据。后续仍需在 staging 使用真实后端任务完成长任务 Trace。
### 批次 3 历史计划切片证据

- 历史页从 2077 行降至 345 行，页面只保留业务编排。
- 新增独立历史状态 composable，覆盖计划加载、并发响应保护、最近计划与日期恢复、进度统计和标题同步。
- 计划导航、每日执行、资源推荐分别拆为独立组件；长日程使用固定高度滚动索引，后续大规模数据再引入虚拟列表。
- 原生 `prompt`、`confirm`、`alert` 已替换为带校验、加载态、错误态和影响说明的应用内弹窗/通知。
- 桌面与移动端 E2E 覆盖：细化任务、完成/撤销打卡、加载资源、生成练习、AI 评测、顺延重排、重命名、删除确认。
- 390px 窄屏无页面级横向溢出；全站桌面/移动端 30 项 E2E 全部通过。
- 浏览器基线截图：
  - `output/playwright/baseline/plan-history-batch3-desktop-chromium.png`
  - `output/playwright/baseline/plan-history-batch3-mobile-chromium.png`

历史页验证使用隔离的浏览器网络 Mock，不包含真实 Token、API Key 或用户数据；部署前仍需在 staging 对真实资源推荐和 AI 长请求做一次联调验收。

### 批次 4 练习回顾切片证据

- 练习回顾页从 979 行降至 161 行，页面只保留业务编排。
- 新增独立练习回顾 composable，统一计划/日期/状态/知识点筛选、统计、掌握度、删除、标记复习、重算和并发响应保护。
- 筛选、掌握度画像和按学习日聚合的记录列表拆为独立组件；答案、AI 评分、反馈、下一步建议和参考答案在同一复盘路径展示。
- 删除单题、清空学习日均使用带影响说明的应用内确认；标记复习和重算提供成功/失败反馈。
- 错题可直接返回对应计划和学习日，历史页会恢复目标日期。
- Playwright CLI 已验证薄弱知识点筛选、展开参考答案、标记复习、删除取消、学习日回跳及 390px 无横向溢出。
- 浏览器验收截图：
  - `output/playwright/review-batch4/exercise-review-batch4-desktop-1440.png`
  - `output/playwright/review-batch4/exercise-review-batch4-mobile-390.png`

本切片使用隔离网络 Mock，不包含真实 Token、API Key 或用户数据。验收会话中的 `/api/chat/models` 404 来自 Mock 未覆盖后台预取，与练习回顾页面和接口无关；部署前仍需在 staging 使用真实掌握度数据做一次联调。

### 批次 4 AI 对话切片证据

- AI 对话页从 691 行降至 173 行，页面仅编排会话、输入区和学习上下文。
- 新增流式会话状态 composable，覆盖模型状态、SSE 分块、停止、重新生成、失败重试、清空和竞态隔离。
- 新增学习上下文 composable，可选择近期计划和学习日，并显式控制是否发送任务与推荐资源。
- Markdown 经 DOMPurify 清洗；代码块、长文本、外部链接和来源卡片统一展示，来源由回答中的安全 Markdown 链接提取。
- 移动端输入区固定在安全区上方；390px 视口 `scrollWidth === clientWidth`，无页面级横向溢出。
- Playwright CLI 已验证 Enter 发送、流式回答、复制、重新生成、中止后保留部分回答、503 失败原位重试、上下文开关和学习日精确回跳；控制台 0 error。
- 浏览器验收截图：
  - `output/playwright/chat-batch4/ai-chat-batch4-desktop-1440.png`
  - `output/playwright/chat-batch4/ai-chat-batch4-mobile-390.png`

本切片使用隔离网络 Mock，不包含真实 Token、API Key 或用户数据。后端当前仅提供 SSE 文本流，没有独立引用元数据；引用卡片基于模型返回的 Markdown 链接生成，部署前仍需使用真实模型验证来源质量与长回答稳定性。

### 批次 6 资源管理切片证据

- 资源管理页从 1055 行降至 250 行，表格、详情侧栏、确认弹窗与纯数据工具独立拆分。
- 资源按举报数量优先，其次按待审状态和资源 ID 排序；筛选条件同步到 URL，可从管理总览直接下钻。
- 详情侧栏汇总质量风险、摄取任务、可编辑元数据和最近反馈；重新摄取、单条上下线与批量上下线均显示影响数量并二次确认。
- Playwright CLI 已验证高风险筛选、反馈加载、重新摄取确认、两条资源批量下线请求、CSV 下载；请求体为 `ids=[101,102]`、`status=INACTIVE`。
- 390px 视口页面宽度为 390px，无页面级横向溢出；1080px 数据表格收纳在 332px 内部滚动容器中；浏览器控制台 0 error、0 warning。
- 浏览器验收截图：
  - `output/playwright/admin-resources-batch6/resources-desktop-1440.png`
  - `output/playwright/admin-resources-batch6/resources-mobile-390.png`

本切片使用隔离网络 Mock 验证管理员操作，不包含真实 Token、API Key 或用户数据；部署前仍需在 staging 对真实资源反馈、状态变更和摄取任务进行一次联调。

### 批次 6 模型配置切片证据

- 模型配置页从 738 行拆为 73 行页面编排、90 行状态层和两个职责组件；提供商、凭据、默认模型、Agent 策略、运行态与目录分区清晰。
- 遵守生产 Secret 约束：浏览器没有 API Key 输入框，保存请求不包含 `apiKey`，只展示服务器返回的脱敏摘要和 Secret 来源。
- “测试连接”复用现有管理员模型目录探测，只测试服务器当前生效配置；同步目录分别展示进行中、成功、失败、跳过和重试状态。
- 未保存草稿会阻止连接测试和目录同步，避免远端结果覆盖管理员输入；保存栏覆盖未修改、未保存、保存中、成功和失败。
- Playwright CLI 已验证 DeepSeek 识别、连接成功、选择 `deepseek-reasoner`、未保存同步拦截、保存请求正文、同步成功及 Secret 脱敏；全新会话控制台 0 error、0 warning。
- 390px 视口 `scrollWidth === clientWidth === 390`，无页面级横向溢出；浏览器验收截图：
  - `output/playwright/admin-models-batch6/models-desktop-1440.png`
  - `output/playwright/admin-models-batch6/models-mobile-390.png`

本切片使用隔离网络 Mock，不包含真实 Token、API Key 或用户数据；部署前仍需在 staging 使用服务器已注入的真实 Secret 执行一次连接和目录同步联调。

### 仍需补齐

- [x] 登录页 1440px 桌面全页截图和 DOM 快照。
- [x] 404 页 1440px 桌面全页截图和 DOM 快照。
- [ ] 学生账号全路由桌面截图。
- [ ] 管理员账号全路由桌面截图。
- [ ] 360/768/1440 三种宽度关键页截图。
- [ ] 生成计划真实后端长任务完整 Trace 与界面状态截图（Mock 状态机证据已完成）。
- [ ] 练习生成、答题、评估完整流程截图。
- [ ] 模型配置保存和模型目录同步截图。
- [x] 登录页桌面和移动端自动可访问性扫描（WCAG 2 A/AA 与 2.1 A/AA，0 violation）。
- [ ] 登录态页面键盘导航和可访问性扫描。

真实浏览器证据统一保存到 `output/playwright/`。当前截图位于 `output/playwright/baseline/`，不提交包含 Token、API Key 或用户隐私的数据。
