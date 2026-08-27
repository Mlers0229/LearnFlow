import { describe, expect, it, vi } from 'vitest';
import { useLearningContext } from './useLearningContext';

function createApi(overrides = {}) {
  return {
    recentPlans: vi.fn().mockResolvedValue([{ id: 7, title: 'Spring Boot 进阶' }]),
    getPlan: vi.fn().mockResolvedValue({
      id: 7,
      title: 'Spring Boot 进阶',
      days: [{ id: 71, date: '2026-08-26', title: '理解自动配置', tasks: ['阅读条件注解', '完成练习'] }]
    }),
    getResources: vi.fn().mockResolvedValue([{ id: 5, title: 'Spring 指南', url: 'https://spring.io/guides' }]),
    ...overrides
  };
}

describe('useLearningContext', () => {
  it('loads a recent plan, its first day and recommended resources', async () => {
    const api = createApi();
    const context = useLearningContext({ api });

    await context.load();

    expect(context.currentDay.value?.id).toBe(71);
    expect(context.resources.value).toHaveLength(1);
    expect(context.learningContext.value).toMatchObject({ planTitle: 'Spring Boot 进阶', dayTitle: '理解自动配置' });
    expect(api.getResources).toHaveBeenCalledWith(71);
  });

  it('does not attach plan data when the user disables context', async () => {
    const context = useLearningContext({ api: createApi() });
    await context.load();
    context.enabled.value = false;

    expect(context.learningContext.value).toBeNull();
    expect(context.contextLabel.value).toBe('未附加学习上下文');
  });
});
