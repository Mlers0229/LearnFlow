import { computed, ref } from 'vue';
import { getPlanById, getRecentPlans, getResourcesByDay } from '../../../api/plan';
import type { ContextDay, ContextPlan, ContextResource } from '../types';
import type { LearningContext } from './useAiChat';

type ContextApi = {
  recentPlans: (limit: number) => Promise<ContextPlan[]>;
  getPlan: (id: number | string) => Promise<ContextPlan>;
  getResources: (dayId: number | string) => Promise<ContextResource[] | { resources?: ContextResource[] }>;
};

type Options = { api?: ContextApi };

const defaultApi: ContextApi = {
  recentPlans: (limit) => getRecentPlans(limit),
  getPlan: (id) => getPlanById(id),
  getResources: (dayId) => getResourcesByDay(dayId)
};

function planId(plan?: ContextPlan | null) {
  const candidate = plan as (ContextPlan & { planId?: number | string; plan_id?: number | string }) | null;
  return candidate?.planId ?? candidate?.plan_id ?? candidate?.id ?? null;
}

export function useLearningContext(options: Options = {}) {
  const api = options.api ?? defaultApi;
  const enabled = ref(true);
  const plans = ref<ContextPlan[]>([]);
  const currentPlan = ref<ContextPlan | null>(null);
  const currentDayId = ref<number | string | null>(null);
  const resources = ref<ContextResource[]>([]);
  const loading = ref(false);
  const resourcesLoading = ref(false);
  const error = ref('');
  const resourcesError = ref('');
  let planRequest = 0;
  let resourceRequest = 0;

  const currentDay = computed<ContextDay | null>(() =>
    currentPlan.value?.days?.find((day) => String(day.id) === String(currentDayId.value)) ?? null
  );
  const planOptions = computed(() => plans.value.map((plan) => ({
    label: plan.title || `学习计划 #${planId(plan)}`,
    value: String(planId(plan))
  })));
  const dayOptions = computed(() => (currentPlan.value?.days ?? []).map((day) => ({
    label: `${day.date || '未排期'} · ${day.title || '学习任务'}`,
    value: String(day.id)
  })));
  const learningContext = computed<LearningContext | null>(() => {
    if (!enabled.value || !currentPlan.value) return null;
    return {
      planTitle: currentPlan.value.title,
      dayTitle: currentDay.value?.title,
      date: currentDay.value?.date,
      tasks: currentDay.value?.tasks ?? [],
      resources: resources.value.map((item) => ({ title: item.title, url: item.url, reason: item.reason }))
    };
  });
  const contextLabel = computed(() => {
    if (!enabled.value) return '未附加学习上下文';
    if (currentDay.value) return currentDay.value.title || currentDay.value.date || '当前学习日';
    if (currentPlan.value) return currentPlan.value.title || '当前学习计划';
    return '暂无可用学习计划';
  });

  async function loadResources(dayId: number | string | null) {
    const request = ++resourceRequest;
    resources.value = [];
    resourcesError.value = '';
    if (dayId == null) return;
    resourcesLoading.value = true;
    try {
      const payload = await api.getResources(dayId);
      if (request !== resourceRequest) return;
      resources.value = Array.isArray(payload) ? payload : payload?.resources ?? [];
    } catch (cause) {
      console.error(cause);
      if (request === resourceRequest) resourcesError.value = '推荐资源暂时无法加载。';
    } finally {
      if (request === resourceRequest) resourcesLoading.value = false;
    }
  }

  async function selectDay(dayId: number | string | null) {
    currentDayId.value = dayId;
    await loadResources(dayId);
  }

  async function selectPlan(id: number | string | null) {
    const request = ++planRequest;
    currentPlan.value = null;
    currentDayId.value = null;
    resources.value = [];
    error.value = '';
    if (id == null) return;
    loading.value = true;
    try {
      const plan = await api.getPlan(id);
      if (request !== planRequest) return;
      currentPlan.value = plan;
      const firstDay = plan.days?.[0]?.id ?? null;
      await selectDay(firstDay);
    } catch (cause) {
      console.error(cause);
      if (request === planRequest) error.value = '学习上下文加载失败，请稍后重试。';
    } finally {
      if (request === planRequest) loading.value = false;
    }
  }

  async function load() {
    loading.value = true;
    error.value = '';
    try {
      plans.value = await api.recentPlans(8);
      if (plans.value.length) await selectPlan(planId(plans.value[0]));
    } catch (cause) {
      console.error(cause);
      error.value = '近期学习计划暂时无法加载。';
    } finally {
      loading.value = false;
    }
  }

  return {
    enabled,
    plans,
    currentPlan,
    currentDayId,
    currentDay,
    resources,
    loading,
    resourcesLoading,
    error,
    resourcesError,
    planOptions,
    dayOptions,
    learningContext,
    contextLabel,
    load,
    selectPlan,
    selectDay,
    loadResources
  };
}
