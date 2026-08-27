<template>
  <aside class="lf-task-monitor" aria-labelledby="plan-task-monitor-title">
    <div class="lf-task-monitor-head">
      <div>
        <div class="lf-eyebrow">Generation status</div>
        <h2 id="plan-task-monitor-title">{{ phaseTitle }}</h2>
      </div>
      <span :class="['lf-task-state', `lf-task-state--${phase}`]">{{ phaseLabel }}</span>
    </div>

    <div class="lf-task-primary">
      <div class="lf-task-progress-copy"><strong>{{ progress }}%</strong><span>已等待 {{ formattedElapsed }}</span></div>
      <n-progress type="line" :percentage="progress" :show-indicator="false" :height="8" />
      <div class="lf-task-stage"><Sparkles :size="18" /><div><strong>{{ activeStage.title }}</strong><span>{{ activeStage.description }}</span></div></div>
    </div>

    <ol class="lf-task-stages" aria-label="计划生成阶段">
      <li v-for="stage in stages" :key="stage.key" :class="stageState(stage.threshold)">
        <span class="lf-task-stage-dot"><Check v-if="progress > stage.threshold" :size="12" /><Circle v-else :size="10" /></span>
        <div><strong>{{ stage.title }}</strong><small>{{ stage.description }}</small></div>
      </li>
    </ol>

    <div v-if="taskId" class="lf-task-id"><span>任务标识</span><code>{{ taskId }}</code></div>

    <n-alert v-if="error" type="error" :bordered="false" role="alert" class="lf-task-alert">{{ error }}</n-alert>
    <n-alert v-else-if="notice" type="info" :bordered="false" role="status" class="lf-task-alert">{{ notice }}</n-alert>

    <div v-if="isBusy" class="lf-task-actions">
      <n-button v-if="canPause" secondary :disabled="actionPending" @click="$emit('pause')">
        <template #icon><Pause /></template>暂停
      </n-button>
      <n-button v-if="canResume" type="primary" secondary :disabled="actionPending" @click="$emit('resume')">
        <template #icon><Play /></template>继续
      </n-button>
      <n-button secondary type="error" :disabled="!canCancel" @click="$emit('cancel')">
        <template #icon><Square /></template>{{ phase === 'cancelling' ? '正在取消' : '取消任务' }}
      </n-button>
    </div>
    <div v-else class="lf-task-actions">
      <n-button v-if="phase === 'failed'" type="primary" @click="$emit('retry')">返回检查并重试</n-button>
      <n-button secondary @click="$emit('history')"><History :size="16" />查看历史计划</n-button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Check, Circle, History, Pause, Play, Sparkles, Square } from 'lucide-vue-next';
import type { PlanGenerationPhase } from '../types';
import { planGenerationStages } from '../utils/planGeneration';

const props = defineProps<{
  phase: PlanGenerationPhase;
  progress: number;
  formattedElapsed: string;
  activeStage: (typeof planGenerationStages)[number];
  taskId: number | string | null;
  error: string;
  notice: string;
  isBusy: boolean;
  canPause: boolean;
  canResume: boolean;
  canCancel: boolean;
  actionPending: boolean;
}>();

defineEmits<{ pause: []; resume: []; cancel: []; retry: []; history: [] }>();

const stages = planGenerationStages;
const labels: Record<PlanGenerationPhase, string> = {
  idle: '待开始', creating: '正在提交', running: '规划中', paused: '已暂停', cancelling: '取消中',
  cancelled: '已取消', succeeded: '已完成', failed: '需要处理'
};
const phaseLabel = computed(() => labels[props.phase]);
const phaseTitle = computed(() => {
  if (props.phase === 'succeeded') return '新的学习计划已经准备好';
  if (props.phase === 'failed') return '本次任务没有完成';
  if (props.phase === 'paused') return '任务已安全暂停';
  if (props.phase === 'cancelled') return '任务已取消';
  if (props.isBusy) return '正在构建你的学习蓝图';
  return '任务控制与进度';
});
const stageState = (threshold: number) => ({
  'is-done': props.progress > threshold,
  'is-active': props.progress >= threshold && props.activeStage.threshold === threshold
});
</script>

<style scoped>
.lf-task-monitor { position: sticky; top: 22px; padding: 24px; border: 1px solid var(--lf-border-subtle); border-radius: var(--lf-radius-xl); background: var(--lf-bg-surface); box-shadow: var(--lf-shadow-sm); }
.lf-task-monitor-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.lf-task-monitor h2 { margin: 7px 0 0; color: var(--lf-text-strong); font-size: 20px; letter-spacing: -.025em; }
.lf-task-state { padding: 6px 9px; border-radius: var(--lf-radius-pill); background: var(--lf-neutral-100); color: var(--lf-text-muted); font-size: 10px; font-weight: 800; }
.lf-task-state--running, .lf-task-state--creating { background: var(--lf-brand-50); color: var(--lf-brand-800); }
.lf-task-state--paused { background: #fff6df; color: #875d0b; }
.lf-task-state--failed, .lf-task-state--cancelled { background: #fff0ef; color: #a13c35; }
.lf-task-state--succeeded { background: #e9f8ef; color: #267245; }
.lf-task-primary { margin-top: 22px; padding: 18px; border-radius: var(--lf-radius-lg); background: var(--lf-bg-subtle); }
.lf-task-progress-copy { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.lf-task-progress-copy strong { color: var(--lf-text-strong); font-size: 26px; }
.lf-task-progress-copy span { color: var(--lf-text-subtle); font-size: 11px; }
.lf-task-stage { display: flex; gap: 10px; margin-top: 15px; color: var(--lf-brand-700); }
.lf-task-stage div { display: flex; flex-direction: column; gap: 3px; }
.lf-task-stage strong { color: var(--lf-text-strong); font-size: 13px; }
.lf-task-stage span { color: var(--lf-text-muted); font-size: 11px; line-height: 1.55; }
.lf-task-stages { display: grid; gap: 0; margin: 22px 0; padding: 0; list-style: none; }
.lf-task-stages li { display: grid; grid-template-columns: 22px 1fr; gap: 9px; padding: 9px 0; color: var(--lf-text-subtle); }
.lf-task-stage-dot { display: grid; width: 20px; height: 20px; place-items: center; border: 1px solid var(--lf-border-default); border-radius: 50%; }
.lf-task-stages li div { display: flex; flex-direction: column; gap: 2px; }
.lf-task-stages strong { font-size: 12px; }
.lf-task-stages small { font-size: 10px; line-height: 1.45; }
.lf-task-stages .is-done, .lf-task-stages .is-active { color: var(--lf-brand-700); }
.lf-task-stages .is-active .lf-task-stage-dot { border-color: var(--lf-brand-600); background: var(--lf-brand-50); box-shadow: 0 0 0 4px rgba(33,129,125,.08); }
.lf-task-id { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; border: 1px dashed var(--lf-border-default); border-radius: var(--lf-radius-md); color: var(--lf-text-subtle); font-size: 10px; }
.lf-task-id code { overflow: hidden; color: var(--lf-text-muted); text-overflow: ellipsis; white-space: nowrap; }
.lf-task-alert { margin-top: 14px; }
.lf-task-actions { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 18px; }
@media (max-width: 1024px) { .lf-task-monitor { position: static; } }
</style>
