<template>
  <section class="lf-plan-wizard" aria-labelledby="plan-wizard-title">
    <div class="lf-plan-wizard-head">
      <div>
        <div class="lf-eyebrow">Plan setup</div>
        <h2 id="plan-wizard-title">配置你的学习计划</h2>
        <p>按四步确认目标、时间、偏好和最终输入，生成过程中可随时暂停或取消。</p>
      </div>
      <span class="lf-draft-state"><CloudCheck :size="15" />草稿自动保存</span>
    </div>

    <n-steps :current="step" size="small" class="lf-plan-steps">
      <n-step title="目标" />
      <n-step title="时间" />
      <n-step title="偏好" />
      <n-step title="确认" />
    </n-steps>

    <n-alert v-if="validationError" type="error" :bordered="false" role="alert" class="lf-plan-form-alert">
      {{ validationError }}
    </n-alert>

    <div v-if="step === 1" class="lf-plan-step-panel">
      <div class="lf-plan-step-copy">
        <span>01 · 学习目标</span>
        <h3>先描述你真正想完成的结果</h3>
        <p>目标越具体，计划中的主题、任务和验收方式越容易落地。</p>
      </div>
      <n-form label-placement="top" size="large" @submit.prevent="nextStep">
        <n-form-item label="学习目标" required>
          <n-input
            :value="modelValue.goalText"
            type="textarea"
            placeholder="例如：8 周转向 Java 后端开发，独立完成并部署一个 Spring Boot 项目"
            :autosize="{ minRows: 5, maxRows: 8 }"
            :disabled="disabled"
            @update:value="updateField('goalText', $event)"
          />
        </n-form-item>
        <div class="lf-plan-field-grid">
          <n-form-item label="目标岗位或方向">
            <n-input
              :value="modelValue.targetRole"
              placeholder="例如：Java 后端工程师"
              :disabled="disabled"
              @update:value="updateField('targetRole', $event)"
            />
          </n-form-item>
          <n-form-item label="期望最终产出">
            <n-input
              :value="modelValue.finalDeliverable"
              placeholder="例如：完成一个可部署的小项目"
              :disabled="disabled"
              @update:value="updateField('finalDeliverable', $event)"
            />
          </n-form-item>
        </div>
      </n-form>
    </div>

    <div v-else-if="step === 2" class="lf-plan-step-panel">
      <div class="lf-plan-step-copy">
        <span>02 · 时间条件</span>
        <h3>给计划一个可持续的节奏</h3>
        <p>系统会用周期和每日投入估算总学习容量，并控制每天的任务负载。</p>
      </div>
      <div class="lf-plan-field-grid lf-plan-field-grid--cards">
        <n-form-item label="学习周期（周）" required>
          <n-input-number
            :value="modelValue.durationWeeks"
            :min="1"
            :max="52"
            :disabled="disabled"
            @update:value="updateNumber('durationWeeks', $event, 8)"
          />
        </n-form-item>
        <n-form-item label="每天投入（小时）" required>
          <n-input-number
            :value="modelValue.hoursPerDay"
            :min="1"
            :max="10"
            :disabled="disabled"
            @update:value="updateNumber('hoursPerDay', $event, 1)"
          />
        </n-form-item>
      </div>
      <div class="lf-capacity-card">
        <Clock3 :size="20" />
        <div><strong>预计总投入 {{ estimatedHours }} 小时</strong><span>系统会分配到阶段、周计划与每日任务中。</span></div>
      </div>
    </div>

    <div v-else-if="step === 3" class="lf-plan-step-panel">
      <div class="lf-plan-step-copy">
        <span>03 · 学习偏好</span>
        <h3>告诉 LearnFlow 怎样安排更适合你</h3>
        <p>基础水平决定起点，学习方式影响理论、实践与练习的比例。</p>
      </div>
      <div class="lf-plan-field-grid">
        <n-form-item label="当前基础水平">
          <n-select
            :value="modelValue.level"
            :options="levelOptions"
            :disabled="disabled"
            @update:value="updateField('level', $event)"
          />
        </n-form-item>
        <n-form-item label="偏好学习方式">
          <n-select
            :value="modelValue.preferredStyle"
            :options="styleOptions"
            :disabled="disabled"
            @update:value="updateField('preferredStyle', $event)"
          />
        </n-form-item>
      </div>
      <n-form-item label="学习约束（每行一条，可选）">
        <n-input
          :value="modelValue.constraintsText"
          type="textarea"
          placeholder="例如：周一到周五只能晚上学习&#10;周末可以安排项目实践&#10;希望多练习、少看长视频"
          :autosize="{ minRows: 4, maxRows: 7 }"
          :disabled="disabled"
          @update:value="updateField('constraintsText', $event)"
        />
      </n-form-item>
    </div>

    <div v-else class="lf-plan-step-panel">
      <div class="lf-plan-step-copy">
        <span>04 · 最终确认</span>
        <h3>确认输入后启动持久化规划任务</h3>
        <p>任务会生成唯一标识；即使页面响应较慢，也可以从历史计划恢复最终结果。</p>
      </div>
      <dl class="lf-plan-review">
        <div class="lf-plan-review--wide"><dt>学习目标</dt><dd>{{ modelValue.goalText }}</dd></div>
        <div><dt>周期</dt><dd>{{ modelValue.durationWeeks }} 周</dd></div>
        <div><dt>每天投入</dt><dd>{{ modelValue.hoursPerDay }} 小时</dd></div>
        <div><dt>基础水平</dt><dd>{{ levelLabel }}</dd></div>
        <div><dt>学习方式</dt><dd>{{ styleLabel }}</dd></div>
        <div><dt>目标方向</dt><dd>{{ modelValue.targetRole || '未指定' }}</dd></div>
        <div><dt>最终产出</dt><dd>{{ modelValue.finalDeliverable || '未指定' }}</dd></div>
        <div class="lf-plan-review--wide"><dt>学习约束</dt><dd>{{ constraintSummary }}</dd></div>
      </dl>
    </div>

    <div class="lf-plan-wizard-actions">
      <n-button v-if="step > 1" size="large" secondary :disabled="disabled" @click="previousStep">
        <template #icon><ArrowLeft /></template>上一步
      </n-button>
      <span v-else />
      <n-button v-if="step < 4" type="primary" size="large" :disabled="disabled" @click="nextStep">
        下一步<template #icon><ArrowRight /></template>
      </n-button>
      <n-button v-else type="primary" size="large" :disabled="disabled" @click="$emit('submit')">
        <template #icon><Sparkles /></template>{{ disabled ? '任务执行中' : '确认并生成计划' }}
      </n-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { z } from 'zod';
import { ArrowLeft, ArrowRight, Clock3, CloudCheck, Sparkles } from 'lucide-vue-next';
import type { PlanFormValue } from '../types';
import { parsePlanConstraints } from '../utils/planGeneration';

const props = defineProps<{ modelValue: PlanFormValue; disabled?: boolean }>();
const emit = defineEmits<{
  'update:modelValue': [value: PlanFormValue];
  submit: [];
}>();

const step = ref(1);
const validationError = ref('');
const levelOptions = [
  { label: '零基础', value: 'beginner' },
  { label: '有一定基础', value: 'intermediate' },
  { label: '进阶学习', value: 'advanced' }
];
const styleOptions = [
  { label: '均衡推进', value: 'balanced' },
  { label: '偏实践', value: 'practice_first' },
  { label: '偏理论', value: 'theory_first' },
  { label: '题目驱动', value: 'exercise_driven' }
];
const goalSchema = z.object({
  goalText: z.string().trim().min(8, '请用至少 8 个字符描述学习目标。').max(500, '学习目标不能超过 500 个字符。')
});

const estimatedHours = computed(() => props.modelValue.durationWeeks * 7 * props.modelValue.hoursPerDay);
const levelLabel = computed(() => levelOptions.find((item) => item.value === props.modelValue.level)?.label || '零基础');
const styleLabel = computed(() => styleOptions.find((item) => item.value === props.modelValue.preferredStyle)?.label || '均衡推进');
const constraintSummary = computed(() => {
  const constraints = parsePlanConstraints(props.modelValue.constraintsText);
  return constraints.length ? `${constraints.length} 条：${constraints.join('；')}` : '未填写额外约束';
});

function updateField<Key extends keyof PlanFormValue>(key: Key, value: PlanFormValue[Key]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value });
}

function updateNumber(key: 'durationWeeks' | 'hoursPerDay', value: number | null, fallback: number) {
  updateField(key, value ?? fallback);
}

function nextStep() {
  validationError.value = '';
  if (step.value === 1) {
    const result = goalSchema.safeParse(props.modelValue);
    if (!result.success) {
      validationError.value = result.error.issues[0]?.message || '请检查学习目标。';
      return;
    }
  }
  step.value = Math.min(4, step.value + 1);
}

function previousStep() {
  validationError.value = '';
  step.value = Math.max(1, step.value - 1);
}
</script>

<style scoped>
.lf-plan-wizard { padding: 28px; border: 1px solid var(--lf-border-subtle); border-radius: var(--lf-radius-xl); background: var(--lf-bg-surface); box-shadow: var(--lf-shadow-sm); }
.lf-plan-wizard-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.lf-plan-wizard-head h2 { margin: 7px 0 7px; color: var(--lf-text-strong); font-size: 25px; letter-spacing: -.03em; }
.lf-plan-wizard-head p { max-width: 620px; margin: 0; color: var(--lf-text-muted); font-size: 13px; line-height: 1.7; }
.lf-draft-state { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 6px; padding: 7px 10px; border-radius: var(--lf-radius-pill); background: var(--lf-brand-50); color: var(--lf-brand-800); font-size: 11px; font-weight: 700; }
.lf-plan-steps { margin: 28px 0 30px; }
.lf-plan-form-alert { margin-bottom: 20px; }
.lf-plan-step-panel { min-height: 360px; }
.lf-plan-step-copy { margin-bottom: 22px; }
.lf-plan-step-copy > span { color: var(--lf-brand-700); font-size: 11px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
.lf-plan-step-copy h3 { margin: 7px 0; color: var(--lf-text-strong); font-size: 19px; }
.lf-plan-step-copy p { margin: 0; color: var(--lf-text-muted); font-size: 13px; line-height: 1.65; }
.lf-plan-field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.lf-plan-field-grid--cards { padding: 22px; border-radius: var(--lf-radius-lg); background: var(--lf-bg-subtle); }
.lf-capacity-card { display: flex; align-items: center; gap: 12px; margin-top: 18px; padding: 16px 18px; border: 1px solid var(--lf-brand-100); border-radius: var(--lf-radius-lg); background: var(--lf-brand-50); color: var(--lf-brand-800); }
.lf-capacity-card div { display: flex; flex-direction: column; gap: 3px; }
.lf-capacity-card span { color: var(--lf-text-muted); font-size: 12px; }
.lf-plan-review { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 0; }
.lf-plan-review > div { padding: 14px 16px; border: 1px solid var(--lf-border-subtle); border-radius: var(--lf-radius-md); background: var(--lf-bg-subtle); }
.lf-plan-review--wide { grid-column: 1 / -1; }
.lf-plan-review dt { margin-bottom: 5px; color: var(--lf-text-subtle); font-size: 10px; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }
.lf-plan-review dd { margin: 0; color: var(--lf-text-strong); font-size: 13px; line-height: 1.6; }
.lf-plan-wizard-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 22px; border-top: 1px solid var(--lf-border-subtle); }
@media (max-width: 640px) {
  .lf-plan-wizard { padding: 20px 16px; }
  .lf-plan-wizard-head { flex-direction: column; gap: 12px; }
  .lf-plan-wizard-head h2 { font-size: 22px; }
  .lf-plan-field-grid, .lf-plan-review { grid-template-columns: 1fr; }
  .lf-plan-review--wide { grid-column: auto; }
  .lf-plan-step-panel { min-height: 0; }
  .lf-plan-wizard-actions :deep(.n-button) { flex: 1; }
}
</style>
