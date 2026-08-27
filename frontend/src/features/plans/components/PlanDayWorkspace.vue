<template>
  <section class="lf-day-workspace" aria-labelledby="day-workspace-title">
    <n-empty v-if="!day" description="选择一个学习日后开始执行" />
    <template v-else>
      <header class="lf-day-workspace__head">
        <div>
          <div class="lf-day-workspace__date"><CalendarDays :size="15" />{{ day.date || '日期待定' }}</div>
          <h2 id="day-workspace-title">{{ day.title || '今日学习任务' }}</h2>
          <p>先完成核心任务，再按需获取资源、生成练习与 AI 评测。</p>
        </div>
        <n-tag :type="planStatusTag(day.status)" round>{{ planStatusText(day.status) }}</n-tag>
      </header>

      <div class="lf-action-bar" aria-label="今日计划操作">
        <n-button
          v-if="day.status !== 'completed'"
          type="primary"
          :loading="statusState?.saving"
          @click="$emit('complete', day)"
        >
          <CheckCircle2 :size="17" />完成打卡
        </n-button>
        <n-button v-else secondary :loading="statusState?.saving" @click="$emit('undo-complete', day)">
          <RotateCcw :size="16" />撤销完成
        </n-button>
        <n-button secondary :loading="refineState?.loading" @click="$emit('refine', day)">
          <WandSparkles :size="16" />细化任务
        </n-button>
        <n-button
          v-if="day.status !== 'completed'"
          tertiary
          type="warning"
          :loading="replanState?.loading"
          @click="$emit('replan', day)"
        >
          <CalendarClock :size="16" />顺延重排
        </n-button>
      </div>

      <n-alert v-if="statusState?.error || refineState?.error || replanState?.error" type="error" :show-icon="false">
        {{ statusState?.error || refineState?.error || replanState?.error }}
      </n-alert>

      <section class="lf-task-block">
        <div class="lf-section-title">
          <div><span>今日路径</span><h3>核心任务</h3></div>
          <n-tag size="small" round>{{ day.tasks?.length || 0 }} 项</n-tag>
        </div>
        <ol v-if="day.tasks?.length" class="lf-task-list">
          <li v-for="(task, index) in day.tasks" :key="`${day.id}-${index}`">
            <span>{{ index + 1 }}</span><p>{{ task }}</p>
          </li>
        </ol>
        <n-empty v-else description="尚未拆出具体任务，可点击“细化任务”" size="small" />
      </section>

      <PlanResourcePanel
        eyebrow="今日材料"
        title="与今日任务匹配的资源"
        description="仅在需要时加载，避免资源列表干扰当前行动。"
        scope-label="日程任务"
        action-label="获取今日资源"
        :items="resourceState?.items || []"
        :loading="resourceState?.loading"
        :loaded-once="resourceState?.loadedOnce"
        :error="resourceState?.error"
        :feedback-state="feedbackState"
        :format-feedback="formatFeedback"
        @load="$emit('load-resources', day)"
        @upload="$emit('upload')"
        @feedback="(resource, value) => $emit('feedback', resource, value)"
      />

      <section class="lf-exercise-block">
        <div class="lf-section-title lf-section-title--action">
          <div><span>理解校验</span><h3>练习与 AI 评测</h3></div>
          <n-button secondary size="small" :loading="exerciseState?.loading" @click="$emit('load-exercises', day)">
            <BrainCircuit :size="16" />{{ exerciseState?.loadedOnce ? '重新生成' : '生成练习题' }}
          </n-button>
        </div>
        <n-alert v-if="exerciseState?.error" type="error" :show-icon="false">{{ exerciseState.error }}</n-alert>
        <div v-else-if="exerciseState?.loading" class="lf-exercise-loading"><n-skeleton text :repeat="4" /></div>
        <n-empty v-else-if="exerciseState?.loadedOnce && !exerciseState?.items?.length" description="暂未生成练习题，请稍后重试" />
        <div v-else-if="exerciseState?.items?.length" class="lf-exercise-list">
          <article v-for="(exercise, index) in exerciseState.items" :key="index" class="lf-exercise-card">
            <div class="lf-exercise-card__head">
              <span>练习 {{ index + 1 }}</span>
              <div><n-tag v-if="exercise.difficulty" size="tiny">{{ exercise.difficulty }}</n-tag><n-tag v-if="exercise.skillFocus" size="tiny" type="info">{{ exercise.skillFocus }}</n-tag></div>
            </div>
            <h4>{{ exercise.question }}</h4>
            <n-input
              type="textarea"
              :value="exerciseState.answers?.[index] || ''"
              :autosize="{ minRows: 3, maxRows: 7 }"
              placeholder="写下你的答案；提交后将完成 AI 评测并保存记录。"
              @update:value="$emit('update-answer', day.id, index, $event)"
            />
            <div class="lf-exercise-card__actions">
              <n-button type="primary" size="small" :loading="isSubmitting(day.id, index)" @click="$emit('submit-answer', day, index)">
                评测并保存
              </n-button>
              <n-button text size="small" @click="toggleReference(index)">
                {{ openReferences.has(index) ? '收起参考答案' : '查看参考答案' }}
              </n-button>
            </div>
            <n-collapse-transition :show="openReferences.has(index)">
              <div class="lf-reference">
                <p><strong>参考答案：</strong>{{ exercise.answer }}</p>
                <p v-if="exercise.explanation"><strong>讲解：</strong>{{ exercise.explanation }}</p>
              </div>
            </n-collapse-transition>
            <div v-if="getResult(day.id, index)" class="lf-evaluation">
              <div><n-tag v-if="getResult(day.id, index)?.score != null" :type="scoreTag(getResult(day.id, index)?.score)" round>得分 {{ getResult(day.id, index)?.score }}</n-tag><strong>{{ formatMistake(getResult(day.id, index)?.mistakeType) }}</strong></div>
              <p>{{ getResult(day.id, index)?.feedback }}</p>
              <small v-if="getResult(day.id, index)?.nextRecommendation">下一步：{{ getResult(day.id, index)?.nextRecommendation }}</small>
            </div>
            <n-alert v-if="exerciseState.lastSubmittedIndex === index && exerciseState.saveError" type="error" :show-icon="false">{{ exerciseState.saveError }}</n-alert>
            <n-alert v-if="exerciseState.lastSubmittedIndex === index && exerciseState.saveSuccessMessage" type="success" :show-icon="false">{{ exerciseState.saveSuccessMessage }}</n-alert>
          </article>
        </div>
        <div v-else class="lf-exercise-placeholder"><BrainCircuit :size="24" /><p>完成今日任务后，用几道题检查是否真正掌握。</p></div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { BrainCircuit, CalendarClock, CalendarDays, CheckCircle2, RotateCcw, WandSparkles } from 'lucide-vue-next';
import type { PlanDay } from '../composables/usePlanHistory';
import { planStatusTag, planStatusText } from '../utils/planHistory';
import PlanResourcePanel from './PlanResourcePanel.vue';

type AsyncState = { loading?: boolean; saving?: boolean; error?: string };
type ResourceItem = Record<string, unknown> & { id?: number | string; url?: string };
type ExerciseItem = Record<string, unknown> & {
  question?: string;
  answer?: string;
  explanation?: string;
  difficulty?: string;
  skillFocus?: string;
};
type ExerciseResult = Record<string, unknown> & {
  score?: number;
  mistakeType?: string;
  feedback?: string;
  nextRecommendation?: string;
};
type ResourceState = AsyncState & { items?: ResourceItem[]; loadedOnce?: boolean };
type ExerciseState = AsyncState & {
  items?: ExerciseItem[];
  answers?: string[];
  loadedOnce?: boolean;
  lastSubmittedIndex?: number | null;
  saveError?: string;
  saveSuccessMessage?: string;
};

withDefaults(defineProps<{
  day: PlanDay | null;
  statusState?: AsyncState;
  refineState?: AsyncState;
  replanState?: AsyncState;
  resourceState?: ResourceState;
  exerciseState?: ExerciseState;
  feedbackState: (id: number | string) => { loading: boolean; value?: string | null };
  formatFeedback: (value?: string | null) => string;
  getResult: (dayId: number | string, index: number) => ExerciseResult | null;
  isSubmitting: (dayId: number | string, index: number) => boolean;
  scoreTag: (score?: number) => 'default' | 'success' | 'warning' | 'error' | 'info';
  formatMistake: (type?: string) => string;
}>(), {
  statusState: undefined,
  refineState: undefined,
  replanState: undefined,
  resourceState: undefined,
  exerciseState: undefined
});

defineEmits<{
  (event: 'complete' | 'undo-complete' | 'refine' | 'load-resources' | 'load-exercises', day: PlanDay): void;
  (event: 'replan', day: PlanDay): void;
  (event: 'upload'): void;
  (event: 'feedback', resource: ResourceItem, value: 'helpful' | 'invalid'): void;
  (event: 'update-answer', dayId: number | string, index: number, value: string): void;
  (event: 'submit-answer', day: PlanDay, index: number): void;
}>();

const openReferences = ref(new Set<number>());
function toggleReference(index: number) {
  const next = new Set(openReferences.value);
  if (next.has(index)) next.delete(index);
  else next.add(index);
  openReferences.value = next;
}
</script>

<style scoped>
.lf-day-workspace { display: grid; gap: 18px; }.lf-day-workspace__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 22px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 20px; background: linear-gradient(135deg, rgba(42,157,143,.1), rgba(255,255,255,.96) 54%, rgba(231,173,95,.1)); }
.lf-day-workspace__date { display: flex; align-items: center; gap: 6px; color: var(--lf-brand-700, #147a73); font-size: 12px; font-weight: 800; }.lf-day-workspace h2 { margin: 7px 0 4px; color: var(--lf-text, #17313d); font-size: clamp(22px, 3vw, 30px); }.lf-day-workspace__head p { margin: 0; color: var(--lf-text-muted, #62737b); font-size: 13px; }
.lf-action-bar { display: flex; flex-wrap: wrap; gap: 9px; padding: 12px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 15px; background: var(--lf-surface, #fff); }
.lf-task-block, .lf-exercise-block { display: grid; gap: 15px; padding: 20px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 20px; background: var(--lf-surface, #fff); }.lf-section-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.lf-section-title span { color: var(--lf-brand-700, #147a73); font-size: 11px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }.lf-section-title h3 { margin: 3px 0 0; color: var(--lf-text, #17313d); font-size: 18px; }
.lf-task-list { display: grid; gap: 9px; margin: 0; padding: 0; list-style: none; }.lf-task-list li { display: grid; grid-template-columns: 30px 1fr; align-items: start; gap: 11px; padding: 12px; border-radius: 13px; background: var(--lf-surface-soft, #f7f9f8); }.lf-task-list li > span { display: grid; place-items: center; width: 28px; height: 28px; color: var(--lf-brand-800, #0d625d); font-size: 11px; font-weight: 800; border-radius: 9px; background: rgba(42,157,143,.12); }.lf-task-list p { margin: 4px 0 0; color: var(--lf-text, #334b54); font-size: 13px; line-height: 1.6; }
.lf-exercise-list { display: grid; gap: 14px; }.lf-exercise-card { display: grid; gap: 12px; padding: 16px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 15px; background: var(--lf-surface-soft, #f7f9f8); }.lf-exercise-card__head, .lf-exercise-card__actions, .lf-evaluation > div { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }.lf-exercise-card__head > span { color: var(--lf-brand-700, #147a73); font-size: 11px; font-weight: 800; }.lf-exercise-card__head > div { display: flex; gap: 6px; }.lf-exercise-card h4 { margin: 0; color: var(--lf-text, #17313d); font-size: 15px; line-height: 1.6; }
.lf-reference, .lf-evaluation { padding: 13px; border-radius: 12px; background: #fff; }.lf-reference p, .lf-evaluation p { margin: 0 0 7px; font-size: 12px; line-height: 1.65; }.lf-reference p:last-child, .lf-evaluation p:last-child { margin-bottom: 0; }.lf-evaluation { display: grid; gap: 8px; border-left: 3px solid var(--lf-brand-500, #2a9d8f); }.lf-evaluation small { color: var(--lf-text-muted, #62737b); }
.lf-exercise-placeholder { display: grid; place-items: center; gap: 6px; padding: 28px; color: var(--lf-text-muted, #62737b); text-align: center; border: 1px dashed var(--lf-border-strong, #cbd7da); border-radius: 14px; }.lf-exercise-placeholder p { margin: 0; font-size: 12px; }
@media (max-width: 640px) { .lf-day-workspace__head { flex-direction: column; }.lf-action-bar :deep(.n-button) { flex: 1 1 calc(50% - 9px); }.lf-section-title--action { align-items: stretch; flex-direction: column; } }
</style>
