import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { useExerciseReview } from './useExerciseReview';

const records = [
  { id: 1, planId: 7, dayId: 71, dayDate: '2026-08-25', dayTitle: '基础', skillFocus: '依赖注入', aiScore: 92 },
  { id: 2, planId: 7, dayId: 72, dayDate: '2026-08-26', dayTitle: '进阶', skillFocus: '自动配置', aiScore: 45, aiMistakeType: 'concept_gap' },
  { id: 3, planId: 7, dayId: 72, dayDate: '2026-08-26', dayTitle: '进阶', skillFocus: '自动配置', aiScore: null }
];

function createApi(overrides = {}) {
  return {
    recentPlans: vi.fn().mockResolvedValue([{ id: 7, title: 'Spring Boot' }]),
    records: vi.fn().mockResolvedValue({ items: records.map((record) => ({ ...record })) }),
    mastery: vi.fn().mockResolvedValue([]),
    recomputeMastery: vi.fn().mockResolvedValue([]),
    markReviewed: vi.fn().mockResolvedValue(undefined),
    deleteRecord: vi.fn().mockResolvedValue(undefined),
    deleteDay: vi.fn().mockResolvedValue({}),
    ...overrides
  };
}

describe('useExerciseReview', () => {
  it('filters by day, review status and knowledge point while recomputing summary', async () => {
    const review = useExerciseReview({ currentUser: ref({ id: 1 }), api: createApi() });
    await review.loadRecords();

    review.selectedDayId.value = 72;
    review.reviewStatus.value = 'needs_review';
    review.skillQuery.value = '自动';

    expect(review.visibleRecords.value.map((record) => record.id)).toEqual([2]);
    expect(review.summary.value).toMatchObject({ totalRecords: 1, averageScore: 45, needsReviewCount: 1 });
    expect(review.weakSkills.value[0]).toMatchObject({ name: '自动配置', attempts: 2, needsReview: 1 });
  });

  it('updates local records only after destructive API actions succeed', async () => {
    const api = createApi();
    const review = useExerciseReview({ currentUser: ref({ id: 1 }), api });
    await review.loadRecords();

    expect(await review.removeRecord(review.records.value[0])).toBe(true);
    expect(review.records.value.map((record) => record.id)).toEqual([2, 3]);
    expect(api.deleteRecord).toHaveBeenCalledWith(1);
  });

  it('ignores stale record responses after a faster plan switch', async () => {
    let resolveFirst!: (value: { items: typeof records }) => void;
    const api = createApi({
      records: vi.fn()
        .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }))
        .mockResolvedValueOnce({ items: [{ ...records[1], id: 20 }] })
    });
    const review = useExerciseReview({ currentUser: ref({ id: 1 }), api });

    const first = review.loadRecords();
    await review.changePlan(7);
    resolveFirst({ items: records });
    await first;

    expect(review.records.value.map((record) => record.id)).toEqual([20]);
    expect(review.loading.value).toBe(false);
  });
});
