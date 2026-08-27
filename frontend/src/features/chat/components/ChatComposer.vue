<template>
  <section class="lf-chat-composer" aria-label="发送消息">
    <div class="lf-chat-composer__context">
      <span><Paperclip :size="13" />{{ contextLabel }}</span>
      <small>{{ loading ? '回答生成中，可随时停止' : 'Enter 发送 · Shift + Enter 换行' }}</small>
    </div>
    <n-input
      :value="modelValue"
      type="textarea"
      :autosize="{ minRows: 3, maxRows: 7 }"
      maxlength="6000"
      show-count
      placeholder="询问概念、代码、学习任务，或让 AI 帮你复盘……"
      :disabled="loading"
      @update:value="$emit('update:modelValue', $event)"
      @keydown.enter="handleEnter"
    />
    <div class="lf-chat-composer__bottom">
      <div class="lf-chat-composer__presets">
        <n-button v-for="preset in presets" :key="preset" size="tiny" quaternary :disabled="loading" @click="$emit('preset', preset)">
          {{ preset }}
        </n-button>
      </div>
      <div class="lf-chat-composer__actions">
        <n-button text size="small" :disabled="loading" @click="$emit('clear')">清空对话</n-button>
        <n-button v-if="loading" type="error" secondary @click="$emit('stop')"><Square :size="14" />停止生成</n-button>
        <n-button v-else type="primary" :disabled="!modelValue.trim()" @click="$emit('send')"><Send :size="15" />发送问题</n-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { Paperclip, Send, Square } from 'lucide-vue-next';

defineProps<{ modelValue: string; loading: boolean; contextLabel: string; presets: string[] }>();
const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
  (event: 'preset', value: string): void;
  (event: 'send'): void;
  (event: 'stop'): void;
  (event: 'clear'): void;
}>();

function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey || event.isComposing) return;
  event.preventDefault();
  emit('send');
}
</script>

<style scoped>
.lf-chat-composer { display: grid; gap: 10px; padding: 14px; border: 1px solid rgba(20,122,115,.16); border-radius: 18px; background: rgba(255,255,255,.96); box-shadow: 0 18px 44px rgba(18,53,59,.1); }.lf-chat-composer__context, .lf-chat-composer__bottom, .lf-chat-composer__actions, .lf-chat-composer__presets { display: flex; align-items: center; gap: 8px; }.lf-chat-composer__context { justify-content: space-between; color: var(--lf-brand-700, #147a73); font-size: 10px; font-weight: 750; }.lf-chat-composer__context > span { display: flex; align-items: center; gap: 5px; }.lf-chat-composer__context small { color: var(--lf-text-muted, #62737b); font-size: 9px; font-weight: 500; }.lf-chat-composer__bottom { align-items: flex-end; justify-content: space-between; }.lf-chat-composer__presets { flex-wrap: wrap; }.lf-chat-composer__actions { flex: 0 0 auto; }.lf-chat-composer :deep(.n-input) { --n-border-radius: 13px; --n-border: rgba(148,163,184,.3); --n-border-focus: rgba(20,122,115,.55); }.lf-chat-composer :deep(.n-input__textarea-el) { line-height: 1.65; }
@media (max-width: 760px) { .lf-chat-composer { position: fixed; z-index: 30; right: max(10px, env(safe-area-inset-right)); bottom: max(10px, env(safe-area-inset-bottom)); left: max(10px, env(safe-area-inset-left)); padding: 11px; border-radius: 16px; }.lf-chat-composer__context small, .lf-chat-composer__presets { display: none; }.lf-chat-composer__bottom { justify-content: flex-end; }.lf-chat-composer__actions { width: 100%; justify-content: flex-end; }.lf-chat-composer__actions :deep(.n-button:last-child) { flex: 1; }.lf-chat-composer :deep(.n-input__textarea-el) { max-height: 116px !important; } }
</style>
