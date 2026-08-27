import { computed, onScopeDispose, ref } from 'vue';
import {
  cancelAsyncTask,
  createPlanTask,
  getAsyncTask,
  getPlanById,
  getRecentPlans,
  pauseAsyncTask,
  resumeAsyncTask
} from '../../../api/plan';
import type { PlanGenerationPhase, PlanPayload, PlanRecord, PlanTask } from '../types';
import {
  formatPlanElapsed,
  newPlanIdempotencyKey,
  planGenerationErrorMessage,
  planGenerationStages
} from '../utils/planGeneration';

export type PlanGenerationApi = {
  createTask: (payload: PlanPayload, key: string) => Promise<PlanTask>;
  getTask: (taskId: number | string) => Promise<PlanTask>;
  getPlan: (planId: number | string) => Promise<PlanRecord>;
  recentPlans: (limit: number) => Promise<PlanRecord[]>;
  pauseTask: (taskId: number | string) => Promise<PlanTask>;
  resumeTask: (taskId: number | string) => Promise<PlanTask>;
  cancelTask: (taskId: number | string) => Promise<PlanTask>;
};

const defaultApi: PlanGenerationApi = {
  createTask: createPlanTask,
  getTask: getAsyncTask,
  getPlan: getPlanById,
  recentPlans: getRecentPlans,
  pauseTask: pauseAsyncTask,
  resumeTask: resumeAsyncTask,
  cancelTask: cancelAsyncTask
};

type UsePlanGenerationOptions = {
  api?: PlanGenerationApi;
  pollIntervalMs?: number;
  timeoutMs?: number;
};

const delay = (milliseconds: number) =>
  new Promise<void>((resolve) => globalThis.setTimeout(resolve, milliseconds));

function safeTime(value: unknown) {
  const timestamp = value ? new Date(String(value)).getTime() : Number.NaN;
  return Number.isNaN(timestamp) ? null : timestamp;
}

export function usePlanGenerationTask(options: UsePlanGenerationOptions = {}) {
  const api = options.api || defaultApi;
  const pollIntervalMs = options.pollIntervalMs ?? 1500;
  const timeoutMs = options.timeoutMs ?? 10 * 60 * 1000;

  const phase = ref<PlanGenerationPhase>('idle');
  const taskId = ref<number | string | null>(null);
  const progress = ref(0);
  const elapsedSeconds = ref(0);
  const plan = ref<PlanRecord | null>(null);
  const error = ref('');
  const notice = ref('');
  const actionPending = ref(false);

  let timer: ReturnType<typeof setInterval> | null = null;
  let startedAt = 0;
  let sequence = 0;
  let pauseLocked = false;

  const isBusy = computed(() => ['creating', 'running', 'paused', 'cancelling'].includes(phase.value));
  const canPause = computed(() => phase.value === 'running' && !actionPending.value);
  const canResume = computed(() => phase.value === 'paused' && !actionPending.value);
  const canCancel = computed(() => isBusy.value && phase.value !== 'cancelling' && !actionPending.value);
  const formattedElapsed = computed(() => formatPlanElapsed(elapsedSeconds.value));
  const activeStage = computed(() =>
    [...planGenerationStages].reverse().find((stage) => progress.value >= stage.threshold) || planGenerationStages[0]
  );

  function clearClock() {
    if (timer) globalThis.clearInterval(timer);
    timer = null;
  }

  function startClock() {
    startedAt = Date.now();
    elapsedSeconds.value = 0;
    clearClock();
    timer = globalThis.setInterval(() => {
      elapsedSeconds.value = Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
  }

  function applyTask(task: PlanTask) {
    progress.value = Math.min(100, Math.max(0, Number(task.progress || 0)));
    if (task.status === 'PAUSED') {
      pauseLocked = true;
      phase.value = 'paused';
    }
    if ((task.status === 'PENDING' || task.status === 'RUNNING') && !pauseLocked) {
      phase.value = 'running';
    }
  }

  async function waitForResult(currentTaskId: number | string, run: number) {
    const expiresAt = Date.now() + timeoutMs;
    let consecutiveFailures = 0;

    while (run === sequence && Date.now() < expiresAt) {
      try {
        const task = await api.getTask(currentTaskId);
        consecutiveFailures = 0;
        applyTask(task);

        if (task.status === 'SUCCEEDED' && task.resultResourceId != null) {
          progress.value = 100;
          return api.getPlan(task.resultResourceId);
        }
        if (task.status === 'FAILED') throw new Error(task.errorCode || 'TASK_FAILED');
        if (task.status === 'CANCELLED') throw new Error('TASK_CANCELLED');
      } catch (cause) {
        const message = cause instanceof Error ? cause.message : '';
        if (message.startsWith('TASK_')) throw cause;
        consecutiveFailures += 1;
        if (consecutiveFailures >= 3) throw cause;
      }

      await delay(pollIntervalMs);
    }

    if (run !== sequence) throw new Error('TASK_ABORTED');
    throw new Error('TASK_POLL_TIMEOUT');
  }

  async function recoverRecentPlan(generationStartedAt: number) {
    for (const waitMs of [0, 1800, 2500]) {
      if (waitMs) await delay(waitMs);
      const recentPlans = await api.recentPlans(6);
      const match = recentPlans.find((item) => {
        const createdTime = safeTime(item.createdAt || item.updatedAt);
        return createdTime != null && createdTime >= generationStartedAt - 15000;
      });
      if (match?.id != null) return api.getPlan(match.id);
    }
    return null;
  }

  async function start(payload: PlanPayload) {
    const run = ++sequence;
    error.value = '';
    notice.value = '';
    progress.value = 0;
    taskId.value = null;
    phase.value = 'creating';
    pauseLocked = false;
    startClock();
    const generationStartedAt = startedAt;

    try {
      const task = await api.createTask(payload, newPlanIdempotencyKey());
      if (run !== sequence) return null;
      taskId.value = task.id;
      applyTask(task);
      const generatedPlan = await waitForResult(task.id, run);
      if (run !== sequence) return null;
      plan.value = generatedPlan;
      phase.value = 'succeeded';
      notice.value = `学习计划已生成完成，本次耗时 ${formattedElapsed.value}。`;
      return generatedPlan;
    } catch (cause) {
      if (run !== sequence || (cause instanceof Error && cause.message === 'TASK_ABORTED')) return null;
      if (cause instanceof Error && cause.message === 'TASK_CANCELLED') {
        phase.value = 'cancelled';
        notice.value = '计划生成任务已取消，输入草稿仍然保留。';
        return null;
      }

      const taskMessage = cause instanceof Error ? cause.message : '';
      if (taskMessage.startsWith('TASK_') && taskMessage !== 'TASK_POLL_TIMEOUT') {
        phase.value = 'failed';
        error.value = planGenerationErrorMessage(cause);
        clearClock();
        return null;
      }

      try {
        const recoveredPlan = await recoverRecentPlan(generationStartedAt);
        if (recoveredPlan && run === sequence) {
          plan.value = recoveredPlan;
          phase.value = 'succeeded';
          notice.value = `计划已在后台生成，页面已自动恢复结果。总耗时 ${formattedElapsed.value}。`;
          return recoveredPlan;
        }
      } catch {
        // Recovery is best-effort; the original task error remains the user-facing cause.
      }

      phase.value = 'failed';
      error.value = planGenerationErrorMessage(cause);
      return null;
    } finally {
      if (run === sequence && !isBusy.value) clearClock();
    }
  }

  async function pause() {
    if (!taskId.value || !canPause.value) return;
    actionPending.value = true;
    error.value = '';
    try {
      const task = await api.pauseTask(taskId.value);
      applyTask(task);
      notice.value = '任务已暂停，当前进度和 Checkpoint 已保留。';
    } catch (cause) {
      error.value = planGenerationErrorMessage(cause);
    } finally {
      actionPending.value = false;
    }
  }

  async function resume() {
    if (!taskId.value || !canResume.value) return;
    actionPending.value = true;
    error.value = '';
    try {
      const task = await api.resumeTask(taskId.value);
      pauseLocked = false;
      applyTask(task);
      notice.value = '任务已继续，将从最近一次完整 Checkpoint 恢复。';
    } catch (cause) {
      error.value = planGenerationErrorMessage(cause);
    } finally {
      actionPending.value = false;
    }
  }

  async function cancel() {
    if (!taskId.value || !canCancel.value) return;
    actionPending.value = true;
    phase.value = 'cancelling';
    error.value = '';
    try {
      await api.cancelTask(taskId.value);
      sequence += 1;
      phase.value = 'cancelled';
      pauseLocked = false;
      notice.value = '任务已取消，输入草稿仍然保留。';
      clearClock();
    } catch (cause) {
      phase.value = 'running';
      error.value = planGenerationErrorMessage(cause);
    } finally {
      actionPending.value = false;
    }
  }

  function resetFeedback() {
    error.value = '';
    notice.value = '';
    if (!isBusy.value) phase.value = 'idle';
  }

  onScopeDispose(() => {
    sequence += 1;
    clearClock();
  });

  return {
    phase,
    taskId,
    progress,
    elapsedSeconds,
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
  };
}
