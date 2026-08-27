<template>
  <main class="lf-review-page">
    <header class="lf-review-hero">
      <div>
        <span class="lf-review-hero__eyebrow">Practice intelligence</span>
        <h1>从每一次作答，看见真正的进步</h1>
        <p>把评分、错误模式、知识点和掌握度放在同一条复盘路径上，明确下一道题应该练什么。</p>
      </div>
      <div class="lf-review-hero__focus">
        <span>当前回顾焦点</span>
        <strong>{{ focusTitle }}</strong>
        <p>{{ loading ? '正在同步练习与评测记录…' : `当前 ${summary.totalRecords} 条记录，${summary.needsReviewCount} 条建议优先复习。` }}</p>
        <n-button secondary :loading="loading" @click="loadRecords"><RefreshCw :size="15" />刷新记录</n-button>
      </div>
    </header>

    <section class="lf-review-metrics" aria-label="练习表现概览">
      <article><ListChecks :size="18" /><div><span>练习记录</span><strong>{{ summary.totalRecords }}</strong><small>{{ summary.scoredRecords }} 条已评分</small></div></article>
      <article><Gauge :size="18" /><div><span>平均得分</span><strong>{{ summary.averageScore ?? '--' }}</strong><small>最高 {{ summary.highestScore ?? '--' }}</small></div></article>
      <article><BadgeCheck :size="18" /><div><span>掌握较稳</span><strong>{{ summary.masteredCount }}</strong><small>85 分及以上</small></div></article>
      <article class="is-warning"><TriangleAlert :size="18" /><div><span>待重点复习</span><strong>{{ summary.needsReviewCount }}</strong><small>低分或概念缺口</small></div></article>
    </section>

    <ExerciseReviewFilters
      :plan-id="selectedPlanId"
      :day-id="selectedDayId"
      :status="reviewStatus"
      :skill-query="skillQuery"
      :limit="limit"
      :plan-options="planOptions"
      :day-options="dayOptions"
      :skill-options="skillOptions"
      :active-count="activeFilterCount"
      @update:plan-id="changePlan"
      @update:day-id="selectedDayId = $event"
      @update:status="reviewStatus = $event"
      @update:skill-query="skillQuery = $event"
      @update:limit="changeLimit"
      @clear="clearFilters"
    />

    <n-alert v-if="error" type="error" :show-icon="false" closable @close="error = ''">{{ error }}</n-alert>

    <MasteryOverviewPanel
      :profiles="masteryProfiles"
      :weak-skills="weakSkills"
      :loading="masteryLoading"
      :recomputing="masteryRecomputing"
      :error="masteryError"
      @recompute="handleRecompute"
      @select-skill="skillQuery = $event"
    />

    <ExerciseRecordList
      :groups="groupedRecords"
      :total="summary.totalRecords"
      :loading="loading"
      :deleting-records="deletingRecordIds"
      :deleting-days="deletingDayIds"
      :reviewing="reviewingRecordIds"
      :reviewed="reviewedRecordIds"
      @clear-filters="clearFilters"
      @open-day="openLearningDay"
      @review="handleReview"
      @delete-record="confirmDeleteRecord"
      @delete-day="confirmDeleteDay"
    />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useDialog, useMessage } from 'naive-ui';
import { BadgeCheck, Gauge, ListChecks, RefreshCw, TriangleAlert } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import ExerciseRecordList from '../features/review/components/ExerciseRecordList.vue';
import ExerciseReviewFilters from '../features/review/components/ExerciseReviewFilters.vue';
import MasteryOverviewPanel from '../features/review/components/MasteryOverviewPanel.vue';
import { useExerciseReview } from '../features/review/composables/useExerciseReview';
import type { ExerciseRecord, ReviewGroup } from '../features/review/types';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const dialog = useDialog();
const message = useMessage();
const { currentUser } = useAuthStore();
const {
  masteryProfiles, selectedPlanId, selectedDayId, reviewStatus, skillQuery, limit,
  loading, masteryLoading, masteryRecomputing, error, masteryError, deletingRecordIds,
  deletingDayIds, reviewingRecordIds, reviewedRecordIds, planOptions, dayOptions, skillOptions,
  summary, groupedRecords, weakSkills, activeFilterCount, loadPlans, loadRecords, loadMastery,
  recomputeMastery, markReviewed, removeRecord, removeDay, changePlan, clearFilters
} = useExerciseReview({ currentUser });

const focusTitle = computed(() => {
  if (skillQuery.value) return skillQuery.value;
  if (reviewStatus.value === 'needs_review') return '待复习问题集';
  if (selectedDayId.value != null) return dayOptions.value.find((item) => String(item.value) === String(selectedDayId.value))?.label || '指定学习日';
  if (selectedPlanId.value != null) return planOptions.value.find((item) => String(item.value) === String(selectedPlanId.value))?.label || '指定计划';
  return '全部练习记录';
});

onMounted(() => Promise.all([loadPlans(), loadRecords(), loadMastery()]));

async function changeLimit(value: number) {
  limit.value = value;
  await loadRecords();
}

async function handleRecompute() {
  const success = await recomputeMastery();
  if (success) message.success('掌握度已根据最新学习事件重算');
  else message.error('掌握度重算失败，请稍后重试');
}

async function handleReview(record: ExerciseRecord) {
  const success = await markReviewed(record);
  if (success) message.success('已记录本次复习，并更新掌握度证据');
  else message.error('标记复习失败，请稍后重试');
}

function openLearningDay(group: ReviewGroup) {
  router.push({ name: 'plan-history', query: { planId: String(group.planId), dayId: String(group.dayId) } });
}

function confirmDeleteRecord(record: ExerciseRecord) {
  dialog.warning({
    title: '删除这条练习记录？',
    content: '删除后，这次作答和 AI 评测将从复盘记录中移除，掌握度会随之重新计算。',
    positiveText: '确认删除', negativeText: '保留记录', positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      const success = await removeRecord(record);
      if (success) message.success('练习记录已删除');
      else { message.error('删除失败，请稍后重试'); return false; }
      return true;
    }
  });
}

function confirmDeleteDay(group: ReviewGroup) {
  dialog.warning({
    title: '清空这个学习日的全部练习？',
    content: `“${group.dayTitle || '当前学习日'}”下的 ${group.records.length} 条作答与评测都会被移除，且无法恢复。`,
    positiveText: '确认清空', negativeText: '取消', positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      const success = await removeDay(group);
      if (success) message.success('本日练习记录已清空');
      else { message.error('清空失败，请稍后重试'); return false; }
      return true;
    }
  });
}
</script>

<style scoped>
.lf-review-page { display: grid; gap: 16px; min-width: 0; padding-bottom: 28px; }.lf-review-hero { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(280px, .7fr); gap: 18px; padding: clamp(22px, 4vw, 38px); overflow: hidden; border: 1px solid rgba(22,84,80,.12); border-radius: 28px; background: radial-gradient(circle at 82% 8%, rgba(196,139,69,.19), transparent 31%), linear-gradient(135deg, #f1faf8, #fff 57%, #f9f0e5); }.lf-review-hero__eyebrow { color: var(--lf-brand-700, #147a73); font-size: 11px; font-weight: 850; letter-spacing: .14em; text-transform: uppercase; }.lf-review-hero h1 { max-width: 720px; margin: 9px 0 10px; color: var(--lf-text, #17313d); font-size: clamp(30px, 4.8vw, 52px); line-height: 1.08; letter-spacing: -.04em; }.lf-review-hero > div:first-child p { max-width: 680px; margin: 0; color: var(--lf-text-muted, #62737b); font-size: 14px; line-height: 1.8; }
.lf-review-hero__focus { align-self: stretch; display: grid; align-content: center; gap: 9px; padding: 20px; border: 1px solid rgba(255,255,255,.8); border-radius: 20px; background: rgba(255,255,255,.7); box-shadow: 0 16px 40px rgba(18,53,59,.08); backdrop-filter: blur(10px); }.lf-review-hero__focus > span { color: var(--lf-text-muted, #62737b); font-size: 11px; font-weight: 800; }.lf-review-hero__focus > strong { color: var(--lf-text, #17313d); font-size: 19px; }.lf-review-hero__focus > p { margin: 0; color: var(--lf-text-muted, #62737b); font-size: 12px; line-height: 1.6; }.lf-review-hero__focus :deep(.n-button) { justify-self: start; margin-top: 3px; }
.lf-review-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }.lf-review-metrics article { display: flex; align-items: center; gap: 11px; padding: 14px 16px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 16px; background: var(--lf-surface, #fff); }.lf-review-metrics article > svg { flex: 0 0 auto; color: var(--lf-brand-600, #1a897f); }.lf-review-metrics article.is-warning > svg, .lf-review-metrics article.is-warning strong { color: #a06018; }.lf-review-metrics article > div { display: grid; }.lf-review-metrics span { color: var(--lf-text-muted, #62737b); font-size: 10px; font-weight: 700; }.lf-review-metrics strong { color: var(--lf-text, #17313d); font-size: 21px; }.lf-review-metrics small { color: var(--lf-text-muted, #62737b); font-size: 10px; }
@media (max-width: 900px) { .lf-review-hero { grid-template-columns: 1fr; }.lf-review-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px) { .lf-review-hero { border-radius: 22px; }.lf-review-metrics article { padding: 12px; } }
</style>
