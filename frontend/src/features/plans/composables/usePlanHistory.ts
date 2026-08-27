import { computed, ref, type Ref } from 'vue';
import { getPlanById, getRecentPlans } from '../../../api/plan';

export type PlanDay = {
  id: number | string;
  date?: string;
  title?: string;
  status?: string;
  tasks?: string[];
};

export type PlanSummary = {
  id: number | string;
  title?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
};

export type LearningPlan = PlanSummary & {
  planId?: number | string;
  plan_id?: number | string;
  days?: PlanDay[];
  adaptation?: unknown;
};

type HistoryApi = {
  recentPlans: (limit: number) => Promise<PlanSummary[]>;
  getPlan: (id: number | string) => Promise<LearningPlan>;
};

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>;

type Options = {
  currentUser: Ref<unknown>;
  api?: HistoryApi;
  storage?: StorageLike | null;
};

const defaultApi: HistoryApi = {
  recentPlans: (limit) => getRecentPlans(limit),
  getPlan: (id) => getPlanById(id)
};

export function getPlanIdentity(plan?: LearningPlan | null) {
  if (!plan) return '';
  return String(plan.planId || plan.plan_id || plan.id || '');
}

export function usePlanHistory(options: Options) {
  const api = options.api ?? defaultApi;
  const storage = options.storage === undefined
    ? (typeof window === 'undefined' ? null : window.sessionStorage)
    : options.storage;

  const plans = ref<PlanSummary[]>([]);
  const currentPlan = ref<LearningPlan | null>(null);
  const currentDayId = ref<number | string | null>(null);
  const listLoading = ref(false);
  const detailLoading = ref(false);
  const listError = ref('');
  const detailError = ref('');
  const rememberedDayMap = ref<Record<string, string>>({});
  const rememberedPlanId = ref<string | null>(null);
  let detailRequest = 0;

  const selectedPlanId = computed(() => getPlanIdentity(currentPlan.value) || null);
  const currentDay = computed(() => {
    const days = currentPlan.value?.days ?? [];
    return days.find((day) => String(day.id) === String(currentDayId.value)) ?? null;
  });
  const totalDays = computed(() => currentPlan.value?.days?.length ?? 0);
  const completedDays = computed(() =>
    (currentPlan.value?.days ?? []).filter((day) => day.status === 'completed').length
  );
  const completionRate = computed(() =>
    totalDays.value ? Math.round((completedDays.value / totalDays.value) * 100) : 0
  );
  const planSelectOptions = computed(() => plans.value.map((plan) => ({
    label: plan.title || `学习计划 #${plan.id}`,
    value: String(plan.id)
  })));

  function dayStorageKey() {
    const user = options.currentUser.value;
    const userId = user && typeof user === 'object' && 'id' in user
      ? (user as { id?: number | string }).id
      : undefined;
    return `learnflow:plan-history:last-day:${userId ?? 'guest'}`;
  }

  function planStorageKey() {
    return dayStorageKey().replace(':last-day:', ':last-plan:');
  }

  function hydrateMemory() {
    if (!storage) return;
    try {
      rememberedDayMap.value = JSON.parse(storage.getItem(dayStorageKey()) || '{}');
      rememberedPlanId.value = storage.getItem(planStorageKey());
    } catch {
      rememberedDayMap.value = {};
      rememberedPlanId.value = null;
    }
  }

  function persistMemory() {
    storage?.setItem(dayStorageKey(), JSON.stringify(rememberedDayMap.value));
  }

  function resolveInitialDayId(plan: LearningPlan) {
    const days = plan.days ?? [];
    if (!days.length) return null;
    const remembered = rememberedDayMap.value[getPlanIdentity(plan)];
    return days.find((day) => String(day.id) === remembered)?.id ?? days[0].id;
  }

  async function loadPlanDetail(id: number | string) {
    const request = ++detailRequest;
    detailError.value = '';
    detailLoading.value = true;
    try {
      const data = await api.getPlan(id);
      if (request !== detailRequest) return null;
      currentPlan.value = data;
      currentDayId.value = resolveInitialDayId(data);
      rememberedPlanId.value = getPlanIdentity(data);
      storage?.setItem(planStorageKey(), rememberedPlanId.value);
      return data;
    } catch (error) {
      console.error(error);
      if (request === detailRequest) detailError.value = '加载计划详情失败，请稍后重试。';
      return null;
    } finally {
      if (request === detailRequest) detailLoading.value = false;
    }
  }

  async function loadRecentPlans(preferredPlanId?: number | string | null) {
    listError.value = '';
    listLoading.value = true;
    try {
      plans.value = await api.recentPlans(50);
      if (!plans.value.length) {
        currentPlan.value = null;
        currentDayId.value = null;
        return;
      }
      const targetId = preferredPlanId ?? rememberedPlanId.value;
      const target = plans.value.find((plan) => String(plan.id) === String(targetId))
        ?? plans.value[0];
      await loadPlanDetail(target.id);
    } catch (error) {
      console.error(error);
      listError.value = '加载历史计划失败，请确认后端服务已启动后再重试。';
    } finally {
      listLoading.value = false;
    }
  }

  function selectDay(dayId: number | string) {
    currentDayId.value = dayId;
    const planId = getPlanIdentity(currentPlan.value);
    if (!planId) return;
    rememberedDayMap.value = { ...rememberedDayMap.value, [planId]: String(dayId) };
    persistMemory();
  }

  function replaceCurrentPlan(plan: LearningPlan, preferredDayId?: number | string | null) {
    currentPlan.value = plan;
    const validPreferred = plan.days?.find((day) => String(day.id) === String(preferredDayId));
    currentDayId.value = validPreferred?.id ?? resolveInitialDayId(plan);
    if (currentDayId.value != null) selectDay(currentDayId.value);
  }

  function updateCurrentTitle(title: string) {
    if (!currentPlan.value) return;
    currentPlan.value.title = title;
    const id = getPlanIdentity(currentPlan.value);
    const item = plans.value.find((plan) => String(plan.id) === id);
    if (item) item.title = title;
  }

  function forgetPlan(planId: number | string) {
    const next = { ...rememberedDayMap.value };
    delete next[String(planId)];
    rememberedDayMap.value = next;
    persistMemory();
  }

  hydrateMemory();

  return {
    plans,
    currentPlan,
    currentDayId,
    currentDay,
    listLoading,
    detailLoading,
    listError,
    detailError,
    selectedPlanId,
    planSelectOptions,
    totalDays,
    completedDays,
    completionRate,
    loadRecentPlans,
    loadPlanDetail,
    selectDay,
    replaceCurrentPlan,
    updateCurrentTitle,
    forgetPlan
  };
}
