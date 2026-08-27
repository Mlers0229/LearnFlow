import { expect, test } from '@playwright/test';

async function mockSession(page, role = 'user') {
  await page.route('**/api/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'e2e-access-token',
        username: 'Mlers',
        role
      })
    });
  });
}

test('认证页面使用独立外壳并可以往返登录与注册', async ({ page }) => {
  await page.goto('/login');

  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  await expect(page.getByRole('complementary', { name: '学习工作台导航' })).toHaveCount(0);

  await page.getByRole('link', { name: '免费注册' }).click();
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.getByRole('heading', { name: '创建学习空间' })).toBeVisible();

  await page.getByRole('link', { name: '返回登录' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('注册流程在本地校验账号并进入偏好步骤', async ({ page }) => {
  await page.goto('/register');

  await page.getByPlaceholder('至少 3 个字符').fill('mlers');
  await page.getByPlaceholder('用于找回密码').fill('mlers@example.com');
  await page.getByPlaceholder('至少 12 位').fill('LearnFlow2026!');
  await page.getByPlaceholder('请再次输入密码').fill('LearnFlow2026!');
  await page.getByRole('button', { name: '下一步：学习偏好' }).click();

  await expect(page.getByRole('button', { name: '返回修改' })).toBeVisible();
  await expect(page.getByText('偏好可以随时调整')).toBeVisible();
});

test('密码重置在请求发出前校验必填字段', async ({ page }) => {
  await page.goto('/reset-password');
  await page.getByRole('button', { name: '发送重置邮件' }).click();

  await expect(page.getByRole('alert')).toContainText('请填写用户名和注册邮箱');
});

test('登录态工作台在桌面和移动端均可访问导航', async ({ page, viewport }) => {
  await mockSession(page, 'admin');
  await page.goto('/about');

  await expect(page.getByRole('heading', { name: '关于 LearnFlow' }).first()).toBeVisible();

  if ((viewport?.width || 1280) < 768) {
    await page.getByRole('button', { name: '打开导航' }).click();
    await expect(page.getByRole('link', { name: '生成学习计划' })).toBeVisible();
    await expect(page.getByRole('link', { name: /进入管理控制台/ })).toBeVisible();
  } else {
    await expect(page.getByRole('complementary', { name: '学习工作台导航' })).toBeVisible();
    await expect(page.getByRole('link', { name: /进入管理控制台/ })).toBeVisible();
  }
});

test('管理控制台在桌面和移动端均使用独立导航', async ({ page, viewport }) => {
  await mockSession(page, 'admin');
  await page.goto('/admin/models');

  await expect(page.getByRole('heading', { name: '模型配置' }).first()).toBeVisible();

  if ((viewport?.width || 1280) < 1024) {
    await page.getByRole('button', { name: '打开管理导航' }).click();
    await expect(page.getByRole('navigation', { name: '管理端主导航' })).toBeVisible();
    await expect(page.getByRole('link', { name: '返回学习工作台' })).toBeVisible();
  } else {
    await expect(page.getByRole('complementary', { name: '管理控制台导航' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: '管理端主导航' })).toBeVisible();
  }
});

test('用户工作台在 360px 与 768px 无页面级横向溢出', async ({ page }) => {
  await mockSession(page, 'user');

  for (const width of [360, 768]) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/about');
    await expect(page.getByRole('heading', { name: '关于 LearnFlow' }).first()).toBeVisible();

    const hasPageOverflow = await page.evaluate(() =>
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    );
    expect(hasPageOverflow, `${width}px 不应出现页面级横向滚动`).toBe(false);

    await page.getByRole('button', { name: '打开导航' }).click();
    await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible();
    await page.getByRole('link', { name: '关于 LearnFlow' }).click();
  }
});

test('未登录访问管理控制台时保留原始目标并返回登录页', async ({ page }) => {
  await page.goto('/admin/models');

  await expect(page).toHaveURL(/\/login\?redirect=\/admin\/models/);
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
});
