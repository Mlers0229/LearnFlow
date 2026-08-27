<template>
  <aside class="lf-chat-context" aria-labelledby="chat-context-title">
    <div class="lf-chat-context__head">
      <div>
        <span>Learning context</span>
        <h2 id="chat-context-title">学习上下文</h2>
      </div>
      <n-switch :value="enabled" aria-label="附加学习上下文" @update:value="$emit('update:enabled', $event)" />
    </div>
    <p class="lf-chat-context__intro">选择计划与学习日，让回答围绕真实任务和已推荐资源展开。</p>

    <n-alert v-if="error" type="error" :show-icon="false">{{ error }}</n-alert>
    <div class="lf-chat-context__fields" :class="{ 'is-disabled': !enabled }">
      <label>
        <span>学习计划</span>
        <n-select
          :value="selectedPlanId"
          :options="planOptions"
          :loading="loading"
          placeholder="暂无近期计划"
          :disabled="!enabled || !planOptions.length"
          @update:value="$emit('select-plan', $event)"
        />
      </label>
      <label>
        <span>当前学习日</span>
        <n-select
          :value="selectedDayId"
          :options="dayOptions"
          :loading="loading"
          placeholder="请选择学习日"
          :disabled="!enabled || !dayOptions.length"
          @update:value="$emit('select-day', $event)"
        />
      </label>
    </div>

    <div v-if="enabled && currentDay" class="lf-chat-context__day">
      <div>
        <CalendarDays :size="16" />
        <span>{{ currentDay.date || '未排期' }}</span>
      </div>
      <strong>{{ currentDay.title || '学习任务' }}</strong>
      <ul v-if="currentDay.tasks?.length">
        <li v-for="task in currentDay.tasks.slice(0, 4)" :key="task">{{ task }}</li>
      </ul>
      <n-button text size="small" @click="$emit('open-day')"><ArrowUpRight :size="14" />打开对应学习日</n-button>
    </div>

    <div v-if="enabled" class="lf-chat-context__resources">
      <div class="lf-chat-context__section-title">
        <span><Library :size="14" />可引用资源</span>
        <small>{{ resources.length }} 项</small>
      </div>
      <n-skeleton v-if="resourcesLoading" text :repeat="3" />
      <n-alert v-else-if="resourcesError" type="warning" :show-icon="false">{{ resourcesError }}</n-alert>
      <div v-else-if="resources.length" class="lf-chat-context__resource-list">
        <a
          v-for="resource in resources.slice(0, 5)"
          :key="String(resource.id || resource.url || resource.title)"
          :href="resource.url || undefined"
          :target="resource.url ? '_blank' : undefined"
          rel="noopener noreferrer"
          :class="{ 'is-disabled': !resource.url }"
        >
          <span><strong>{{ resource.title || '学习资源' }}</strong><small>{{ resource.reason || resource.domain || '计划推荐资源' }}</small></span>
          <ExternalLink v-if="resource.url" :size="13" />
        </a>
      </div>
      <p v-else class="lf-chat-context__empty">当前学习日暂时没有推荐资源，AI 会只参考任务文本。</p>
    </div>

    <div class="lf-chat-context__privacy">
      <ShieldCheck :size="15" />
      <p><strong>发送范围清晰可控</strong><span>{{ enabled ? '当前计划、学习日与上方资源会随问题发送给 AI。' : '本次只发送对话内容，不附加计划信息。' }}</span></p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ArrowUpRight, CalendarDays, ExternalLink, Library, ShieldCheck } from 'lucide-vue-next';
import type { ContextDay, ContextResource } from '../types';

defineProps<{
  enabled: boolean;
  selectedPlanId: string | null;
  selectedDayId: string | null;
  planOptions: Array<{ label: string; value: string }>;
  dayOptions: Array<{ label: string; value: string }>;
  currentDay: ContextDay | null;
  resources: ContextResource[];
  loading: boolean;
  resourcesLoading: boolean;
  error: string;
  resourcesError: string;
}>();
defineEmits<{
  (event: 'update:enabled', value: boolean): void;
  (event: 'select-plan', value: string): void;
  (event: 'select-day', value: string): void;
  (event: 'open-day'): void;
}>();
</script>

<style scoped>
.lf-chat-context { position: sticky; top: 16px; display: grid; align-content: start; gap: 15px; min-width: 0; padding: 19px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 22px; background: var(--lf-surface, #fff); box-shadow: 0 14px 36px rgba(18,53,59,.06); }.lf-chat-context__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.lf-chat-context__head span { color: var(--lf-brand-700, #147a73); font-size: 9px; font-weight: 850; letter-spacing: .13em; text-transform: uppercase; }.lf-chat-context__head h2 { margin: 3px 0 0; color: var(--lf-text, #17313d); font-size: 19px; }.lf-chat-context__intro, .lf-chat-context__empty { margin: 0; color: var(--lf-text-muted, #62737b); font-size: 11px; line-height: 1.65; }.lf-chat-context__fields { display: grid; gap: 11px; transition: opacity .2s ease; }.lf-chat-context__fields.is-disabled { opacity: .52; }.lf-chat-context__fields label { display: grid; gap: 5px; }.lf-chat-context__fields label > span { color: var(--lf-text-muted, #62737b); font-size: 10px; font-weight: 750; }
.lf-chat-context__day { display: grid; gap: 8px; padding: 13px; border-radius: 14px; background: linear-gradient(145deg, #eef8f6, #fbf6ee); }.lf-chat-context__day > div { display: flex; align-items: center; gap: 6px; color: var(--lf-brand-700, #147a73); font-size: 10px; font-weight: 800; }.lf-chat-context__day > strong { color: var(--lf-text, #17313d); font-size: 13px; }.lf-chat-context__day ul { display: grid; gap: 4px; margin: 0; padding-left: 18px; color: var(--lf-text-muted, #62737b); font-size: 10px; line-height: 1.5; }.lf-chat-context__resources { display: grid; gap: 9px; }.lf-chat-context__section-title { display: flex; align-items: center; justify-content: space-between; color: var(--lf-text-muted, #62737b); font-size: 10px; font-weight: 800; }.lf-chat-context__section-title > span { display: flex; align-items: center; gap: 6px; }.lf-chat-context__resource-list { display: grid; gap: 7px; }.lf-chat-context__resource-list a { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; min-width: 0; padding: 10px; color: var(--lf-text, #17313d); text-decoration: none; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 11px; background: var(--lf-surface-soft, #f7f9f8); }.lf-chat-context__resource-list a:hover { border-color: var(--lf-brand-400, #54b0a6); }.lf-chat-context__resource-list a.is-disabled { pointer-events: none; }.lf-chat-context__resource-list a > span { display: grid; min-width: 0; }.lf-chat-context__resource-list strong { overflow: hidden; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.lf-chat-context__resource-list small { display: -webkit-box; overflow: hidden; color: var(--lf-text-muted, #62737b); font-size: 9px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.lf-chat-context__privacy { display: flex; align-items: flex-start; gap: 8px; padding-top: 12px; color: var(--lf-brand-700, #147a73); border-top: 1px solid var(--lf-border, #e3e9ec); }.lf-chat-context__privacy p { display: grid; gap: 3px; margin: 0; }.lf-chat-context__privacy strong { color: var(--lf-text, #17313d); font-size: 10px; }.lf-chat-context__privacy span { color: var(--lf-text-muted, #62737b); font-size: 9px; line-height: 1.5; }
@media (max-width: 1040px) { .lf-chat-context { position: static; } }
</style>
