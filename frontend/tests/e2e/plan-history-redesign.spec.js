import { expect, test } from '@playwright/test';

async function mockPlanHistory(page) {
  const plan = {
    id: 7,
    title: 'Java 工程化进阶',
    status: 'active',
    start_date: '2026-08-25',
    end_date: '2026-08-27',
    days: [
      { id: 71, date: '2026-08-25', title: '建立工程骨架', tasks: ['创建 Spring Boot 项目'], status: 'in_progress' },
      { id: 72, date: '2026-08-26', title: '实现持久化层', tasks: ['设计实体与仓储'], status: 'not_started' },
      { id: 73, date: '2026-08-27', title: '完成部署', tasks: ['构建镜像'], status: 'not_started' }
    ]
  };

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = (body, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body)
    });

    if (path === '/api/auth/refresh') return json({ accessToken: 'history-token', id: 1, username: 'Mlers', role: 'user' });
    if (path === '/api/plan/recent') return json([{ id: 7, title: plan.title, status: plan.status, start_date: plan.start_date, end_date: plan.end_date }]);
    if (path === '/api/plan/7' && request.method() === 'GET') return json(plan);
    if (path === '/api/plan/7' && request.method() === 'PATCH') {
      const body = request.postDataJSON();
      plan.title = body.title || plan.title;
      return json({ success: true });
    }
    if (path === '/api/plan/7' && request.method() === 'DELETE') return json({ success: true });
    if (path === '/api/plan/7/resources') return json([{ id: 901, title: 'Spring Boot 官方指南', url: 'https://spring.io/guides', domain: 'java', level: 'beginner', reason: '覆盖项目初始化与核心约定' }]);
    if (path === '/api/plan/day/71/status') {
      plan.days[0].status = request.postDataJSON().status;
      return json({ success: true });
    }
    if (path === '/api/plan/day/71/refine') {
      plan.days[0].tasks = ['创建项目骨架', '配置 Java 21', '运行健康检查'];
      return json(plan.days[0]);
    }
    if (path === '/api/plan/7/replan') {
      plan.days[0].date = '2026-08-26';
      return json(plan);
    }
    if (path === '/api/plan/day/71/resources') return json([{ id: 902, title: 'Spring Initializr', url: 'https://start.spring.io', domain: 'java', reason: '快速生成今日项目骨架' }]);
    if (path === '/api/plan/day/71/exercises') return json([{ question: 'Spring Boot 启动类的核心注解是什么？', answer: '@SpringBootApplication', explanation: '它组合了配置、自动配置和组件扫描。', difficulty: '基础', skillFocus: '项目结构' }]);
    if (path === '/api/plan/day/71/exercise-evaluate') return json({ score: 95, mistakeType: 'none', feedback: '回答准确。', nextRecommendation: '继续理解自动配置机制。' });
    if (path === '/api/plan/day/71/exercise-records') return json({ success: true });

    return json({ message: `Unhandled E2E route: ${request.method()} ${path}` }, 404);
  });
}

test('历史计划工作台支持细化、打卡、资源、练习和重排', async ({ page }, testInfo) => {
  await mockPlanHistory(page);
  await page.goto('/history');

  await expect(page.getByRole('heading', { name: '把计划变成今天能完成的行动' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '建立工程骨架' })).toBeVisible();
  await page.screenshot({
    path: `../output/playwright/baseline/plan-history-batch3-${testInfo.project.name}.png`,
    fullPage: true
  });

  await page.getByRole('button', { name: '细化任务' }).click();
  await expect(page.getByText('配置 Java 21')).toBeVisible();

  await page.getByRole('button', { name: '完成打卡' }).click();
  await expect(page.getByRole('button', { name: '撤销完成' })).toBeVisible();
  await page.getByRole('button', { name: '撤销完成' }).click();

  await page.getByRole('button', { name: '获取今日资源' }).click();
  await expect(page.getByRole('link', { name: 'Spring Initializr' })).toBeVisible();

  await page.getByRole('button', { name: '生成练习题' }).click();
  await page.getByPlaceholder(/写下你的答案/).fill('@SpringBootApplication');
  await page.getByRole('button', { name: '评测并保存' }).click();
  await expect(page.getByText('得分 95')).toBeVisible();
  await expect(page.getByText('回答准确。')).toBeVisible();

  await page.getByRole('button', { name: '顺延重排' }).click();
  await expect(page.getByRole('heading', { name: '顺延并重排计划', exact: true })).toBeVisible();
  await page.getByPlaceholder(/今天临时加班/).fill('临时调整学习节奏');
  await page.getByRole('button', { name: '确认顺延并重排' }).click();
  await expect(page.getByText('2026-08-26').first()).toBeVisible();
});

test('历史计划工作台支持重命名并使用二次确认保护删除', async ({ page }) => {
  await mockPlanHistory(page);
  await page.goto('/history');

  await page.getByRole('button', { name: '重命名' }).click();
  await page.getByPlaceholder(/清晰、容易识别/).fill('Java 生产实践');
  await page.getByRole('button', { name: '保存名称' }).click();
  await expect(page.getByText('Java 生产实践').first()).toBeVisible();

  await page.getByRole('button', { name: '删除' }).click();
  await expect(page.getByText('删除这份学习计划？')).toBeVisible();
  await expect(page.getByRole('button', { name: '确认删除' })).toBeVisible();
  await page.getByRole('button', { name: '保留计划' }).click();
  await expect(page.getByText('Java 生产实践').first()).toBeVisible();
});

test('历史计划工作台在窄屏无页面级横向溢出', async ({ page }) => {
  await mockPlanHistory(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/history');
  await expect(page.getByRole('heading', { name: '建立工程骨架' })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(overflow).toBe(false);
});
