<template>
  <div class="plan-generator-page">
    <section class="generator-hero">
      <div class="generator-hero-copy">
        <div class="hero-kicker">AI Study Planning Studio</div>
        <h1 class="title hero-title">把学习目标拆成真正能执行的节奏</h1>
        <p class="subtitle hero-subtitle">
          输入目标、周期、学习偏好和最终产出，LearnFlow 会把 GoalAgent、SchedulerAgent、PlanAgent 和 Validator 串成一条完整规划链路，生成一份适合落地执行的学习蓝图。
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
              生成后右侧会直接展示目标蓝图、阶段拆解、周节奏与每日任务，还可以继续加载资源和练习题。
            </p>
            <div class="form-submit">
              <n-button
                type="primary"
                attr-type="submit"
                :loading="loading"
                block
              >
                {{ loading ? '正在生成计划…' : '生成学习计划' }}
              </n-button>
            </div>
          </div>
        </n-form>

        <p v-if="error" class="error-text">
          {{ error }}
        </p>
      </n-card>

      <n-card class="plan-result-wrapper" :bordered="true" hoverable>
        <template #header>
          <div class="result-header result-header-rich">
            <div>
              <div class="panel-kicker">结果面板</div>
              <div class="result-title">{{ plan ? '最新生成计划' : '生成结果预览' }}</div>
              <div class="result-subtitle">
                {{ plan ? '计划已生成，可以继续查看蓝图、资源、练习与执行明细。' : '成功生成后，这里会展示学习蓝图、阶段拆解、周计划与每日任务。' }}
              </div>
            </div>
            <div class="result-status-card">
              <div class="result-status-label">当前状态</div>
              <div class="result-status-value">{{ loading ? '规划中' : plan ? '已生成' : '待开始' }}</div>
              <div class="result-status-meta">{{ resultStatusHint }}</div>
            </div>
          </div>
        </template>

        <n-spin :show="loading">
          <template v-if="plan">
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
import { computed, reactive, ref } from 'vue';
import { generatePlan } from '../api/plan';
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
const plan = ref(null);

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

const resultStatusHint = computed(() => {
  if (loading.value) return 'Agent 正在串联目标、阶段与日计划';
  if (plan.value) return '可以继续查看计划详情或进入历史页复盘';
  return '填写左侧信息后即可开始生成';
});

async function onSubmit() {
  error.value = '';
  plan.value = null;

  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
  }

  loading.value = true;

  try {
    const data = await generatePlan({
      goalText: form.goalText,
      durationWeeks: form.durationWeeks,
      hoursPerDay: form.hoursPerDay,
      level: form.level,
      targetRole: toOptionalText(form.targetRole),
      preferredStyle: toOptionalText(form.preferredStyle),
      constraints: parseConstraints(form.constraintsText),
      finalDeliverable: toOptionalText(form.finalDeliverable),
      userId: currentUser.value ? currentUser.value.id : null
    });
    plan.value = data;
  } catch (e) {
    console.error(e);
    error.value =
      '生成学习计划失败，请确认后端与 Agent 服务已启动（8080 & 8000），然后重试。';
  } finally {
    loading.value = false;
  }
}
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
  line-height: 1.7;
}

.form-submit {
  width: 196px;
  flex-shrink: 0;
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

.result-status-meta {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #65798a;
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
  .input-summary-header {
    flex-direction: column;
    align-items: stretch;
  }

  .form-submit,
  .result-status-card {
    width: 100%;
  }

  .generator-hero {
    padding: 20px 18px;
    border-radius: 22px;
  }
}
</style>