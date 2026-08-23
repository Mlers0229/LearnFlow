<template>
  <div class="plan-generator-page">
    <section class="generator-hero">
      <div class="generator-hero-copy">
        <div class="hero-kicker">AI Study Planning Studio</div>
        <h1 class="title hero-title">把学习目标拆成真正能执行的节奏</h1>
        <p class="subtitle hero-subtitle">
          输入目标、周期、学习偏好和最终产出，LearnFlow 会把 GoalAgent、SchedulerAgent、PlanAgent 和 Validator
          串成一条完整规划链路，生成一份适合落地执行的学习蓝图。
        </p>
        <div class="hero-chip-row">
          <span v-for="chip in heroChips" :key="chip" class="hero-chip">{{ chip }}</span>
        </div>
      </div>

      <div class="hero-side-panel">
        <div class="hero-panel-title">这次规划会重点关注</div>
        <div class="hero-facts-grid">
          <div v-for="fact in planningFacts" :key="fact.label" class="hero-fact-card">
            <div class="hero-fact-label">{{ fact.label }}</div>
            <div class="hero-fact-value">{{ fact.value }}</div>
          </div>
        </div>
        <div class="hero-flow">
          <div v-for="step in workflowSteps" :key="step.title" class="hero-flow-item">
            <div class="hero-flow-index">{{ step.index }}</div>
            <div>
              <div class="hero-flow-title">{{ step.title }}</div>
              <div class="hero-flow-desc">{{ step.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div class="plan-generator-layout">
      <n-card class="plan-generator-card" :bordered="true" hoverable>
        <template #header>
          <div class="panel-header">
            <div>
              <div class="panel-kicker">输入条件</div>
              <div class="panel-title">创建一份新的学习计划</div>
            </div>
            <div class="panel-badge">v2 编排链路</div>
          </div>
        </template>

        <n-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-placement="top"
          size="small"
          @submit.prevent="onSubmit"
        >
          <div class="form-row form-row-full">
            <n-form-item label="学习目标（必填）" path="goalText" class="form-item-grow">
              <n-input
                v-model:value="form.goalText"
                type="textarea"
                placeholder="例如：8 周转向 Java 后端开发，能独立完成一个 Spring Boot + MySQL 的小项目"
                :autosize="{ minRows: 4, maxRows: 6 }"
              />
            </n-form-item>
          </div>

          <div class="form-row form-row-grid form-row-grid-4">
            <n-form-item label="学习周期（周）" path="durationWeeks">
              <n-input-number
                v-model:value="form.durationWeeks"
                :min="1"
                :max="52"
                placeholder="建议 4 ~ 12 周"
              />
            </n-form-item>

            <n-form-item label="每天学习时长（小时）" path="hoursPerDay">
              <n-input-number
                v-model:value="form.hoursPerDay"
                :min="1"
                :max="10"
                placeholder="建议每天至少 1 小时"
              />
            </n-form-item>

            <n-form-item label="基础水平" path="level">
              <n-select
                v-model:value="form.level"
                :options="levelOptions"
              />
            </n-form-item>

            <n-form-item label="偏好学习方式" path="preferredStyle">
              <n-select
                v-model:value="form.preferredStyle"
                :options="preferredStyleOptions"
              />
            </n-form-item>
          </div>

          <div class="form-row form-row-grid">
            <n-form-item label="目标岗位 / 方向" path="targetRole">
              <n-input
                v-model:value="form.targetRole"
                placeholder="例如：Java 后端工程师 / 算法竞赛 / 英语口语"
              />
            </n-form-item>

            <n-form-item label="期望最终产出" path="finalDeliverable">
              <n-input
                v-model:value="form.finalDeliverable"
                placeholder="例如：完成一个可部署的小项目 / 通过一次面试 / 写出一篇总结"
              />
            </n-form-item>
          </div>

          <div class="form-row form-row-full">
            <n-form-item label="学习约束（每行一条，可选）" path="constraintsText" class="form-item-grow">
              <n-input
                v-model:value="form.constraintsText"
                type="textarea"
                placeholder="例如：&#10;周一到周五只能晚上学习&#10;周末可以安排项目练习&#10;希望多做题、少看长视频"
                :autosize="{ minRows: 3, maxRows: 6 }"
              />
            </n-form-item>
          </div>

          <div class="input-summary-card">
            <div class="input-summary-header">
              <div class="input-summary-title">输入摘要</div>
              <div class="input-summary-caption">这些信息会直接进入 AI 规划链路</div>
            </div>
            <div class="input-summary-grid">
              <div v-for="fact in planningFacts" :key="fact.label" class="input-summary-item">
                <div class="input-summary-label">{{ fact.label }}</div>
                <div class="input-summary-value">{{ fact.value }}</div>
              </div>
            </div>
          </div>

          <div class="form-submit-row">
            <p class="helper-text submit-hint">
              复杂目标可能需要几十秒完成推理与校验。生成期间右侧会持续显示进度状态和已等待时间，不会让你误以为页面卡住。
            </p>
            <div class="form-submit">
              <n-button
                v-if="loading && !taskPaused"
                attr-type="button"
                secondary
                :disabled="pauseActionPending || cancelRequested"
                @click="pauseActiveTask"
              >
                {{ pauseActionPending ? '正在暂停…' : '暂停任务' }}
              </n-button>
              <n-button
                v-if="loading && taskPaused"
                attr-type="button"
                secondary
                :disabled="pauseActionPending || cancelRequested"
                @click="resumeActiveTask"
              >
                {{ pauseActionPending ? '正在继续…' : '继续任务' }}
              </n-button>
              <n-button
                v-if="loading"
                attr-type="button"
                secondary
                :disabled="cancelRequested || pauseActionPending"
                @click="cancelActiveTask"
              >
                {{ cancelRequested ? '正在取消…' : '取消任务' }}
              </n-button>
              <n-button
                type="primary"
                attr-type="submit"
                :loading="loading && !taskPaused"
                :disabled="loading"
                block
              >
                {{ taskPaused ? '计划任务已暂停' : loading ? '正在生成计划…' : '生成学习计划' }}
              </n-button>
            </div>
          </div>
        </n-form>

        <p v-if="error" class="error-text">
          {{ error }}
        </p>
        <p v-if="notice" class="generation-note">
          {{ notice }}
        </p>
      </n-card>

      <n-card class="plan-result-wrapper" :bordered="true" hoverable>
        <template #header>
          <div class="result-header result-header-rich">
            <div>
              <div class="panel-kicker">结果面板</div>
              <div class="result-title">{{ plan ? '最新生成计划' : '生成结果预览' }}</div>
              <div class="result-subtitle">
                {{
                  loading
                    ? '系统正在组织目标拆解、阶段排布与每日任务，请稍候。'
                    : plan
                      ? '计划已生成，可以继续查看蓝图、资源、练习与执行明细。'
                      : '成功生成后，这里会展示学习蓝图、阶段拆解、周计划与每日任务。'
                }}
              </div>
            </div>
            <div class="result-status-card">
              <div class="result-status-label">当前状态</div>
              <div class="result-status-value">{{ loading ? '规划中' : plan ? '已生成' : '待开始' }}</div>
              <div v-if="loading" class="result-status-timer">已等待 {{ formattedElapsed }}</div>
              <div v-if="loading" class="result-status-stage">{{ currentLoadingStage.title }}</div>
              <div class="result-status-meta">{{ resultStatusHint }}</div>
            </div>
          </div>
        </template>

        <n-spin :show="loading && !!plan">
          <template v-if="loading && !plan">
            <div class="loading-state">
              <div class="loading-state-head">
                <div class="loading-badge">AI 正在生成</div>
                <div class="loading-timer">{{ formattedElapsed }}</div>
              </div>
              <div class="loading-title">{{ currentLoadingStage.title }}</div>
              <div class="loading-desc">{{ currentLoadingStage.desc }}</div>

              <div class="loading-stage-list">
                <div
                  v-for="item in loadingChecklist"
                  :key="item.title"
                  :class="['loading-stage-item', `loading-stage-item-${item.state}`]"
                >
                  <div class="loading-stage-index">{{ item.index }}</div>
                  <div class="loading-stage-copy">
                    <div class="loading-stage-title">{{ item.title }}</div>
                    <div class="loading-stage-desc">{{ item.desc }}</div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="plan">
            <div v-if="loading" class="inline-generation-banner">
              <div>
                <div class="inline-generation-title">正在生成新计划</div>
                <div class="inline-generation-desc">
                  已等待 {{ formattedElapsed }}，当前暂时展示上一份结果，新的规划完成后会自动替换。
                </div>
              </div>
              <div class="inline-generation-stage">{{ currentLoadingStage.title }}</div>
            </div>
            <PlanResultCard :plan="plan" />
          </template>

          <template v-else>
            <div class="result-empty-state">
              <div class="empty-illustration">01</div>
              <div class="empty-title">从左侧输入条件开始</div>
              <div class="empty-desc">
                先描述你的目标和最终产出，右侧会把整份学习计划、阶段节奏与每天任务一次性整理出来。
              </div>
            </div>
          </template>
        </n-spin>
      </n-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import {
  cancelAsyncTask,
  createPlanTask,
  getAsyncTask,
  getPlanById,
  getRecentPlans,
  pauseAsyncTask,
  resumeAsyncTask
} from '../api/plan';
import PlanResultCard from '../components/PlanResultCard.vue';
import { useAuthStore } from '../store/auth';

function normalizeLevel(value) {
  if (!value) return 'beginner';
  const normalized = String(value).trim().toLowerCase();
  if (['beginner', 'intermediate', 'advanced'].includes(normalized)) {
    return normalized;
  }
  if (normalized.includes('初') || normalized.includes('零')) {
    return 'beginner';
  }
  if (normalized.includes('进阶') || normalized.includes('高级')) {
    return 'advanced';
  }
  return 'intermediate';
}

function toOptionalText(value) {
  const text = value == null ? '' : String(value).trim();
  return text || null;
}

function parseConstraints(value) {
  if (!value) return [];
  return String(value)
    .split(/\r?\n|[；;]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatElapsed(totalSeconds) {
  const safeSeconds = Math.max(0, Number(totalSeconds || 0));
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  if (minutes <= 0) {
    return `${seconds} 秒`;
  }
  return `${minutes} 分 ${String(seconds).padStart(2, '0')} 秒`;
}

function safeToTime(value) {
  if (!value) return null;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? null : time;
}

function sleep(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

const { currentUser } = useAuthStore();

const form = reactive({
  goalText: '',
  durationWeeks: 8,
  hoursPerDay: 1,
  level: normalizeLevel(currentUser.value?.level),
  targetRole: '',
  preferredStyle: 'balanced',
  finalDeliverable: '',
  constraintsText: ''
});

const levelOptions = [
  { label: '零基础', value: 'beginner' },
  { label: '有一点基础', value: 'intermediate' },
  { label: '进阶', value: 'advanced' }
];

const preferredStyleOptions = [
  { label: '均衡推进', value: 'balanced' },
  { label: '偏实践', value: 'practice_first' },
  { label: '偏理论', value: 'theory_first' },
  { label: '题驱动', value: 'exercise_driven' }
];

const workflowSteps = [
  { index: '01', title: 'GoalAgent', desc: '拆解目标，生成学习蓝图与主题结构。' },
  { index: '02', title: 'SchedulerAgent', desc: '把主题分配到阶段与周计划节奏。' },
  { index: '03', title: 'PlanAgent', desc: '展开为每天可执行的任务与练习节奏。' },
  { index: '04', title: 'Validator', desc: '检查覆盖度、重复度和负载均衡。' }
];

const loadingStages = [
  {
    index: '01',
    title: '理解目标与约束',
    desc: '正在整理目标、周期、学习方式和最终产出。',
    minSeconds: 0
  },
  {
    index: '02',
    title: '编排阶段与周节奏',
    desc: '系统会把主题拆进阶段、周计划与执行顺序。',
    minSeconds: 8
  },
  {
    index: '03',
    title: '展开每日任务',
    desc: '正在生成每天的学习主题、练习节奏与重点提醒。',
    minSeconds: 18
  },
  {
    index: '04',
    title: '校验覆盖与负载',
    desc: '最后会检查重复度、覆盖度与整体强度是否合理。',
    minSeconds: 30
  }
];

const heroChips = ['学习蓝图', '阶段拆解', '周节奏', '每日任务', '资源联动', '练习闭环'];

const rules = {
  goalText: {
    required: true,
    message: '请先填写学习目标',
    trigger: ['input', 'blur']
  }
};

const formRef = ref(null);
const loading = ref(false);
const error = ref('');
const notice = ref('');
const plan = ref(null);
const generationStartedAt = ref(0);
const elapsedSeconds = ref(0);
const activeTaskId = ref(null);
const taskProgress = ref(0);
const cancelRequested = ref(false);
const taskPaused = ref(false);
const pauseActionPending = ref(false);

let generationTimer = null;
let generationSequence = 0;

const constraintCount = computed(() => parseConstraints(form.constraintsText).length);
const planningFacts = computed(() => [
  {
    label: '学习周期',
    value: `${form.durationWeeks || 0} 周`
  },
  {
    label: '每日投入',
    value: `${form.hoursPerDay || 0} 小时`
  },
  {
    label: '学习方式',
    value: preferredStyleOptions.find((item) => item.value === form.preferredStyle)?.label || '均衡推进'
  },
  {
    label: '约束数量',
    value: constraintCount.value ? `${constraintCount.value} 条` : '未填写'
  }
]);

const currentLoadingStage = computed(() => {
  const matched = [...loadingStages]
    .reverse()
    .find((item) => elapsedSeconds.value >= item.minSeconds);
  return matched || loadingStages[0];
});

const loadingChecklist = computed(() => {
  const currentIndex = loadingStages.findIndex(
    (item) => item.title === currentLoadingStage.value.title
  );
  return loadingStages.map((item, index) => {
    let state = 'pending';
    if (index < currentIndex) {
      state = 'done';
    } else if (index === currentIndex) {
      state = 'active';
    }
    return {
      ...item,
      state
    };
  });
});

const formattedElapsed = computed(() => formatElapsed(elapsedSeconds.value));

const resultStatusHint = computed(() => {
  if (taskPaused.value) {
    return `任务已暂停，Checkpoint 已保留；当前进度 ${taskProgress.value}%`;
  }
  if (loading.value) {
    return `任务进度 ${taskProgress.value}%，当前耗时 ${formattedElapsed.value}`;
  }
  if (plan.value) return '可以继续查看计划详情或进入历史页复盘';
  return '填写左侧信息后即可开始生成';
});

function clearGenerationClock() {
  if (generationTimer) {
    window.clearInterval(generationTimer);
    generationTimer = null;
  }
}

function newIdempotencyKey() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID();
  }
  return `plan-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function waitForPlanTask(taskId, sequence) {
  let expiresAt = Date.now() + 10 * 60 * 1000;
  let consecutiveQueryFailures = 0;

  while (sequence === generationSequence && Date.now() < expiresAt) {
    try {
      const task = await getAsyncTask(taskId);
      consecutiveQueryFailures = 0;
      taskProgress.value = Number(task.progress || 0);
      taskPaused.value = task.status === 'PAUSED';
      if (taskPaused.value) {
        expiresAt = Date.now() + 10 * 60 * 1000;
      }
      if (task.status === 'SUCCEEDED' && task.resultResourceId != null) {
        return getPlanById(task.resultResourceId);
      }
      if (task.status === 'FAILED') {
        throw new Error(task.errorCode || 'TASK_FAILED');
      }
      if (task.status === 'CANCELLED') {
        const cancelled = new Error('TASK_CANCELLED');
        cancelled.cancelled = true;
        throw cancelled;
      }
    } catch (queryError) {
      if (queryError?.cancelled || queryError?.message === 'TASK_CANCELLED') {
        throw queryError;
      }
      if (String(queryError?.message || '').startsWith('TASK_')) {
        throw queryError;
      }
      consecutiveQueryFailures += 1;
      if (consecutiveQueryFailures >= 3) {
        throw queryError;
      }
    }
    await sleep(1500);
  }
  throw new Error('TASK_POLL_TIMEOUT');
}

async function pauseActiveTask() {
  if (!activeTaskId.value || taskPaused.value || pauseActionPending.value) return;
  pauseActionPending.value = true;
  try {
    const task = await pauseAsyncTask(activeTaskId.value);
    taskPaused.value = task.status === 'PAUSED';
    notice.value = '任务已暂停，当前生成进度和工作流 Checkpoint 已安全保留。';
  } catch (pauseError) {
    error.value = buildPlanErrorMessage(pauseError);
  } finally {
    pauseActionPending.value = false;
  }
}

async function resumeActiveTask() {
  if (!activeTaskId.value || !taskPaused.value || pauseActionPending.value) return;
  pauseActionPending.value = true;
  try {
    const task = await resumeAsyncTask(activeTaskId.value);
    taskPaused.value = task.status === 'PAUSED';
    notice.value = '任务已继续，将从最近一次完整 Checkpoint 恢复。';
  } catch (resumeError) {
    error.value = buildPlanErrorMessage(resumeError);
  } finally {
    pauseActionPending.value = false;
  }
}

async function cancelActiveTask() {
  if (!activeTaskId.value || cancelRequested.value) return;
  cancelRequested.value = true;
  try {
    await cancelAsyncTask(activeTaskId.value);
    notice.value = '已提交取消请求；系统会关闭当前下游调用，并阻止后续业务写入。';
  } catch (cancelError) {
    cancelRequested.value = false;
    error.value = buildPlanErrorMessage(cancelError);
  }
}

function startGenerationClock() {
  generationStartedAt.value = Date.now();
  elapsedSeconds.value = 0;
  clearGenerationClock();
  generationTimer = window.setInterval(() => {
    elapsedSeconds.value = Math.max(
      0,
      Math.floor((Date.now() - generationStartedAt.value) / 1000)
    );
  }, 1000);
}

async function tryRecoverGeneratedPlan(startedAt, userId) {
  const retries = [0, 1800, 2500];

  for (const waitMs of retries) {
    if (waitMs > 0) {
      await sleep(waitMs);
    }

    const recentPlans = await getRecentPlans(6, userId);
    const matched = recentPlans.find((item) => {
      const createdTime = safeToTime(item.createdAt || item.updatedAt);
      return createdTime != null && createdTime >= startedAt - 15000;
    });

    if (matched?.id != null) {
      return getPlanById(matched.id, userId);
    }
  }

  return null;
}

function buildPlanErrorMessage(rawError) {
  const message = String(rawError?.message || '').trim();

  if (message.includes('504') || message.toLowerCase().includes('timeout')) {
    return '学习计划生成时间较长，本次请求已超时。你可以稍后到“历史计划”查看是否已生成成功，或重新发起一次。';
  }
  if (message.includes('500')) {
    return '学习计划生成过程中服务暂时不可用，请稍后重试。';
  }
  if (message.includes('Failed to fetch') || message.includes('NetworkError')) {
    return '当前网络连接不稳定，未能拿到生成结果。请稍后重试，或到“历史计划”查看是否已经生成成功。';
  }
  return '学习计划生成失败，请稍后重试。如等待时间较长，也可以先到“历史计划”中查看最新结果。';
}

async function onSubmit() {
  error.value = '';
  notice.value = '';

  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
  }

  loading.value = true;
  cancelRequested.value = false;
  taskPaused.value = false;
  pauseActionPending.value = false;
  taskProgress.value = 0;
  startGenerationClock();
  const startedAt = generationStartedAt.value;
  const userId = currentUser.value ? currentUser.value.id : null;
  const sequence = ++generationSequence;

  try {
    const task = await createPlanTask({
      goalText: form.goalText,
      durationWeeks: form.durationWeeks,
      hoursPerDay: form.hoursPerDay,
      level: form.level,
      targetRole: toOptionalText(form.targetRole),
      preferredStyle: toOptionalText(form.preferredStyle),
      constraints: parseConstraints(form.constraintsText),
      finalDeliverable: toOptionalText(form.finalDeliverable),
      userId
    }, newIdempotencyKey());
    activeTaskId.value = task.id;
    taskProgress.value = Number(task.progress || 0);
    const data = await waitForPlanTask(task.id, sequence);
    plan.value = data;
    notice.value =
      elapsedSeconds.value >= 12
        ? `学习计划已生成完成，本次耗时 ${formattedElapsed.value}。`
        : '学习计划已生成完成，可以继续查看执行细节。';
  } catch (e) {
    console.error(e);
    if (e?.cancelled || e?.message === 'TASK_CANCELLED') {
      notice.value = '计划生成任务已取消。';
      return;
    }
    try {
      const recoveredPlan = await tryRecoverGeneratedPlan(startedAt, userId);
      if (recoveredPlan) {
        plan.value = recoveredPlan;
        notice.value = `本次计划已经生成完成，但页面返回偏慢，系统已自动恢复最新结果。总耗时 ${formatElapsed(
          Math.max(
            elapsedSeconds.value,
            Math.floor((Date.now() - startedAt) / 1000)
          )
        )}。`;
        return;
      }
    } catch (recoveryError) {
      console.error(recoveryError);
    }

    error.value = buildPlanErrorMessage(e);
  } finally {
    if (sequence === generationSequence) {
      loading.value = false;
      activeTaskId.value = null;
      cancelRequested.value = false;
      taskPaused.value = false;
      pauseActionPending.value = false;
      clearGenerationClock();
    }
  }
}

onBeforeUnmount(() => {
  generationSequence += 1;
  clearGenerationClock();
});
</script>

<style scoped>
.plan-generator-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.generator-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.95fr);
  gap: 18px;
  padding: 24px 26px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.82), transparent 34%),
    linear-gradient(135deg, #12304a, #1f4f63 48%, #d8efe7 180%);
  color: #f8fafc;
  box-shadow: 0 22px 48px rgba(12, 37, 53, 0.18);
}

.hero-kicker,
.panel-kicker {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(232, 244, 248, 0.72);
}

.hero-title {
  margin: 8px 0 10px;
  font-size: clamp(30px, 3vw, 40px);
  line-height: 1.08;
  color: #f8fafc;
}

.hero-subtitle {
  margin: 0;
  max-width: 760px;
  font-size: 14px;
  line-height: 1.75;
  color: rgba(236, 245, 248, 0.9);
}

.hero-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.hero-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.14);
  font-size: 12px;
  color: #f7fafc;
}

.hero-side-panel {
  padding: 18px;
  border-radius: 22px;
  background: rgba(9, 24, 33, 0.24);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
}

.hero-panel-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 12px;
}

.hero-facts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.hero-fact-card {
  padding: 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
}

.hero-fact-label {
  font-size: 12px;
  color: rgba(236, 245, 248, 0.72);
}

.hero-fact-value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}

.hero-flow {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hero-flow-item {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 10px;
  align-items: flex-start;
}

.hero-flow-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.1);
  font-size: 12px;
  font-weight: 700;
}

.hero-flow-title {
  font-size: 13px;
  font-weight: 700;
  color: #ffffff;
}

.hero-flow-desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.55;
  color: rgba(236, 245, 248, 0.74);
}

.plan-generator-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1.45fr);
  gap: 16px;
}

.plan-generator-card,
.plan-result-wrapper {
  align-self: flex-start;
  border-radius: 24px;
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.07);
}

.panel-header,
.result-header-rich {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.panel-title,
.result-title {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: #102235;
}

.panel-badge {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  background: #edf7f2;
  color: #146c43;
  font-size: 12px;
  font-weight: 700;
}

.form-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.form-row-full {
  width: 100%;
}

.form-row-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.form-row-grid-4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.form-item-grow {
  flex: 1;
}

.input-summary-card {
  margin-top: 8px;
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f9fbfb, #f2f7f5);
  border: 1px solid rgba(19, 84, 90, 0.1);
}

.input-summary-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 10px;
}

.input-summary-title {
  font-size: 14px;
  font-weight: 700;
  color: #143349;
}

.input-summary-caption {
  font-size: 12px;
  color: #6a7f8f;
}

.input-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.input-summary-item {
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid rgba(20, 51, 73, 0.08);
}

.input-summary-label {
  font-size: 12px;
  color: #738496;
}

.input-summary-value {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
  color: #102235;
}

.form-submit-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.submit-hint {
  margin: 0;
  line-height: 1.75;
}

.form-submit {
  width: 196px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.generation-note {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, #eff8f3, #e8f4ee);
  border: 1px solid rgba(20, 108, 67, 0.16);
  color: #16603b;
  font-size: 13px;
  line-height: 1.7;
}

.result-subtitle {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #67798a;
}

.result-status-card {
  min-width: 184px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f4f8fb, #eef6f3);
  border: 1px solid rgba(16, 34, 53, 0.08);
}

.result-status-label {
  font-size: 12px;
  color: #6e8091;
}

.result-status-value {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
  color: #102235;
}

.result-status-timer {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #174566;
}

.result-status-stage {
  margin-top: 6px;
  font-size: 12px;
  color: #1f5f68;
}

.result-status-meta {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #65798a;
}

.loading-state {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 26px 18px;
  border-radius: 20px;
  background:
    radial-gradient(circle at top left, rgba(219, 234, 254, 0.72), transparent 34%),
    linear-gradient(180deg, #fbfdff, #f5f9ff);
}

.loading-state-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.loading-badge {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.loading-timer {
  font-size: 18px;
  font-weight: 700;
  color: #102235;
}

.loading-title {
  font-size: 22px;
  font-weight: 700;
  color: #102235;
}

.loading-desc {
  font-size: 13px;
  line-height: 1.8;
  color: #64748b;
}

.loading-stage-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.loading-stage-item {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.76);
}

.loading-stage-item-active {
  border-color: rgba(37, 99, 235, 0.24);
  background: linear-gradient(180deg, #f7fbff, #eef5ff);
  box-shadow: 0 12px 24px rgba(59, 130, 246, 0.08);
}

.loading-stage-item-done {
  border-color: rgba(34, 197, 94, 0.2);
  background: linear-gradient(180deg, #f7fcf8, #eef8f1);
}

.loading-stage-index {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: #eaf1f5;
  font-size: 12px;
  font-weight: 800;
  color: #173850;
}

.loading-stage-item-active .loading-stage-index {
  background: linear-gradient(135deg, #2563eb, #22c55e);
  color: #ffffff;
}

.loading-stage-item-done .loading-stage-index {
  background: #dcfce7;
  color: #166534;
}

.loading-stage-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.loading-stage-title {
  font-size: 14px;
  font-weight: 700;
  color: #102235;
}

.loading-stage-desc {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.inline-generation-banner {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f8fbff, #f1f7ff);
  border: 1px solid rgba(59, 130, 246, 0.18);
}

.inline-generation-title {
  font-size: 14px;
  font-weight: 700;
  color: #102235;
}

.inline-generation-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: #607483;
}

.inline-generation-stage {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.result-empty-state {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 28px 18px;
  text-align: center;
}

.empty-illustration {
  width: 72px;
  height: 72px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 24px;
  background: linear-gradient(135deg, #143349, #2f6b72);
  color: #ffffff;
  font-size: 24px;
  font-weight: 800;
}

.empty-title {
  font-size: 18px;
  font-weight: 700;
  color: #102235;
}

.empty-desc {
  max-width: 420px;
  font-size: 13px;
  line-height: 1.8;
  color: #67798a;
}

@media (max-width: 1100px) {
  .generator-hero,
  .plan-generator-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-side-panel {
    order: 2;
  }
}

@media (max-width: 820px) {
  .form-row-grid,
  .form-row-grid-4,
  .input-summary-grid,
  .hero-facts-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .form-submit-row,
  .panel-header,
  .result-header-rich,
  .input-summary-header,
  .loading-state-head,
  .inline-generation-banner {
    flex-direction: column;
    align-items: stretch;
  }

  .form-submit,
  .result-status-card {
    width: 100%;
  }

  .loading-stage-item {
    grid-template-columns: 1fr;
  }

  .generator-hero {
    padding: 20px 18px;
    border-radius: 22px;
  }
}
</style>
