<template>
  <main class="lf-history-page">
    <header class="lf-history-hero">
      <div class="lf-history-hero__copy">
        <span class="lf-history-hero__eyebrow">Learning command center</span>
        <h1>把计划变成今天能完成的行动</h1>
        <p>回到上次浏览的日程，完成任务、获取资源、练习检验，并在节奏变化时快速重排。</p>
      </div>
      <div class="lf-history-hero__focus">
        <span>当前计划</span>
        <strong>{{ currentPlan?.title || '尚未选择计划' }}</strong>
        <div v-if="currentPlan" class="lf-history-hero__progress">
          <n-progress type="line" :percentage="completionRate" :height="8" :show-indicator="false" />
          <small>{{ completedDays }}/{{ totalDays }} 天完成 · {{ completionRate }}%</small>
        </div>
        <p v-else>创建计划后，这里会成为你的每日学习入口。</p>
      </div>
    </header>

    <section class="lf-history-metrics" aria-label="计划进度概览">
      <div><CalendarRange :size="18" /><span>计划周期<strong>{{ totalDays }} 天</strong></span></div>
      <div><BadgeCheck :size="18" /><span>已完成<strong>{{ completedDays }} 天</strong></span></div>
      <div><Target :size="18" /><span>今日定位<strong>{{ currentDay?.date || '未选择' }}</strong></span></div>
      <div v-if="adaptationDescription"><Sparkles :size="18" /><span>自适应策略<strong>{{ adaptationDescription }}</strong></span></div>
    </section>

    <div class="lf-history-layout">
      <PlanHistoryNavigator
        :plans="plans"
        :current-plan="currentPlan"
        :current-day-id="currentDayId"
        :selected-plan-id="selectedPlanId"
        :plan-options="planSelectOptions"
        :list-loading="listLoading"
        :detail-loading="detailLoading"
        :list-error="listError"
        :detail-error="detailError"
        @select-plan="handleSelectPlan"
        @select-day="selectDay"
        @create-plan="router.push('/plan')"
      />

      <div class="lf-history-content">
        <div v-if="currentPlan" class="lf-plan-toolbar">
          <div>
            <span>计划管理</span>
            <strong>{{ currentPlan.title || `学习计划 #${selectedPlanId}` }}</strong>
          </div>
          <n-space size="small">
            <n-button size="small" secondary @click="openRename"><Pencil :size="15" />重命名</n-button>
            <n-button size="small" secondary type="error" :loading="deleting" @click="confirmDelete"><Trash2 :size="15" />删除</n-button>
          </n-space>
        </div>

        <n-skeleton v-if="detailLoading" text :repeat="8" class="lf-detail-skeleton" />
        <n-alert v-else-if="detailError" type="error" :show-icon="false">{{ detailError }}</n-alert>
        <template v-else>
          <PlanDayWorkspace
            :day="currentDay"
            :status-state="currentDay ? typedDayStatusMap[currentDay.id] : undefined"
            :refine-state="currentDay ? typedDayRefineMap[currentDay.id] : undefined"
            :replan-state="currentDay ? dayReplanMap[currentDay.id] : undefined"
            :resource-state="currentDay ? typedDayResourcesMap[currentDay.id] : undefined"
            :exercise-state="currentDay ? typedDayExercisesMap[currentDay.id] : undefined"
            :feedback-state="getFeedbackState"
            :format-feedback="formatResourceFeedbackLabel"
            :get-result="getExerciseResult"
            :is-submitting="isExerciseSubmitting"
            :score-tag="scoreTagType"
            :format-mistake="formatMistakeType"
            @complete="handleComplete"
            @undo-complete="handleUndoComplete"
            @refine="handleRefine"
            @replan="openReplan"
            @load-resources="loadDayResources"
            @load-exercises="loadDayExercises"
            @upload="goToResourceUpload"
            @feedback="handleResourceFeedback"
            @update-answer="updateExerciseAnswer"
            @submit-answer="saveExerciseForDay"
          />

          <PlanResourcePanel
            v-if="currentPlan"
            eyebrow="计划资料库"
            title="覆盖整份计划的核心资源"
            description="用于建立全局认知地图；每日执行资源请在上方按需加载。"
            action-label="获取计划资源"
            scope-label="计划目标"
            :items="planResources.items"
            :loading="planResources.loading"
            :loaded-once="planResources.loadedOnce"
            :error="planResources.error"
            :feedback-state="getFeedbackState"
            :format-feedback="formatResourceFeedbackLabel"
            @load="loadPlanResources"
            @upload="goToResourceUpload"
            @feedback="handleResourceFeedback"
          />
        </template>
      </div>
    </div>

    <n-modal v-model:show="renameModalOpen" preset="card" title="重命名计划" class="lf-action-modal" :mask-closable="!renaming">
      <n-form-item label="计划名称" :validation-status="renameError ? 'error' : undefined" :feedback="renameError">
        <n-input v-model:value="renameTitle" maxlength="80" show-count placeholder="输入清晰、容易识别的计划名称" @keyup.enter="submitRename" />
      </n-form-item>
      <template #footer><n-space justify="end"><n-button :disabled="renaming" @click="renameModalOpen = false">取消</n-button><n-button type="primary" :loading="renaming" @click="submitRename">保存名称</n-button></n-space></template>
    </n-modal>

    <n-modal v-model:show="replanModalOpen" preset="card" title="顺延并重排计划" class="lf-action-modal" :mask-closable="!replanning">
      <n-alert type="warning" :show-icon="false">将从当前学习日开始顺延 1 天，并重新安排后续日程；已完成的内容不会回退。</n-alert>
      <n-form-item label="顺延原因（可选）" class="lf-modal-field">
        <n-input v-model:value="replanReason" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" maxlength="200" show-count placeholder="例如：今天临时加班，需要调整后续节奏" />
      </n-form-item>
      <p v-if="replanError" class="lf-modal-error">{{ replanError }}</p>
      <template #footer><n-space justify="end"><n-button :disabled="replanning" @click="replanModalOpen = false">取消</n-button><n-button type="warning" :loading="replanning" @click="submitReplan">确认顺延并重排</n-button></n-space></template>
    </n-modal>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useDialog, useMessage } from 'naive-ui';
import { BadgeCheck, CalendarRange, Pencil, Sparkles, Target, Trash2 } from 'lucide-vue-next';
import { useRoute, useRouter } from 'vue-router';
import { deletePlan, getResourcesByPlan, replanPlan, updatePlan } from '../api/plan';
import { usePlanStudyActions } from '../composables/usePlanStudyActions';
import { useResourceFeedback } from '../composables/useResourceFeedback';
import PlanDayWorkspace from '../features/plans/components/PlanDayWorkspace.vue';
import PlanHistoryNavigator from '../features/plans/components/PlanHistoryNavigator.vue';
import PlanResourcePanel from '../features/plans/components/PlanResourcePanel.vue';
import { getPlanIdentity, usePlanHistory, type PlanDay } from '../features/plans/composables/usePlanHistory';
import { useAuthStore } from '../store/auth';
import { describeAdaptation } from '../utils/adaptation';

const router = useRouter();
const route = useRoute();
const message = useMessage();
const dialog = useDialog();
const { currentUser } = useAuthStore();
const {
  plans, currentPlan, currentDayId, currentDay, listLoading, detailLoading, listError, detailError,
  selectedPlanId, planSelectOptions, totalDays, completedDays, completionRate,
  loadRecentPlans, loadPlanDetail, selectDay, replaceCurrentPlan, updateCurrentTitle, forgetPlan
} = usePlanHistory({ currentUser });

const {
  hydrateResourceFeedback, sendResourceFeedback, getFeedbackState, formatResourceFeedbackLabel
} = useResourceFeedback(currentUser);
const {
  dayResourcesMap, dayStatusSavingMap, dayRefineMap, dayExercisesMap,
  loadDayResources, loadDayExercises, markDayCompleted, cancelDayCompleted, refineDayTasks,
  saveExerciseForDay, getExerciseResult, isExerciseSubmitting, scoreTagType, formatMistakeType
} = usePlanStudyActions(currentUser, { onDayResourcesLoaded: hydrateResourceFeedback });

type ResourceItem = Record<string, unknown> & { id?: number | string; url?: string };
type LegacyAsyncState = { loading?: boolean; saving?: boolean; error?: string };
type LegacyResourceState = LegacyAsyncState & { items?: ResourceItem[]; loadedOnce?: boolean };
type LegacyExerciseState = LegacyAsyncState & {
  items?: Array<Record<string, unknown>>;
  answers?: string[];
  loadedOnce?: boolean;
  results?: Array<Record<string, unknown> | null>;
  submittingIndex?: number | null;
  lastSubmittedIndex?: number | null;
  saveError?: string;
  saveSuccessMessage?: string;
};
const typedDayStatusMap = dayStatusSavingMap as Record<string, LegacyAsyncState>;
const typedDayRefineMap = dayRefineMap as Record<string, LegacyAsyncState>;
const typedDayResourcesMap = dayResourcesMap as Record<string, LegacyResourceState>;
const typedDayExercisesMap = dayExercisesMap as Record<string, LegacyExerciseState>;
const planResources = reactive({ loading: false, error: '', items: [] as ResourceItem[], loadedOnce: false });
const dayReplanMap = reactive<Record<string, { loading: boolean; error: string }>>({});
const renameModalOpen = ref(false);
const renameTitle = ref('');
const renameError = ref('');
const renaming = ref(false);
const replanModalOpen = ref(false);
const replanReason = ref('');
const replanError = ref('');
const replanning = ref(false);
const deleting = ref(false);
const adaptationDescription = computed(() => describeAdaptation(currentPlan.value?.adaptation, { detailed: false }));

onMounted(async () => {
  const queryPlanId = typeof route.query.planId === 'string' ? route.query.planId : undefined;
  await loadRecentPlans(queryPlanId);
  const queryDayId = typeof route.query.dayId === 'string' ? route.query.dayId : undefined;
  if (queryDayId && currentPlan.value?.days?.some((day) => String(day.id) === queryDayId)) {
    selectDay(queryDayId);
  }
});

function resetPlanResources() {
  planResources.loading = false;
  planResources.error = '';
  planResources.items = [];
  planResources.loadedOnce = false;
}

async function handleSelectPlan(id: number | string) {
  const loaded = await loadPlanDetail(id);
  if (loaded) resetPlanResources();
}

async function handleComplete(day: PlanDay) {
  await markDayCompleted(day);
  if (!typedDayStatusMap[day.id]?.error) message.success('今日任务已完成，进度已更新');
}

async function handleUndoComplete(day: PlanDay) {
  await cancelDayCompleted(day);
  if (!typedDayStatusMap[day.id]?.error) message.info('已撤销完成状态');
}

async function handleRefine(day: PlanDay) {
  await refineDayTasks(day);
  if (!typedDayRefineMap[day.id]?.error) message.success('任务已细化，可以按步骤开始执行');
}

function updateExerciseAnswer(dayId: number | string, index: number, value: string) {
  const state = typedDayExercisesMap[dayId];
  if (state?.answers) state.answers[index] = value;
}

function handleResourceFeedback(resource: ResourceItem, value: 'helpful' | 'invalid') {
  sendResourceFeedback(resource, value)
    .then(() => message.success('资源反馈已保存'))
    .catch(() => message.error('资源反馈保存失败，请稍后重试'));
}

function goToResourceUpload() { router.push('/upload-resource'); }

async function loadPlanResources() {
  const planId = getPlanIdentity(currentPlan.value);
  if (!planId) return;
  planResources.loading = true;
  planResources.error = '';
  try {
    planResources.items = hydrateResourceFeedback(await getResourcesByPlan(planId) || []);
    planResources.loadedOnce = true;
  } catch (error) {
    console.error(error);
    planResources.error = '加载计划推荐资源失败，请稍后重试。';
  } finally {
    planResources.loading = false;
  }
}

function openRename() {
  renameTitle.value = currentPlan.value?.title || '';
  renameError.value = '';
  renameModalOpen.value = true;
}

async function submitRename() {
  const title = renameTitle.value.trim();
  if (!title) { renameError.value = '请输入计划名称。'; return; }
  const planId = getPlanIdentity(currentPlan.value);
  if (!planId) return;
  renaming.value = true;
  renameError.value = '';
  try {
    await updatePlan(planId, currentUser.value?.id, { title });
    updateCurrentTitle(title);
    renameModalOpen.value = false;
    message.success('计划名称已更新');
  } catch (error) {
    console.error(error);
    renameError.value = '更新失败，请稍后重试。';
  } finally {
    renaming.value = false;
  }
}

function openReplan(day: PlanDay) {
  if (!day.id) return;
  replanReason.value = '';
  replanError.value = '';
  replanModalOpen.value = true;
}

async function submitReplan() {
  const planId = getPlanIdentity(currentPlan.value);
  const dayId = currentDay.value?.id;
  const userId = Number((currentUser.value as { id?: number | string } | null)?.id);
  if (!planId || !dayId || !Number.isFinite(userId)) {
    replanError.value = '登录状态已失效，请重新登录后再试。';
    return;
  }
  const state = dayReplanMap[dayId] ||= { loading: false, error: '' };
  state.loading = true;
  state.error = '';
  replanning.value = true;
  replanError.value = '';
  try {
    const updated = await replanPlan(planId, { userId, triggerDayId: dayId, delayDays: 1, reason: replanReason.value.trim() || undefined });
    replaceCurrentPlan(updated, dayId);
    resetPlanResources();
    replanModalOpen.value = false;
    message.success('计划已顺延并完成重排');
  } catch (error) {
    console.error(error);
    state.error = '顺延并重排失败，请稍后重试。';
    replanError.value = state.error;
  } finally {
    state.loading = false;
    replanning.value = false;
  }
}

function confirmDelete() {
  const planId = getPlanIdentity(currentPlan.value);
  if (!planId) return;
  dialog.warning({
    title: '删除这份学习计划？',
    content: `“${currentPlan.value?.title || `学习计划 #${planId}`}”删除后将不再出现在历史计划中。`,
    positiveText: '确认删除',
    negativeText: '保留计划',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      deleting.value = true;
      try {
        await deletePlan(planId);
        forgetPlan(planId);
        resetPlanResources();
        await loadRecentPlans();
        message.success('计划已删除');
      } catch (error) {
        console.error(error);
        message.error('删除计划失败，请稍后重试');
        return false;
      } finally {
        deleting.value = false;
      }
      return true;
    }
  });
}
</script>

<style scoped>
.lf-history-page { display: grid; gap: 16px; min-width: 0; padding-bottom: 28px; }.lf-history-hero { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(280px, .7fr); gap: 18px; padding: clamp(22px, 4vw, 38px); overflow: hidden; border: 1px solid rgba(22,84,80,.12); border-radius: 28px; background: radial-gradient(circle at 80% 10%, rgba(231,173,95,.18), transparent 33%), linear-gradient(135deg, #f0faf7 0%, #fff 58%, #fbf4e8 100%); }
.lf-history-hero__eyebrow { color: var(--lf-brand-700, #147a73); font-size: 11px; font-weight: 850; letter-spacing: .14em; text-transform: uppercase; }.lf-history-hero h1 { max-width: 720px; margin: 9px 0 10px; color: var(--lf-text, #17313d); font-size: clamp(30px, 4.8vw, 52px); line-height: 1.08; letter-spacing: -.04em; }.lf-history-hero__copy p { max-width: 680px; margin: 0; color: var(--lf-text-muted, #62737b); font-size: 14px; line-height: 1.8; }
.lf-history-hero__focus { align-self: stretch; display: grid; align-content: center; gap: 9px; padding: 20px; border: 1px solid rgba(255,255,255,.78); border-radius: 20px; background: rgba(255,255,255,.7); box-shadow: 0 16px 40px rgba(18,53,59,.08); backdrop-filter: blur(10px); }.lf-history-hero__focus > span { color: var(--lf-text-muted, #62737b); font-size: 11px; font-weight: 800; }.lf-history-hero__focus > strong { color: var(--lf-text, #17313d); font-size: 19px; }.lf-history-hero__focus > p { margin: 0; color: var(--lf-text-muted, #62737b); font-size: 12px; }.lf-history-hero__progress { display: grid; gap: 7px; }.lf-history-hero__progress small { color: var(--lf-text-muted, #62737b); }
.lf-history-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }.lf-history-metrics > div { display: flex; align-items: center; gap: 10px; min-width: 0; padding: 13px 15px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 15px; background: var(--lf-surface, #fff); }.lf-history-metrics svg { flex: 0 0 auto; color: var(--lf-brand-600, #1a897f); }.lf-history-metrics span { display: grid; min-width: 0; color: var(--lf-text-muted, #62737b); font-size: 10px; }.lf-history-metrics strong { overflow: hidden; margin-top: 2px; color: var(--lf-text, #17313d); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.lf-history-layout { display: grid; grid-template-columns: minmax(270px, 320px) minmax(0, 1fr); gap: 16px; align-items: start; }.lf-history-content { display: grid; gap: 16px; min-width: 0; }.lf-plan-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 13px 16px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 16px; background: var(--lf-surface, #fff); }.lf-plan-toolbar > div { display: grid; min-width: 0; }.lf-plan-toolbar span { color: var(--lf-text-muted, #62737b); font-size: 10px; font-weight: 800; }.lf-plan-toolbar strong { overflow: hidden; margin-top: 2px; color: var(--lf-text, #17313d); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.lf-detail-skeleton { padding: 22px; border-radius: 20px; background: #fff; }.lf-action-modal { width: min(520px, calc(100vw - 28px)); }.lf-modal-field { margin-top: 16px; }.lf-modal-error { margin: 8px 0 0; color: #c33d3d; font-size: 12px; }
@media (max-width: 1080px) { .lf-history-metrics { grid-template-columns: repeat(2, 1fr); }.lf-history-layout { grid-template-columns: 280px minmax(0, 1fr); } }
@media (max-width: 820px) { .lf-history-hero { grid-template-columns: 1fr; }.lf-history-layout { grid-template-columns: 1fr; }.lf-plan-toolbar { align-items: flex-start; flex-direction: column; } }
@media (max-width: 520px) { .lf-history-metrics { grid-template-columns: 1fr 1fr; }.lf-history-metrics > div { padding: 11px; }.lf-history-hero { border-radius: 22px; }.lf-plan-toolbar :deep(.n-space) { width: 100%; }.lf-plan-toolbar :deep(.n-button) { flex: 1; } }
</style>
