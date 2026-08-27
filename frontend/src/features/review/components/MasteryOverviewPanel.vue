<template>
  <section class="lf-mastery" aria-labelledby="mastery-title">
    <div class="lf-mastery__head">
      <div>
        <span>Mastery profile · {{ profiles[0]?.algorithmVersion || 'weighted-v1' }}</span>
        <h2 id="mastery-title">掌握度与薄弱知识点</h2>
        <p>画像只基于可追溯学习事件；低置信度结果不会被包装成确定结论。</p>
      </div>
      <n-button secondary :loading="recomputing" @click="$emit('recompute')">
        <RefreshCw :size="15" />按学习事件重算
      </n-button>
    </div>

    <n-alert v-if="error" type="error" :show-icon="false">{{ error }}</n-alert>
    <div v-else-if="loading" class="lf-mastery__loading"><n-skeleton text :repeat="5" /></div>
    <n-empty v-else-if="!profiles.length && !weakSkills.length" description="完成练习后，这里会形成掌握度画像" />
    <div v-else class="lf-mastery__layout">
      <div class="lf-mastery__profiles">
        <article v-for="profile in profiles.slice(0, 6)" :key="profile.knowledgeKey" class="lf-mastery-card">
          <div class="lf-mastery-card__top">
            <div><strong>{{ profile.displayName || profile.knowledgeKey }}</strong><small>{{ profile.sampleCount || 0 }} 个样本</small></div>
            <b>{{ percent(profile.masteryScore) }}%</b>
          </div>
          <n-progress type="line" :percentage="percent(profile.masteryScore)" :height="7" :show-indicator="false" />
          <div class="lf-mastery-card__confidence">
            <span>置信度 {{ percent(profile.confidence) }}%</span>
            <span>有效权重 {{ Number(profile.effectiveWeight || 0).toFixed(2) }}</span>
          </div>
          <div v-if="profile.evidence?.length" class="lf-mastery-card__evidence">
            <n-popover v-for="event in profile.evidence.slice(0, 3)" :key="event.eventId" trigger="hover">
              <template #trigger><n-tag size="tiny" round>{{ evidenceLabel(event.eventType) }}</n-tag></template>
              <div class="lf-evidence-popover"><strong>{{ event.summary || evidenceLabel(event.eventType) }}</strong><span>{{ event.occurredAt ? formatDateTime(event.occurredAt) : '时间未知' }}</span></div>
            </n-popover>
          </div>
        </article>
      </div>
      <aside class="lf-weak-skills">
        <div class="lf-weak-skills__title"><TriangleAlert :size="16" /><span>优先复习建议</span></div>
        <div v-if="weakSkills.length" class="lf-weak-skills__list">
          <button v-for="skill in weakSkills" :key="skill.name" type="button" @click="$emit('select-skill', skill.name)">
            <span><strong>{{ skill.name }}</strong><small>{{ skill.attempts }} 次练习 · {{ skill.needsReview }} 次待复习</small></span>
            <b>{{ skill.averageScore == null ? '--' : skill.averageScore }}</b>
          </button>
        </div>
        <p v-else>当前记录暂未发现明显薄弱知识点。</p>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { RefreshCw, TriangleAlert } from 'lucide-vue-next';
import type { MasteryProfile, WeakSkill } from '../types';

defineProps<{
  profiles: MasteryProfile[];
  weakSkills: WeakSkill[];
  loading: boolean;
  recomputing: boolean;
  error: string;
}>();
defineEmits<{ (event: 'recompute'): void; (event: 'select-skill', value: string): void }>();

function percent(value?: number) { return Math.max(0, Math.min(100, Math.round(Number(value || 0) * 100))); }
function formatDateTime(value: string) { return value.replace('T', ' ').replace(/Z$/, ''); }
function evidenceLabel(value?: string) {
  const labels: Record<string, string> = {
    PLAN_DAY_STARTED: '开始学习', PLAN_DAY_COMPLETED: '完成学习日', PLAN_DAY_DELAYED: '学习延期',
    PLAN_DAY_RESET: '状态重置', EXERCISE_ANSWERED: '练习作答', EXERCISE_REVIEWED: '已复习',
    RESOURCE_FEEDBACK_SUBMITTED: '资源反馈'
  };
  return value ? labels[value] || value : '学习事件';
}
</script>

<style scoped>
.lf-mastery { display: grid; gap: 17px; padding: 20px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 22px; background: var(--lf-surface, #fff); }.lf-mastery__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.lf-mastery__head span { color: var(--lf-brand-700, #147a73); font-size: 10px; font-weight: 850; letter-spacing: .11em; text-transform: uppercase; }.lf-mastery__head h2 { margin: 4px 0 0; color: var(--lf-text, #17313d); font-size: 20px; }.lf-mastery__head p { margin: 5px 0 0; color: var(--lf-text-muted, #62737b); font-size: 12px; }
.lf-mastery__layout { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(220px, .7fr); gap: 14px; }.lf-mastery__profiles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }.lf-mastery-card { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 14px; background: var(--lf-surface-soft, #f7f9f8); }.lf-mastery-card__top, .lf-mastery-card__confidence { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }.lf-mastery-card__top > div { display: grid; }.lf-mastery-card__top strong { color: var(--lf-text, #17313d); font-size: 13px; }.lf-mastery-card__top small, .lf-mastery-card__confidence { color: var(--lf-text-muted, #62737b); font-size: 10px; }.lf-mastery-card__top b { color: var(--lf-brand-700, #147a73); font-size: 19px; }.lf-mastery-card__evidence { display: flex; flex-wrap: wrap; gap: 5px; }
.lf-weak-skills { display: grid; align-content: start; gap: 11px; padding: 14px; border-radius: 15px; background: linear-gradient(150deg, rgba(248,235,214,.8), rgba(255,255,255,.9)); }.lf-weak-skills__title { display: flex; align-items: center; gap: 7px; color: #9a5b12; font-size: 12px; font-weight: 850; }.lf-weak-skills__list { display: grid; gap: 7px; }.lf-weak-skills button { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px; text-align: left; border: 1px solid rgba(184,128,54,.14); border-radius: 11px; background: rgba(255,255,255,.72); cursor: pointer; }.lf-weak-skills button:hover { border-color: rgba(184,128,54,.35); }.lf-weak-skills button span { display: grid; }.lf-weak-skills strong { color: var(--lf-text, #17313d); font-size: 12px; }.lf-weak-skills small, .lf-weak-skills p { color: var(--lf-text-muted, #62737b); font-size: 10px; }.lf-weak-skills b { color: #9a5b12; font-size: 16px; }.lf-evidence-popover { display: grid; max-width: 260px; gap: 4px; }.lf-evidence-popover span { color: #6b7280; font-size: 11px; }
@media (max-width: 900px) { .lf-mastery__layout { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .lf-mastery__head { align-items: stretch; flex-direction: column; }.lf-mastery__profiles { grid-template-columns: 1fr; } }
</style>
