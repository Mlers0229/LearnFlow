<template>
  <section class="lf-review-filters" aria-labelledby="review-filter-title">
    <div class="lf-review-filters__head">
      <div><span>Review lens</span><h2 id="review-filter-title">定位需要复盘的内容</h2></div>
      <n-button v-if="activeCount" text size="small" @click="$emit('clear')">清除 {{ activeCount }} 项筛选</n-button>
    </div>
    <div class="lf-review-filters__grid">
      <n-form-item label="学习计划" :show-feedback="false">
        <n-select
          :value="planId"
          :options="planOptions"
          clearable
          filterable
          placeholder="全部计划"
          @update:value="$emit('update:plan-id', $event)"
        />
      </n-form-item>
      <n-form-item label="学习日期" :show-feedback="false">
        <n-select
          :value="dayId"
          :options="dayOptions"
          clearable
          filterable
          placeholder="全部日期"
          @update:value="$emit('update:day-id', $event)"
        />
      </n-form-item>
      <n-form-item label="复习状态" :show-feedback="false">
        <n-select :value="status" :options="statusOptions" @update:value="$emit('update:status', $event)" />
      </n-form-item>
      <n-form-item label="知识点" :show-feedback="false">
        <n-select
          :value="skillQuery || null"
          :options="skillOptions"
          clearable
          filterable
          tag
          placeholder="全部知识点"
          @update:value="$emit('update:skill-query', $event || '')"
        />
      </n-form-item>
      <n-form-item label="加载范围" :show-feedback="false">
        <n-select :value="limit" :options="limitOptions" @update:value="$emit('update:limit', $event)" />
      </n-form-item>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ReviewStatus } from '../types';

defineProps<{
  planId: number | string | null;
  dayId: number | string | null;
  status: ReviewStatus;
  skillQuery: string;
  limit: number;
  planOptions: Array<{ label: string; value: number | string }>;
  dayOptions: Array<{ label: string; value: number | string }>;
  skillOptions: Array<{ label: string; value: string }>;
  activeCount: number;
}>();

defineEmits<{
  (event: 'update:plan-id', value: number | string | null): void;
  (event: 'update:day-id', value: number | string | null): void;
  (event: 'update:status', value: ReviewStatus): void;
  (event: 'update:skill-query', value: string): void;
  (event: 'update:limit', value: number): void;
  (event: 'clear'): void;
}>();

const statusOptions = [
  { label: '全部状态', value: 'all' },
  { label: '需要复习', value: 'needs_review' },
  { label: '掌握较稳', value: 'mastered' },
  { label: '尚未评分', value: 'unscored' }
];
const limitOptions = [20, 50, 100].map((value) => ({ label: `最近 ${value} 条`, value }));
</script>

<style scoped>
.lf-review-filters { display: grid; gap: 15px; padding: 18px 20px 20px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 20px; background: var(--lf-surface, #fff); box-shadow: var(--lf-shadow-sm, 0 12px 30px rgba(18,45,58,.05)); }
.lf-review-filters__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.lf-review-filters__head span { color: var(--lf-brand-700, #147a73); font-size: 10px; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }.lf-review-filters__head h2 { margin: 3px 0 0; color: var(--lf-text, #17313d); font-size: 18px; }
.lf-review-filters__grid { display: grid; grid-template-columns: 1.2fr 1.2fr 1fr 1fr .85fr; gap: 11px; }.lf-review-filters :deep(.n-form-item-label) { color: var(--lf-text-muted, #62737b); font-size: 11px; font-weight: 700; }
@media (max-width: 1100px) { .lf-review-filters__grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 720px) { .lf-review-filters__grid { grid-template-columns: 1fr; }.lf-review-filters__head { align-items: flex-start; flex-direction: column; } }
</style>
