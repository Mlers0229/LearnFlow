import { expect, test } from '@playwright/test';

async function mockPlanGeneration(page) {
  let paused = false;
  let resumed = false;
  let resumedPolls = 0;

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = (body, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body)
    });

    if (path === '/api/auth/refresh') {
      return json({ accessToken: 'e2e-token', id: 1, username: 'Mlers', role: 'user', level: 'beginner' });
    }
    if (path === '/api/plan/tasks') {
      return json({ id: 'task-e2e-1', status: 'PENDING', progress: 8 }, 202);
    }
    if (path.endsWith('/pause')) {
      paused = true;
      return json({ id: 'task-e2e-1', status: 'PAUSED', progress: 28 });
    }
    if (path.endsWith('/resume')) {
      paused = false;
      resumed = true;
      return json({ id: 'task-e2e-1', status: 'RUNNING', progress: 42 }, 202);
    }
    if (path === '/api/tasks/task-e2e-1' && request.method() === 'DELETE') {
      return json({ id: 'task-e2e-1', status: 'CANCELLED', progress: 28 });
    }
    if (path === '/api/tasks/task-e2e-1') {
      if (paused) return json({ id: 'task-e2e-1', status: 'PAUSED', progress: 28 });
      if (!resumed) return json({ id: 'task-e2e-1', status: 'RUNNING', progress: 28 });
      resumedPolls += 1;
      if (resumedPolls < 2) return json({ id: 'task-e2e-1', status: 'RUNNING', progress: 72 });
      return json({ id: 'task-e2e-1', status: 'SUCCEEDED', progress: 100, result_resource_id: 42 });
    }
    if (path === '/api/plan/42/progress') {
      return json({ totalDays: 1, completedDays: 0, completionRate: 0 });
    }
    if (path === '/api/plan/42/resources') return json([]);
    if (path === '/api/plan/42') {
      return json({
        id: 42,
        title: 'Java 后端八周学习计划',
        goal_text: '独立完成 Spring Boot 项目',
        duration_weeks: 8,
        hours_per_day: 1,
        level: 'beginner',
        days: [{
          id: 101,
          date: '2026-08-27',
          title: '搭建 Java 开发环境',
          tasks: ['安装 JDK 21', '创建第一个 Spring Boot 项目'],
          status: 'not_started'
        }]
      });
    }

    return json({ message: `Unhandled E2E route: ${path}` }, 404);
  });
}

test('四步向导支持暂停、继续并展示生成结果', async ({ page }) => {
  await mockPlanGeneration(page);
  await page.goto('/');

  await page.getByPlaceholder('例如：8 周转向 Java 后端开发，独立完成并部署一个 Spring Boot 项目')
    .fill('8 周掌握 Java 后端并完成可部署的 Spring Boot 项目');
  await page.getByPlaceholder('例如：Java 后端工程师').fill('Java 后端工程师');
  await page.getByPlaceholder('例如：完成一个可部署的小项目').fill('部署一个带数据库的课程项目');
  await page.getByRole('button', { name: '下一步' }).click();
  await page.getByRole('button', { name: '下一步' }).click();
  await page.getByPlaceholder(/周一到周五只能晚上学习/).fill('工作日只能晚上学习');
  await page.getByRole('button', { name: '下一步' }).click();

  await expect(page.getByText('8 周掌握 Java 后端并完成可部署的 Spring Boot 项目')).toBeVisible();
  await page.getByRole('button', { name: '确认并生成计划' }).click();
  await expect(page.getByText('task-e2e-1')).toBeVisible();

  await page.getByRole('button', { name: '暂停' }).click();
  await expect(page.getByRole('heading', { name: '任务已安全暂停' })).toBeVisible();
  await page.getByRole('button', { name: '继续' }).click();

  await expect(page.getByRole('heading', { name: '学习计划与执行蓝图' })).toBeVisible({ timeout: 10_000 });
  await expect(page.getByText('Java 后端八周学习计划').first()).toBeVisible();
});

test('取消生成后保留输入草稿并可在刷新后恢复', async ({ page }) => {
  await mockPlanGeneration(page);
  await page.goto('/');

  const goal = '6 周完成数据结构系统复习和项目实践';
  await page.getByPlaceholder('例如：8 周转向 Java 后端开发，独立完成并部署一个 Spring Boot 项目').fill(goal);
  await page.getByPlaceholder('例如：Java 后端工程师').fill('后端开发');
  await page.getByPlaceholder('例如：完成一个可部署的小项目').fill('完成可演示项目');
  await page.getByRole('button', { name: '下一步' }).click();
  await page.getByRole('button', { name: '下一步' }).click();
  await page.getByRole('button', { name: '下一步' }).click();
  await page.getByRole('button', { name: '确认并生成计划' }).click();

  await page.getByRole('button', { name: '取消任务' }).click();
  await expect(page.getByRole('heading', { name: '任务已取消' })).toBeVisible();
  await expect(page.getByText('任务已取消，输入草稿仍然保留。')).toBeVisible();

  await page.reload();
  await expect(page.getByText('已恢复上次未完成的输入草稿')).toBeVisible();
  await expect(page.getByPlaceholder('例如：8 周转向 Java 后端开发，独立完成并部署一个 Spring Boot 项目')).toHaveValue(goal);
});
