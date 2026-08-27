<template>
  <section class="lf-records" aria-labelledby="record-list-title">
    <div class="lf-records__head">
      <div><span>Practice archive</span><h2 id="record-list-title">练习记录</h2><p>按学习日聚合，逐题查看答案、AI 判断和下一步建议。</p></div>
      <n-tag round>{{ total }} 条结果</n-tag>
    </div>

    <div v-if="loading" class="lf-records__loading"><n-skeleton text :repeat="8" /></div>
    <n-empty v-else-if="!groups.length" description="没有符合当前筛选条件的记录">
      <template #extra><n-button size="small" @click="$emit('clear-filters')">清除筛选</n-button></template>
    </n-empty>
    <div v-else class="lf-record-group-list">
      <article v-for="group in groups" :key="group.key" class="lf-record-group">
        <header class="lf-record-group__head">
          <div>
            <span>{{ group.dayDate || '日期待定' }}</span>
            <h3>{{ group.dayTitle || '未命名学习日' }}</h3>
            <p>{{ group.planTitle || '未命名计划' }} · {{ group.records.length }} 道练习</p>
          </div>
          <div class="lf-record-group__actions">
            <n-tag v-if="reviewCount(group)" size="small" type="warning" round>{{ reviewCount(group) }} 道待复习</n-tag>
            <n-button v-if="group.planId && group.dayId" size="small" secondary @click="$emit('open-day', group)">
              <ExternalLink :size="14" />返回学习日
            </n-button>
            <n-button v-if="group.dayId" size="small" tertiary type="error" :loading="deletingDays[String(group.dayId)]" @click="$emit('delete-day', group)">
              清空本日
            </n-button>
          </div>
        </header>

        <div class="lf-record-list">
          <section v-for="record in group.records" :key="record.id" class="lf-record-card" :class="{ 'needs-review': isNeedsReview(record) }">
            <div class="lf-record-card__top">
              <div class="lf-record-card__question"><span v-if="isNeedsReview(record)">重点复习</span><h4>{{ record.question || '未命名练习' }}</h4></div>
              <div class="lf-record-card__score">
                <n-tag v-if="record.aiScore != null" :type="scoreTagType(record.aiScore)" round>得分 {{ record.aiScore }}</n-tag>
                <n-tag v-else round>未评分</n-tag>
                <small v-if="record.aiMistakeType">{{ formatMistakeType(record.aiMistakeType) }}</small>
              </div>
            </div>
            <div class="lf-record-card__meta">
              <n-tag v-if="record.difficulty" size="tiny">{{ record.difficulty }}</n-tag>
              <n-tag v-if="record.skillFocus" size="tiny" type="info">{{ record.skillFocus }}</n-tag>
              <span v-if="record.createdAt">{{ formatDateTime(record.createdAt) }}</span>
            </div>

            <div class="lf-answer-grid">
              <div class="lf-answer-panel"><span>你的答案</span><p>{{ record.userAnswer || '暂无作答内容' }}</p></div>
              <div v-if="record.aiFeedback || record.aiNextRecommendation" class="lf-answer-panel lf-answer-panel--ai">
                <span>AI 反馈</span><p>{{ record.aiFeedback || '暂无反馈' }}</p><small v-if="record.aiNextRecommendation">下一步：{{ record.aiNextRecommendation }}</small>
              </div>
            </div>

            <n-collapse-transition :show="expandedIds.has(String(record.id))">
              <div class="lf-reference-answer"><p><strong>参考答案：</strong>{{ record.referenceAnswer || '暂无参考答案' }}</p><p v-if="record.explanation"><strong>题目讲解：</strong>{{ record.explanation }}</p></div>
            </n-collapse-transition>
            <footer class="lf-record-card__footer">
              <n-button text size="small" @click="toggleExpanded(record.id)">{{ expandedIds.has(String(record.id)) ? '收起参考答案' : '查看参考答案' }}</n-button>
              <n-space size="small">
                <n-button size="small" secondary :disabled="reviewed[String(record.id)]" :loading="reviewing[String(record.id)]" @click="$emit('review', record)">
                  <CheckCheck :size="15" />{{ reviewed[String(record.id)] ? '本次已复习' : '标记已复习' }}
                </n-button>
                <n-button size="small" quaternary type="error" :loading="deletingRecords[String(record.id)]" @click="$emit('delete-record', record)">
                  <Trash2 :size="14" />删除
                </n-button>
              </n-space>
            </footer>
          </section>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { CheckCheck, ExternalLink, Trash2 } from 'lucide-vue-next';
import { formatMistakeType, isNeedsReview, scoreTagType } from '../../../utils/exercise';
import type { ExerciseRecord, ReviewGroup } from '../types';

defineProps<{
  groups: ReviewGroup[];
  total: number;
  loading: boolean;
  deletingRecords: Record<string, boolean>;
  deletingDays: Record<string, boolean>;
  reviewing: Record<string, boolean>;
  reviewed: Record<string, boolean>;
}>();
defineEmits<{
  (event: 'clear-filters'): void;
  (event: 'open-day' | 'delete-day', group: ReviewGroup): void;
  (event: 'review' | 'delete-record', record: ExerciseRecord): void;
}>();

const expandedIds = ref(new Set<string>());
function toggleExpanded(id: number | string) {
  const key = String(id);
  const next = new Set(expandedIds.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedIds.value = next;
}
function reviewCount(group: ReviewGroup) { return group.records.filter((record) => isNeedsReview(record)).length; }
function formatDateTime(value: string) { return value.replace('T', ' ').replace(/Z$/, ''); }
</script>

<style scoped>
.lf-records { display: grid; gap: 17px; padding: 20px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 22px; background: var(--lf-surface, #fff); }.lf-records__head, .lf-record-group__head, .lf-record-card__top, .lf-record-card__footer { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }.lf-records__head > div > span { color: var(--lf-brand-700, #147a73); font-size: 10px; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }.lf-records__head h2 { margin: 4px 0 0; color: var(--lf-text, #17313d); font-size: 20px; }.lf-records__head p { margin: 4px 0 0; color: var(--lf-text-muted, #62737b); font-size: 12px; }
.lf-record-group-list, .lf-record-list { display: grid; gap: 14px; }.lf-record-group { display: grid; gap: 13px; padding: 15px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 17px; background: var(--lf-surface-soft, #f7f9f8); }.lf-record-group__head { padding-bottom: 12px; border-bottom: 1px dashed var(--lf-border-strong, #cbd7da); }.lf-record-group__head span { color: var(--lf-brand-700, #147a73); font-size: 10px; font-weight: 800; }.lf-record-group__head h3 { margin: 3px 0 0; color: var(--lf-text, #17313d); font-size: 17px; }.lf-record-group__head p { margin: 3px 0 0; color: var(--lf-text-muted, #62737b); font-size: 11px; }.lf-record-group__actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }
.lf-record-card { display: grid; gap: 12px; padding: 16px; border: 1px solid var(--lf-border, #e3e9ec); border-left: 3px solid transparent; border-radius: 14px; background: #fff; }.lf-record-card.needs-review { border-left-color: #d08a32; }.lf-record-card__question > span { color: #a06018; font-size: 9px; font-weight: 850; letter-spacing: .1em; text-transform: uppercase; }.lf-record-card h4 { max-width: 760px; margin: 4px 0 0; color: var(--lf-text, #17313d); font-size: 15px; line-height: 1.6; }.lf-record-card__score { display: grid; justify-items: end; gap: 4px; }.lf-record-card__score small { color: #a06018; font-size: 10px; }.lf-record-card__meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; color: var(--lf-text-muted, #62737b); font-size: 10px; }
.lf-answer-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }.lf-answer-panel { padding: 12px; border-radius: 12px; background: var(--lf-surface-soft, #f7f9f8); }.lf-answer-panel--ai { background: rgba(42,157,143,.075); }.lf-answer-panel > span { color: var(--lf-text-muted, #62737b); font-size: 10px; font-weight: 800; }.lf-answer-panel p, .lf-reference-answer p { margin: 5px 0 0; color: var(--lf-text, #334b54); font-size: 12px; line-height: 1.65; white-space: pre-wrap; }.lf-answer-panel small { display: block; margin-top: 7px; color: var(--lf-brand-700, #147a73); font-size: 10px; }.lf-reference-answer { padding: 12px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 12px; background: #fbfcfc; }.lf-record-card__footer { align-items: center; padding-top: 10px; border-top: 1px solid var(--lf-border, #e3e9ec); }
@media (max-width: 760px) { .lf-record-group__head, .lf-record-card__top { flex-direction: column; }.lf-record-group__actions { justify-content: flex-start; }.lf-answer-grid { grid-template-columns: 1fr; }.lf-record-card__score { display: flex; align-items: center; }.lf-record-card__footer { align-items: stretch; flex-direction: column; }.lf-record-card__footer :deep(.n-space) { width: 100%; }.lf-record-card__footer :deep(.n-space .n-button) { flex: 1; } }
</style>
