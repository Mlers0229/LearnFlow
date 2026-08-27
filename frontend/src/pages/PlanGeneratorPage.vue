<template>
  <div class="lf-plan-page">
    <header class="lf-plan-hero">
      <div class="lf-plan-hero-copy">
        <div class="lf-eyebrow">AI planning workspace</div>
        <h2>把目标、时间和学习偏好整理成一条可执行路径</h2>
        <p>先完成四步输入，再由持久化任务编排 Goal、Scheduler、Plan 与 Validator。页面会显示真实任务进度、耗时和任务标识。</p>
      </div>
      <dl class="lf-plan-hero-facts">
        <div><dt>学习容量</dt><dd>{{ totalCapacity }} 小时</dd></div>
        <div><dt>学习方式</dt><dd>{{ preferredStyleLabel }}</dd></div>
        <div><dt>输入约束</dt><dd>{{ constraintCount }} 条</dd></div>
      </dl>
    </header>

    <n-alert v-if="draftRestored" type="info" :bordered="false" closable class="lf-plan-draft-alert" @close="draftRestored = false">
      已恢复上次未完成的输入草稿，你可以继续修改或直接确认生成。
    </n-alert>

    <div class="lf-plan-workspace">
      <PlanSetupWizard v-model="form" :disabled="isBusy" @submit="submitPlan" />
      <PlanTaskMonitor
        :phase="phase"
        :progress="progress"
        :formatted-elapsed="formattedElapsed"
        :active-stage="activeStage"
        :task-id="taskId"
        :error="error"
        :notice="notice"
        :is-busy="isBusy"
        :can-pause="canPause"
        :can-resume="canResume"
        :can-cancel="canCancel"
        :action-pending="actionPending"
        @pause="pause"
        @resume="resume"
        @cancel="cancel"
        @retry="returnToWizard"
        @history="openHistory"
      />
    </div>

    <section class="lf-plan-result" aria-labelledby="plan-result-title">
      <div class="lf-plan-result-head">
        <div>
          <div class="lf-eyebrow">Plan output</div>
          <h2 id="plan-result-title">{{ plan ? '学习计划与执行蓝图' : '计划结果将在这里展开' }}</h2>
          <p>{{ resultDescription }}</p>
        </div>
        <RouterLink to="/history" class="lf-plan-history-link">查看历史计划<ArrowRight :size="16" /></RouterLink>
      </div>

      <div v-if="isBusy && plan" class="lf-plan-refreshing" role="status">
        <LoaderCircle :size="18" class="lf-plan-spin" />
        正在生成新计划，完成后会自动替换当前结果。
      </div>

      <PlanResultCard v-if="plan" :plan="plan" />
      <div v-else class="lf-plan-empty">
        <div class="lf-plan-empty-icon"><Route :size="25" /></div>
        <div>
          <strong>{{ isBusy ? activeStage.title : '从目标开始建立学习路径' }}</strong>
          <p>{{ isBusy ? activeStage.description : '完成上方四步配置后，这里会展示目标蓝图、阶段、周计划、每日任务、资源和练习入口。' }}</p>
        </div>
        <div class="lf-plan-preview-grid" aria-hidden="true">
          <span>目标蓝图</span><span>阶段节奏</span><span>每日任务</span><span>练习闭环</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { ArrowRight, LoaderCircle, Route } from 'lucide-vue-next';
import PlanResultCard from '../components/PlanResultCard.vue';
import PlanSetupWizard from '../features/plans/components/PlanSetupWizard.vue';
import PlanTaskMonitor from '../features/plans/components/PlanTaskMonitor.vue';
import { usePlanGenerationTask } from '../features/plans/composables/usePlanGenerationTask';
import type { PlanFormValue } from '../features/plans/types';
import {
  buildPlanPayload,
  normalizePlanLevel,
  parsePlanConstraints
} from '../features/plans/utils/planGeneration';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const { currentUser } = useAuthStore();
const draftRestored = ref(false);
const draftTimer = ref<ReturnType<typeof setTimeout> | null>(null);

const form = ref<PlanFormValue>({
  goalText: '',
  durationWeeks: 8,
  hoursPerDay: 1,
  level: normalizePlanLevel(currentUser.value?.level),
  targetRole: '',
  preferredStyle: 'balanced',
  finalDeliverable: '',
  constraintsText: ''
});

const {
  phase,
  taskId,
  progress,
  formattedElapsed,
  activeStage,
  plan,
  error,
  notice,
  actionPending,
  isBusy,
  canPause,
  canResume,
  canCancel,
  start,
  pause,
  resume,
  cancel,
  resetFeedback
} = usePlanGenerationTask();

const draftKey = computed(() => `learnflow:plan-draft:${currentUser.value?.id || 'current'}`);
const totalCapacity = computed(() => form.value.durationWeeks * 7 * form.value.hoursPerDay);
const constraintCount = computed(() => parsePlanConstraints(form.value.constraintsText).length);
const styleLabels = {
  balanced: '均衡推进',
  practice_first: '偏实践',
  theory_first: '偏理论',
  exercise_driven: '题目驱动'
};
const preferredStyleLabel = computed(() => styleLabels[form.value.preferredStyle]);
const resultDescription = computed(() => {
  if (isBusy.value) return `任务正在执行，当前进度 ${progress.value}%。生成期间可以安全暂停、继续或取消。`;
  if (plan.value) return '计划已保存，可以查看阶段、每日任务、资源、练习和执行状态。';
  return '任务完成后会自动保存并展示；页面中断时也可以从历史计划恢复。';
});

function restoreDraft() {
  try {
    const raw = localStorage.getItem(draftKey.value);
    if (!raw) return;
    const saved = JSON.parse(raw) as Partial<PlanFormValue>;
    form.value = { ...form.value, ...saved, level: normalizePlanLevel(saved.level || form.value.level) };
    draftRestored.value = Boolean(form.value.goalText.trim());
  } catch {
    localStorage.removeItem(draftKey.value);
  }
}

function saveDraft(value: PlanFormValue) {
  if (draftTimer.value) globalThis.clearTimeout(draftTimer.value);
  draftTimer.value = globalThis.setTimeout(() => {
    localStorage.setItem(draftKey.value, JSON.stringify(value));
  }, 350);
}

async function submitPlan() {
  await start(buildPlanPayload(form.value));
}

function returnToWizard() {
  resetFeedback();
  document.querySelector('.lf-plan-wizard')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function openHistory() {
  return router.push('/history');
}

watch(form, saveDraft, { deep: true });
onMounted(restoreDraft);
onBeforeUnmount(() => {
  if (draftTimer.value) globalThis.clearTimeout(draftTimer.value);
});
</script>

<style scoped>
.lf-plan-page { display: grid; gap: 24px; width: min(100%, 1500px); margin: 0 auto; }
.lf-plan-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 32px; padding: 26px 28px; border: 1px solid rgba(33,129,125,.16); border-radius: var(--lf-radius-xl); background: linear-gradient(135deg, rgba(237,249,247,.96), rgba(255,255,255,.98) 58%, rgba(255,246,225,.72)); }
.lf-plan-hero-copy { max-width: 760px; }
.lf-plan-hero h2 { margin: 8px 0 9px; color: var(--lf-text-strong); font-size: clamp(25px, 3vw, 38px); line-height: 1.14; letter-spacing: -.045em; }
.lf-plan-hero p { margin: 0; color: var(--lf-text-muted); font-size: 13px; line-height: 1.75; }
.lf-plan-hero-facts { display: grid; grid-template-columns: repeat(3, minmax(92px, 1fr)); gap: 8px; margin: 0; }
.lf-plan-hero-facts div { min-width: 105px; padding: 13px 14px; border: 1px solid rgba(33,129,125,.12); border-radius: var(--lf-radius-md); background: rgba(255,255,255,.8); }
.lf-plan-hero-facts dt { color: var(--lf-text-subtle); font-size: 9px; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }
.lf-plan-hero-facts dd { margin: 5px 0 0; color: var(--lf-text-strong); font-size: 13px; font-weight: 750; }
.lf-plan-draft-alert { margin-top: -8px; }
.lf-plan-workspace { display: grid; grid-template-columns: minmax(0, 1fr) minmax(300px, 370px); align-items: start; gap: 20px; }
.lf-plan-result { padding: 28px; border: 1px solid var(--lf-border-subtle); border-radius: var(--lf-radius-xl); background: var(--lf-bg-surface); box-shadow: var(--lf-shadow-sm); }
.lf-plan-result-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 22px; }
.lf-plan-result-head h2 { margin: 7px 0; color: var(--lf-text-strong); font-size: 24px; letter-spacing: -.03em; }
.lf-plan-result-head p { margin: 0; color: var(--lf-text-muted); font-size: 12px; line-height: 1.65; }
.lf-plan-history-link { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 7px; color: var(--lf-brand-700); font-size: 12px; font-weight: 750; text-decoration: none; }
.lf-plan-refreshing { display: flex; align-items: center; gap: 9px; margin-bottom: 18px; padding: 11px 13px; border-radius: var(--lf-radius-md); background: var(--lf-brand-50); color: var(--lf-brand-800); font-size: 12px; }
.lf-plan-spin { animation: lf-plan-spin 1s linear infinite; }
.lf-plan-empty { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 16px; min-height: 260px; padding: 28px; border: 1px dashed var(--lf-border-default); border-radius: var(--lf-radius-lg); background: var(--lf-bg-subtle); }
.lf-plan-empty-icon { display: grid; width: 54px; height: 54px; place-items: center; border-radius: 18px; background: var(--lf-brand-50); color: var(--lf-brand-700); }
.lf-plan-empty strong { color: var(--lf-text-strong); font-size: 17px; }
.lf-plan-empty p { max-width: 650px; margin: 6px 0 0; color: var(--lf-text-muted); font-size: 12px; line-height: 1.7; }
.lf-plan-preview-grid { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.lf-plan-preview-grid span { padding: 12px; border-radius: var(--lf-radius-md); background: var(--lf-bg-surface); color: var(--lf-text-subtle); font-size: 10px; font-weight: 750; text-align: center; }
@keyframes lf-plan-spin { to { transform: rotate(360deg); } }
@media (max-width: 1180px) { .lf-plan-hero { align-items: flex-start; flex-direction: column; } .lf-plan-workspace { grid-template-columns: 1fr; } }
@media (max-width: 640px) {
  .lf-plan-page { gap: 16px; }
  .lf-plan-hero, .lf-plan-result { padding: 20px 16px; }
  .lf-plan-hero-facts { width: 100%; grid-template-columns: 1fr; }
  .lf-plan-hero-facts div { display: flex; align-items: center; justify-content: space-between; }
  .lf-plan-hero-facts dd { margin: 0; }
  .lf-plan-result-head { flex-direction: column; }
  .lf-plan-empty { grid-template-columns: 1fr; padding: 22px 16px; }
  .lf-plan-preview-grid { grid-column: auto; grid-template-columns: repeat(2, 1fr); }
}
@media (prefers-reduced-motion: reduce) { .lf-plan-spin { animation: none; } }
</style>
