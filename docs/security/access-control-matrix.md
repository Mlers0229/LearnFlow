# LearnFlow 权限与资源归属矩阵

更新日期：2026-08-21

## 角色与可信身份

- `anonymous`：只能注册、登录、刷新、注销和访问健康检查。
- `student`：只能操作 JWT `sub` 对应用户自己的业务数据。
- `admin`：拥有 student 能力，并可访问显式标记为 `ROLE_ADMIN` 的管理接口。
- 客户端传入的 `userId`、`uploaderUserId`、`uploaderUsername` 和本地存储角色均不可信。
- 跨用户资源统一返回 404，避免泄露资源是否存在。

## 接口矩阵

| 接口范围 | anonymous | student | admin | 资源归属规则 |
| --- | --- | --- | --- | --- |
| `/api/auth/register`、`/login` | 允许 | 允许 | 允许 | 不适用 |
| `/api/auth/refresh`、`/logout` | 仅白名单 Origin | 仅白名单 Origin | 仅白名单 Origin | Refresh Cookie 会话 |
| `/api/plan/**` | 401 | 允许 | 允许 | `study_plan.user_id = JWT sub` |
| `/api/tasks/{taskId}` | 401 | 仅本人 | 仅本人 | `async_task.owner_user_id = JWT sub`，跨用户返回 404 |
| `/api/exercise-records/**` | 401 | 允许 | 允许 | `exercise_record.user_id = JWT sub` |
| `/api/resources/mine`、反馈、上传 | 401 | 允许 | 允许 | 上传者/反馈用户由 JWT 覆盖 |
| `/api/resources` 管理操作 | 401 | 403 | 允许 | `ROLE_ADMIN` |
| `/api/chat/admin-config/**` | 401 | 403 | 允许 | `ROLE_ADMIN` |
| `/api/admin/**`、`/api/agent/logs` | 401 | 403 | 允许 | `ROLE_ADMIN` |

## 自动化证据

- `AdminAuthorizationWebMvcTest`：匿名 401、student 对所有管理读取面 403、admin 成功访问。
- `TrustedIdentityControllerTest`：计划生成、资源上传、资源反馈和练习查询只能使用 JWT 身份。
- `PlanQueryServiceOwnershipTest`：跨用户学习日返回 404。
- `ExerciseRecordServiceOwnershipTest`：禁止把练习写入他人学习日，删除使用所有者范围查询。
- `CookieOriginValidationFilterTest`：刷新/注销拒绝缺失或非白名单 Origin。
- `AsyncTaskServiceTest`：任务创建覆盖客户端身份，查询与取消始终使用所有者范围，幂等键不能复用于不同参数。
- `AdminAuthorizationWebMvcTest`：student 访问失败任务列表返回 403；死信重放接口由 `ROLE_ADMIN` 方法级授权保护并记录审计。
