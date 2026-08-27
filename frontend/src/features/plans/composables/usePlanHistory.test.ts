import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { usePlanHistory, type LearningPlan } from './usePlanHistory';

function createStorage(seed = '{}') {
  let value = seed;
  return {
    getItem: vi.fn(() => value),
    setItem: vi.fn((_key: string, next: string) => { value = next; })
  };
}

const plans = [
  { id: 1, title: 'Java 基础' },
  { id: 2, title: 'Spring Boot' }
];

describe('usePlanHistory', () => {
  it('loads the recent plan and restores the remembered day', async () => {
    const storage = createStorage(JSON.stringify({ '1': '12' }));
    const api = {
      recentPlans: vi.fn().mockResolvedValue(plans),
      getPlan: vi.fn().mockResolvedValue({ id: 1, days: [{ id: 11 }, { id: 12 }] })
    };
    const history = usePlanHistory({ currentUser: ref({ id: 7 }), api, storage });

    await history.loadRecentPlans();

    expect(history.currentDay.value?.id).toBe(12);
    expect(history.completionRate.value).toBe(0);
    expect(storage.getItem).toHaveBeenCalledWith('learnflow:plan-history:last-day:7');
  });

  it('persists day selection and updates titles in both views', async () => {
    const storage = createStorage();
    const api = {
      recentPlans: vi.fn().mockResolvedValue(plans.map((plan) => ({ ...plan }))),
      getPlan: vi.fn().mockResolvedValue({
        id: 1,
        title: 'Java 基础',
        days: [{ id: 11, status: 'completed' }, { id: 12, status: 'in_progress' }]
      })
    };
    const history = usePlanHistory({ currentUser: ref({ id: 7 }), api, storage });
    await history.loadRecentPlans();

    history.selectDay(12);
    history.updateCurrentTitle('Java 工程化');

    expect(history.currentDay.value?.id).toBe(12);
    expect(history.completionRate.value).toBe(50);
    expect(history.plans.value[0].title).toBe('Java 工程化');
    expect(storage.setItem).toHaveBeenLastCalledWith(
      'learnflow:plan-history:last-day:7',
      JSON.stringify({ '1': '12' })
    );
  });

  it('ignores a slower stale detail response', async () => {
    let resolveFirst!: (value: { id: number; title: string; days: Array<{ id: number }> }) => void;
    const api = {
      recentPlans: vi.fn().mockResolvedValue(plans),
      getPlan: vi.fn((id: number | string): Promise<LearningPlan> => id === 1
        ? new Promise<LearningPlan>((resolve) => { resolveFirst = resolve; })
        : Promise.resolve({ id: 2, title: 'Spring Boot', days: [{ id: 21 }] }))
    };
    const history = usePlanHistory({ currentUser: ref(null), api, storage: createStorage() });

    const first = history.loadPlanDetail(1);
    await history.loadPlanDetail(2);
    resolveFirst({ id: 1, title: '过期响应', days: [{ id: 11 }] });
    await first;

    expect(history.currentPlan.value?.id).toBe(2);
    expect(history.detailLoading.value).toBe(false);
  });

  it('restores the most recently viewed plan', async () => {
    const storage = {
      getItem: vi.fn((key: string) => key.includes(':last-plan:') ? '2' : '{}'),
      setItem: vi.fn()
    };
    const api = {
      recentPlans: vi.fn().mockResolvedValue(plans),
      getPlan: vi.fn((id: number | string) => Promise.resolve({
        id,
        title: String(id) === '2' ? 'Spring Boot' : 'Java 基础',
        days: [{ id: String(id) === '2' ? 21 : 11 }]
      }))
    };
    const history = usePlanHistory({ currentUser: ref({ id: 7 }), api, storage });

    await history.loadRecentPlans();

    expect(history.currentPlan.value?.id).toBe(2);
    expect(api.getPlan).toHaveBeenCalledWith(2);
});
});
