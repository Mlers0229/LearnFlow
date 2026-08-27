<template>
  <aside class="lf-history-nav" aria-label="计划和日程导航">
    <div class="lf-history-nav__heading">
      <div>
        <span class="lf-eyebrow">学习档案</span>
        <h2>计划与日程</h2>
      </div>
      <n-tag round size="small">{{ plans.length }} 份</n-tag>
    </div>

    <n-select
      :value="selectedPlanId"
      :options="planOptions"
      filterable
      placeholder="搜索并切换计划"
      aria-label="搜索并切换计划"
      @update:value="$emit('select-plan', $event)"
    />

    <n-alert v-if="listError" type="error" :show-icon="false">{{ listError }}</n-alert>
    <n-skeleton v-else-if="listLoading" text :repeat="4" />
    <n-empty v-else-if="!plans.length" description="还没有学习计划">
      <template #extra>
        <n-button type="primary" size="small" @click="$emit('create-plan')">创建第一份计划</n-button>
      </template>
    </n-empty>

    <template v-else>
      <div class="lf-plan-strip" role="list" aria-label="最近计划">
        <button
          v-for="plan in visiblePlans"
          :key="plan.id"
          type="button"
          class="lf-plan-pill"
          :class="{ 'is-active': String(plan.id) === String(selectedPlanId) }"
          @click="$emit('select-plan', plan.id)"
        >
          <span>{{ plan.title || `学习计划 #${plan.id}` }}</span>
          <small>{{ planStatusText(plan.status) }}</small>
        </button>
        <n-button v-if="plans.length > 4" text size="tiny" @click="expanded = !expanded">
          {{ expanded ? '收起' : `查看全部 ${plans.length} 份` }}
        </n-button>
      </div>

      <div class="lf-day-index">
        <div class="lf-day-index__head">
          <span>日程索引</span>
          <small>{{ currentPlan?.days?.length || 0 }} 天</small>
        </div>
        <n-skeleton v-if="detailLoading" text :repeat="5" />
        <n-alert v-else-if="detailError" type="error" :show-icon="false">{{ detailError }}</n-alert>
        <n-empty v-else-if="!currentPlan?.days?.length" description="当前计划暂无日程" size="small" />
        <div v-else class="lf-day-list" role="list">
          <button
            v-for="(day, index) in currentPlan.days"
            :key="day.id"
            type="button"
            class="lf-day-row"
            :class="{ 'is-active': String(day.id) === String(currentDayId) }"
            @click="$emit('select-day', day.id)"
          >
            <span class="lf-day-row__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="lf-day-row__copy">
              <strong>{{ day.title || `第 ${index + 1} 天` }}</strong>
              <small>{{ day.date || '日期待定' }}</small>
            </span>
            <n-tag :type="planStatusTag(day.status)" size="tiny" round>
              {{ planStatusText(day.status) }}
            </n-tag>
          </button>
        </div>
      </div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { LearningPlan, PlanSummary } from '../composables/usePlanHistory';
import { planStatusTag, planStatusText } from '../utils/planHistory';

const props = defineProps<{
  plans: PlanSummary[];
  currentPlan: LearningPlan | null;
  currentDayId: number | string | null;
  selectedPlanId: string | null;
  planOptions: Array<{ label: string; value: string }>;
  listLoading: boolean;
  detailLoading: boolean;
  listError: string;
  detailError: string;
}>();

defineEmits<{
  (event: 'select-plan', id: number | string): void;
  (event: 'select-day', id: number | string): void;
  (event: 'create-plan'): void;
}>();

const expanded = ref(false);
const visiblePlans = computed(() => {
  if (expanded.value || props.plans.length <= 4) return props.plans;
  const base = props.plans.slice(0, 4);
  const current = props.plans.find((plan) => String(plan.id) === String(props.selectedPlanId));
  return current && !base.includes(current) ? [current, ...base.slice(0, 3)] : base;
});
</script>

<style scoped>
.lf-history-nav { display: grid; gap: 16px; min-width: 0; padding: 20px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 22px; background: var(--lf-surface, #fff); box-shadow: var(--lf-shadow-sm, 0 12px 32px rgba(18, 45, 58, .06)); }
.lf-history-nav__heading, .lf-day-index__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.lf-history-nav h2 { margin: 3px 0 0; color: var(--lf-text, #17313d); font-size: 20px; }
.lf-eyebrow { color: var(--lf-brand-700, #147a73); font-size: 11px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.lf-plan-strip { display: grid; gap: 7px; }
.lf-plan-pill { display: flex; align-items: center; justify-content: space-between; gap: 10px; width: 100%; padding: 10px 12px; color: var(--lf-text-muted, #62737b); text-align: left; border: 1px solid transparent; border-radius: 12px; background: var(--lf-surface-soft, #f5f8f7); cursor: pointer; }
.lf-plan-pill span { overflow: hidden; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.lf-plan-pill small { flex: 0 0 auto; }
.lf-plan-pill:hover, .lf-plan-pill.is-active { color: var(--lf-brand-800, #0d625d); border-color: rgba(26, 137, 127, .25); background: rgba(42, 157, 143, .09); }
.lf-day-index { display: grid; gap: 10px; min-height: 240px; padding-top: 4px; border-top: 1px solid var(--lf-border, #e3e9ec); }
.lf-day-index__head span { font-size: 13px; font-weight: 800; }.lf-day-index__head small { color: var(--lf-text-muted, #62737b); }
.lf-day-list { display: grid; gap: 6px; max-height: 520px; overflow: auto; padding-right: 3px; }
.lf-day-row { display: grid; grid-template-columns: 32px minmax(0, 1fr) auto; gap: 9px; align-items: center; width: 100%; padding: 10px; text-align: left; border: 1px solid transparent; border-radius: 13px; background: transparent; cursor: pointer; }
.lf-day-row:hover { background: var(--lf-surface-soft, #f5f8f7); }.lf-day-row.is-active { border-color: rgba(26, 137, 127, .25); background: rgba(42, 157, 143, .09); }
.lf-day-row__index { display: grid; place-items: center; width: 30px; height: 30px; color: var(--lf-brand-700, #147a73); font-size: 11px; font-weight: 800; border-radius: 10px; background: rgba(42, 157, 143, .1); }
.lf-day-row__copy { display: grid; min-width: 0; }.lf-day-row__copy strong { overflow: hidden; color: var(--lf-text, #17313d); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.lf-day-row__copy small { margin-top: 2px; color: var(--lf-text-muted, #718087); font-size: 11px; }
@media (min-width: 981px) { .lf-history-nav { position: sticky; top: 84px; } }
</style>
