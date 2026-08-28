import { expect, test } from '@playwright/test';

async function mockAdminCloseout(page) {
  const taskId = '123e4567-e89b-12d3-a456-426614174000';
  const users = [
    { id: 1, username: 'Mlers', email: 'mlers@example.com', role: 'admin', level: 'advanced', status: 'ACTIVE', createdAt: '2026-08-01T08:00:00Z' },
    { id: 2, username: 'learner01', email: 'learner@example.com', role: 'student', level: 'beginner', status: 'ACTIVE', createdAt: '2026-08-27T08:00:00Z' }
  ];
  const audits = [];
  const logs = [
    { id: 20, traceId: 'trace-plan-001', agentName: 'PlanAgent', modelName: 'deepseek-chat', durationMs: 1280, requestPayload: JSON.stringify({ task_id: taskId, goal: 'Java' }), responsePayload: JSON.stringify({ status: 'ok' }), createdAt: '2026-08-28T08:00:00Z' },
    { id: 21, traceId: 'trace-tutor-002', agentName: 'TutorAgent', modelName: 'deepseek-chat', durationMs: 4380, requestPayload: JSON.stringify({ question: 'DI' }), responsePayload: JSON.stringify({ score: 88 }), createdAt: '2026-08-28T08:05:00Z' }
  ];

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

    if (path === '/api/auth/refresh') return json({ accessToken: 'admin-closeout-token', id: 1, username: 'Mlers', role: 'admin', level: 'advanced' });
    if (path === '/api/agent/logs') return json(logs);
    if (path === '/api/admin/users' && request.method() === 'GET') return json(users);
    if (path === '/api/admin/audit/logs') return json(audits);
    if (path === '/api/admin/users/2' && request.method() === 'PATCH') {
      const patch = request.postDataJSON();
      Object.assign(users[1], patch);
      audits.unshift({ id: 30, type: 'USER_UPDATE', operator: 'Mlers', targetType: 'USER', targetId: 2, detail: 'E2E status update', createdAt: '2026-08-28T09:00:00Z' });
      return json({ success: true });
    }
    return json({ message: `Unhandled E2E route: ${request.method()} ${path}` }, 404);
  });

  return { taskId };
}

test('Agent 日志支持任务深链筛选、调用链详情和脱敏说明', async ({ page }) => {
  const { taskId } = await mockAdminCloseout(page);
  await page.goto(`/admin/logs?taskId=${taskId}`);

  await expect(page.getByRole('heading', { name: 'Agent 调用日志' })).toBeVisible();
  await expect(page.getByRole('searchbox', { name: '任务 ID' })).toHaveValue(taskId);
  await expect(page.getByText('/ 2 条')).toBeVisible();
  await expect(page.getByRole('button', { name: '查看详情' })).toHaveCount(1);

  await page.getByRole('button', { name: '查看详情' }).click();
  await expect(page.getByRole('heading', { name: 'PlanAgent' })).toBeVisible();
  await expect(page.getByText(taskId, { exact: true })).toBeVisible();
  await expect(page.getByText('已由服务端脱敏并限长')).toBeVisible();
  await expect(page.getByRole('link', { name: '查看关联任务' })).toBeVisible();
});

test('用户管理保护当前最后管理员并通过确认流程禁用普通用户', async ({ page }) => {
  await mockAdminCloseout(page);
  await page.goto('/admin/users');

  const currentAdminRow = page.getByRole('row').filter({ hasText: 'Mlers' });
  await currentAdminRow.getByRole('button', { name: '管理' }).click();
  await page.getByRole('button', { name: '改为学习者' }).click();
  await expect(page.getByRole('alert')).toContainText('不能降低或禁用当前登录管理员的权限');
  await expect(page.getByRole('button', { name: '确认降级' })).toBeDisabled();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '关闭', exact: true }).click();

  const learnerRow = page.getByRole('row').filter({ hasText: 'learner01' });
  await learnerRow.getByRole('button', { name: '管理' }).click();
  await page.getByRole('button', { name: '禁用账号' }).click();
  await page.getByRole('button', { name: '确认禁用' }).click();

  await expect(page.getByText('用户操作已完成并写入审计记录。')).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'learner01' })).toContainText('已禁用');
});

test('管理收尾页在窄屏下不产生页面级横向溢出', async ({ page }) => {
  await mockAdminCloseout(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/admin/logs');

  await expect(page.getByRole('heading', { name: 'Agent 调用日志' })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(overflow).toBe(false);
});
