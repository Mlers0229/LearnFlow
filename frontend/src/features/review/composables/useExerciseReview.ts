import { computed, ref, type Ref } from 'vue';
import {
  deleteExerciseRecord,
  deleteExerciseRecordsByDay,
  getExerciseRecords,
  getMasteryProfiles,
  getRecentPlans,
  markExerciseReviewed,
  recomputeMasteryProfiles
} from '../../../api/plan';
import { isNeedsReview } from '../../../utils/exercise';
import type {
  ExerciseRecord,
  MasteryProfile,
  ReviewGroup,
  ReviewStatus,
  WeakSkill
} from '../types';

type PlanOption = { id: number | string; title?: string };
type ReviewApi = {
  recentPlans: (limit: number) => Promise<PlanOption[]>;
  records: (params: { userId: number; planId?: number | string; limit?: number }) => Promise<{ items?: ExerciseRecord[] }>;
  mastery: (limit: number) => Promise<MasteryProfile[]>;
  recomputeMastery: (limit: number) => Promise<MasteryProfile[]>;
  markReviewed: (id: number | string) => Promise<void>;
  deleteRecord: (id: number | string) => Promise<void>;
  deleteDay: (id: number | string) => Promise<unknown>;
};

type Options = { currentUser: Ref<unknown>; api?: ReviewApi };

const defaultApi: ReviewApi = {
  recentPlans: (limit) => getRecentPlans(limit),
  records: (params) => getExerciseRecords(params),
  mastery: (limit) => getMasteryProfiles(limit),
  recomputeMastery: (limit) => recomputeMasteryProfiles(limit),
  markReviewed: (id) => markExerciseReviewed(id),
  deleteRecord: (id) => deleteExerciseRecord(id),
  deleteDay: (id) => deleteExerciseRecordsByDay(id)
};

function userIdFrom(value: unknown) {
  if (!value || typeof value !== 'object' || !('id' in value)) return null;
  const id = Number((value as { id?: number | string }).id);
  return Number.isFinite(id) ? id : null;
}

function scoreAverage(records: ExerciseRecord[]) {
  const scores = records.flatMap((record) => typeof record.aiScore === 'number' ? [record.aiScore] : []);
  return scores.length
    ? Math.round((scores.reduce((sum, score) => sum + score, 0) / scores.length) * 10) / 10
    : null;
}

export function useExerciseReview(options: Options) {
  const api = options.api ?? defaultApi;
  const records = ref<ExerciseRecord[]>([]);
  const plans = ref<PlanOption[]>([]);
  const masteryProfiles = ref<MasteryProfile[]>([]);
  const selectedPlanId = ref<number | string | null>(null);
  const selectedDayId = ref<number | string | null>(null);
  const reviewStatus = ref<ReviewStatus>('all');
  const skillQuery = ref('');
  const limit = ref(50);
  const loading = ref(false);
  const masteryLoading = ref(false);
  const masteryRecomputing = ref(false);
  const error = ref('');
  const masteryError = ref('');
  const deletingRecordIds = ref<Record<string, boolean>>({});
  const deletingDayIds = ref<Record<string, boolean>>({});
  const reviewingRecordIds = ref<Record<string, boolean>>({});
  const reviewedRecordIds = ref<Record<string, boolean>>({});
  let recordRequest = 0;

  const planOptions = computed(() => plans.value.map((plan) => ({
    label: plan.title || `学习计划 #${plan.id}`,
    value: plan.id
  })));
  const dayOptions = computed(() => {
    const unique = new Map<string, { label: string; value: number | string }>();
    records.value.forEach((record) => {
      if (record.dayId == null) return;
      unique.set(String(record.dayId), {
        label: `${record.dayDate || '日期待定'} · ${record.dayTitle || '未命名学习日'}`,
        value: record.dayId
      });
    });
    return [...unique.values()];
  });
  const skillOptions = computed(() => [...new Set(records.value
    .map((record) => record.skillFocus?.trim())
    .filter((skill): skill is string => Boolean(skill)))]
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    .map((skill) => ({ label: skill, value: skill })));

  const visibleRecords = computed(() => {
    const query = skillQuery.value.trim().toLowerCase();
    return records.value.filter((record) => {
      if (selectedDayId.value != null && String(record.dayId) !== String(selectedDayId.value)) return false;
      if (query && !String(record.skillFocus || '').toLowerCase().includes(query)) return false;
      if (reviewStatus.value === 'needs_review' && !isNeedsReview(record)) return false;
      if (reviewStatus.value === 'mastered' && !(record.aiScore != null && record.aiScore >= 85)) return false;
      if (reviewStatus.value === 'unscored' && record.aiScore != null) return false;
      return true;
    });
  });

  const summary = computed(() => {
    const items = visibleRecords.value;
    const scores = items.flatMap((record) => typeof record.aiScore === 'number' ? [record.aiScore] : []);
    return {
      totalRecords: items.length,
      scoredRecords: scores.length,
      averageScore: scoreAverage(items),
      highestScore: scores.length ? Math.max(...scores) : null,
      latestScore: scores.length ? scores[0] : null,
      masteredCount: items.filter((record) => record.aiScore != null && record.aiScore >= 85).length,
      needsReviewCount: items.filter((record) => isNeedsReview(record)).length
    };
  });

  const groupedRecords = computed<ReviewGroup[]>(() => {
    const groups = new Map<string, ReviewGroup>();
    visibleRecords.value.forEach((record) => {
      const key = `${record.planId || 'plan'}-${record.dayId || 'day'}`;
      const group = groups.get(key) ?? {
        key,
        planId: record.planId,
        dayId: record.dayId,
        planTitle: record.planTitle,
        dayTitle: record.dayTitle,
        dayDate: record.dayDate,
        records: []
      };
      group.records.push(record);
      groups.set(key, group);
    });
    return [...groups.values()];
  });

  const weakSkills = computed<WeakSkill[]>(() => {
    const groups = new Map<string, ExerciseRecord[]>();
    records.value.forEach((record) => {
      const name = record.skillFocus?.trim() || '未分类知识点';
      groups.set(name, [...(groups.get(name) ?? []), record]);
    });
    return [...groups.entries()].map(([name, items]) => ({
      name,
      attempts: items.length,
      needsReview: items.filter((record) => isNeedsReview(record)).length,
      averageScore: scoreAverage(items)
    })).sort((a, b) => b.needsReview - a.needsReview || (a.averageScore ?? 101) - (b.averageScore ?? 101)).slice(0, 4);
  });

  const activeFilterCount = computed(() => [
    selectedPlanId.value != null,
    selectedDayId.value != null,
    reviewStatus.value !== 'all',
    Boolean(skillQuery.value.trim())
  ].filter(Boolean).length);

  async function loadPlans() {
    if (userIdFrom(options.currentUser.value) == null) return;
    try { plans.value = await api.recentPlans(50); }
    catch (loadError) { console.error('load review plans failed', loadError); }
  }

  async function loadRecords() {
    const userId = userIdFrom(options.currentUser.value);
    if (userId == null) {
      error.value = '当前登录状态已失效，请重新登录后再试。';
      records.value = [];
      return;
    }
    const request = ++recordRequest;
    loading.value = true;
    error.value = '';
    try {
      const response = await api.records({ userId, planId: selectedPlanId.value ?? undefined, limit: limit.value });
      if (request === recordRequest) records.value = response?.items ?? [];
    } catch (loadError) {
      console.error(loadError);
      if (request === recordRequest) {
        error.value = '加载练习记录失败，请稍后重试。';
        records.value = [];
      }
    } finally {
      if (request === recordRequest) loading.value = false;
    }
  }

  async function loadMastery() {
    masteryLoading.value = true;
    masteryError.value = '';
    try { masteryProfiles.value = await api.mastery(20); }
    catch (loadError) {
      console.error(loadError);
      masteryProfiles.value = [];
      masteryError.value = '掌握度画像加载失败，请稍后重试。';
    } finally { masteryLoading.value = false; }
  }

  async function recomputeMastery() {
    masteryRecomputing.value = true;
    masteryError.value = '';
    try {
      masteryProfiles.value = await api.recomputeMastery(20);
      return true;
    } catch (recomputeError) {
      console.error(recomputeError);
      masteryError.value = '重算掌握度失败，请稍后重试。';
      return false;
    } finally { masteryRecomputing.value = false; }
  }

  async function markReviewed(record: ExerciseRecord) {
    const id = record.id;
    reviewingRecordIds.value = { ...reviewingRecordIds.value, [id]: true };
    try {
      await api.markReviewed(id);
      reviewedRecordIds.value = { ...reviewedRecordIds.value, [id]: true };
      await loadMastery();
      return true;
    } catch (reviewError) {
      console.error(reviewError);
      return false;
    } finally { reviewingRecordIds.value = { ...reviewingRecordIds.value, [id]: false }; }
  }

  async function removeRecord(record: ExerciseRecord) {
    deletingRecordIds.value = { ...deletingRecordIds.value, [record.id]: true };
    try {
      await api.deleteRecord(record.id);
      records.value = records.value.filter((item) => String(item.id) !== String(record.id));
      await loadMastery();
      return true;
    } catch (deleteError) {
      console.error(deleteError);
      return false;
    } finally { deletingRecordIds.value = { ...deletingRecordIds.value, [record.id]: false }; }
  }

  async function removeDay(group: ReviewGroup) {
    if (group.dayId == null) return false;
    deletingDayIds.value = { ...deletingDayIds.value, [group.dayId]: true };
    try {
      await api.deleteDay(group.dayId);
      records.value = records.value.filter((item) => String(item.dayId) !== String(group.dayId));
      await loadMastery();
      return true;
    } catch (deleteError) {
      console.error(deleteError);
      return false;
    } finally { deletingDayIds.value = { ...deletingDayIds.value, [group.dayId]: false }; }
  }

  async function changePlan(id: number | string | null) {
    selectedPlanId.value = id;
    selectedDayId.value = null;
    await loadRecords();
  }

  function clearFilters() {
    selectedDayId.value = null;
    reviewStatus.value = 'all';
    skillQuery.value = '';
  }

  return {
    records, plans, masteryProfiles, selectedPlanId, selectedDayId, reviewStatus, skillQuery, limit,
    loading, masteryLoading, masteryRecomputing, error, masteryError, deletingRecordIds,
    deletingDayIds, reviewingRecordIds, reviewedRecordIds, planOptions, dayOptions, skillOptions,
    visibleRecords, summary, groupedRecords, weakSkills, activeFilterCount, loadPlans, loadRecords,
    loadMastery, recomputeMastery, markReviewed, removeRecord, removeDay, changePlan, clearFilters
  };
}
