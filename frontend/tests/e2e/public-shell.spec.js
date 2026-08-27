import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test('未登录用户访问学习页时进入登录页', async ({ page }) => {
  await page.goto('/history');

  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole('heading', { name: /欢迎回来|进入你的学习工作台/ }).first()).toBeVisible();
});

test('未知路由展示 404 页面', async ({ page }) => {
  await page.goto('/this-page-does-not-exist');

  await expect(page.getByRole('heading', { name: '没有找到这个页面' })).toBeVisible();
  await expect(page.getByText('404')).toBeVisible();
});

test('登录页没有自动可访问性严重问题', async ({ page }) => {
  await page.goto('/login');

  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  expect(results.violations).toEqual([]);
});
