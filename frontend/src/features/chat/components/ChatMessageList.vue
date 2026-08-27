<template>
  <!-- eslint-disable vue/no-v-html -->
  <section ref="scrollContainer" class="lf-chat-stream" aria-label="AI 对话消息" aria-live="polite">
    <article
      v-for="item in messages"
      :key="item.id"
      class="lf-chat-message"
      :class="`is-${item.role}`"
    >
      <div class="lf-chat-message__avatar" aria-hidden="true">
        <Sparkles v-if="item.role === 'assistant'" :size="16" />
        <UserRound v-else :size="16" />
      </div>
      <div class="lf-chat-message__body">
        <div class="lf-chat-message__meta">
          <strong>{{ item.role === 'assistant' ? 'LearnFlow AI' : '你' }}</strong>
          <span>{{ formatTime(item.createdAt) }}</span>
          <n-tag v-if="item.status === 'stopped'" size="tiny" round type="warning">已停止</n-tag>
          <n-tag v-if="item.status === 'error'" size="tiny" round type="error">生成失败</n-tag>
        </div>

        <div class="lf-chat-message__bubble">
          <div
            v-if="item.role === 'assistant' && item.content"
            class="lf-chat-markdown"
            v-html="renderMarkdown(item.content)"
          />
          <p v-else-if="item.role === 'user'" class="lf-chat-user-copy">{{ item.content }}</p>
          <div v-else-if="item.status === 'streaming'" class="lf-chat-thinking">
            <span /><span /><span />
            <b>正在组织回答</b>
          </div>
          <p v-else class="lf-chat-empty-answer">尚未生成回答。</p>

          <n-alert v-if="item.error" :type="item.status === 'error' ? 'error' : 'warning'" :show-icon="false">
            {{ item.error }}
          </n-alert>

          <div v-if="item.role === 'assistant' && sources(item.content).length" class="lf-chat-sources">
            <div class="lf-chat-sources__label"><Library :size="14" />回答中引用的来源</div>
            <div class="lf-chat-sources__list">
              <a
                v-for="source in sources(item.content)"
                :key="source.url"
                :href="source.url"
                target="_blank"
                rel="noopener noreferrer"
              >
                <ExternalLink :size="13" />
                <span><strong>{{ source.title }}</strong><small>{{ source.hostname }}</small></span>
              </a>
            </div>
          </div>
        </div>

        <div v-if="item.role === 'assistant'" class="lf-chat-message__actions">
          <n-button text size="small" :disabled="!item.content" @click="$emit('copy', item)">
            <Copy :size="14" />复制
          </n-button>
          <n-button
            v-if="item.status !== 'streaming' && item !== messages[0]"
            text
            size="small"
            :disabled="loading"
            @click="$emit('regenerate', item)"
          >
            <RefreshCw :size="14" />{{ item.status === 'error' ? '失败重试' : '重新生成' }}
          </n-button>
          <n-button v-if="item.id === activeAssistantId" text size="small" type="error" @click="$emit('stop')">
            <Square :size="13" />停止
          </n-button>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { Copy, ExternalLink, Library, RefreshCw, Sparkles, Square, UserRound } from 'lucide-vue-next';
import type { ChatMessage } from '../types';
import { extractChatSources } from '../composables/useAiChat';

const props = defineProps<{
  messages: ChatMessage[];
  loading: boolean;
  activeAssistantId: string;
}>();
defineEmits<{
  (event: 'copy', message: ChatMessage): void;
  (event: 'regenerate', message: ChatMessage): void;
  (event: 'stop'): void;
}>();

const scrollContainer = ref<HTMLElement | null>(null);

function renderMarkdown(text: string) {
  const safe = DOMPurify.sanitize(marked.parse(text || '', { breaks: true }) as string);
  return safe.replace(/<a /g, '<a target="_blank" rel="noopener noreferrer" ');
}

function sources(text: string) {
  return extractChatSources(text);
}

function formatTime(value: Date) {
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

watch(
  () => props.messages.map((item) => `${item.id}:${item.content.length}:${item.status}`).join('|'),
  () => nextTick(() => {
    if (scrollContainer.value) scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight;
  })
);
</script>

<style scoped>
.lf-chat-stream { min-height: 480px; max-height: min(66vh, 760px); overflow-y: auto; overscroll-behavior: contain; padding: 22px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 22px; background: radial-gradient(circle at 50% 0, rgba(223,243,239,.52), transparent 34%), #f9fbfa; scroll-behavior: smooth; }.lf-chat-message { display: flex; align-items: flex-start; gap: 11px; margin-bottom: 22px; }.lf-chat-message.is-user { flex-direction: row-reverse; }.lf-chat-message__avatar { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 auto; color: #fff; border-radius: 11px; background: linear-gradient(135deg, var(--lf-brand-700, #147a73), #55a99f); box-shadow: 0 8px 20px rgba(20,122,115,.18); }.lf-chat-message.is-user .lf-chat-message__avatar { background: linear-gradient(135deg, #76522f, #b37a3c); }.lf-chat-message__body { display: grid; min-width: 0; width: min(82%, 760px); gap: 7px; }.lf-chat-message.is-user .lf-chat-message__body { justify-items: end; }.lf-chat-message__meta { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; color: var(--lf-text-muted, #62737b); font-size: 10px; }.lf-chat-message__meta strong { color: var(--lf-text, #17313d); font-size: 11px; }.lf-chat-message__bubble { display: grid; width: 100%; gap: 12px; padding: 15px 17px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 4px 18px 18px; background: #fff; box-shadow: 0 10px 28px rgba(18,53,59,.06); }.lf-chat-message.is-user .lf-chat-message__bubble { width: auto; border-color: rgba(20,122,115,.22); border-radius: 18px 4px 18px 18px; color: #effcf9; background: linear-gradient(135deg, #145f5a, #1b7e75); }.lf-chat-user-copy, .lf-chat-empty-answer { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 13px; line-height: 1.75; }.lf-chat-empty-answer { color: var(--lf-text-muted, #62737b); }.lf-chat-message__actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; min-height: 26px; }.lf-chat-message.is-user .lf-chat-message__actions { justify-content: flex-end; }
.lf-chat-markdown { min-width: 0; color: var(--lf-text, #17313d); overflow-wrap: anywhere; font-size: 13px; line-height: 1.75; }.lf-chat-markdown :deep(p) { margin: 0 0 .75em; }.lf-chat-markdown :deep(p:last-child) { margin-bottom: 0; }.lf-chat-markdown :deep(h1), .lf-chat-markdown :deep(h2), .lf-chat-markdown :deep(h3) { margin: 1.1em 0 .45em; line-height: 1.35; }.lf-chat-markdown :deep(ul), .lf-chat-markdown :deep(ol) { padding-left: 1.35rem; }.lf-chat-markdown :deep(blockquote) { margin: 1em 0; padding: 9px 12px; color: var(--lf-text-muted, #62737b); border-left: 3px solid var(--lf-brand-500, #2e9d92); background: var(--lf-surface-soft, #f7f9f8); }.lf-chat-markdown :deep(code) { padding: 2px 5px; border-radius: 5px; background: #edf2f1; font-size: 12px; }.lf-chat-markdown :deep(pre) { max-width: 100%; overflow-x: auto; padding: 14px; color: #dce9e7; border-radius: 12px; background: #11292e; }.lf-chat-markdown :deep(pre code) { padding: 0; color: inherit; background: transparent; }.lf-chat-markdown :deep(a) { color: var(--lf-brand-700, #147a73); font-weight: 750; }
.lf-chat-thinking { display: flex; align-items: center; gap: 5px; color: var(--lf-text-muted, #62737b); }.lf-chat-thinking span { width: 6px; height: 6px; border-radius: 50%; background: var(--lf-brand-500, #2e9d92); animation: lf-chat-pulse 1s ease-in-out infinite; }.lf-chat-thinking span:nth-child(2) { animation-delay: .16s; }.lf-chat-thinking span:nth-child(3) { animation-delay: .32s; }.lf-chat-thinking b { margin-left: 5px; font-size: 11px; }.lf-chat-sources { display: grid; gap: 8px; padding-top: 11px; border-top: 1px solid var(--lf-border, #e3e9ec); }.lf-chat-sources__label { display: flex; align-items: center; gap: 6px; color: var(--lf-text-muted, #62737b); font-size: 10px; font-weight: 800; }.lf-chat-sources__list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; }.lf-chat-sources__list a { display: flex; align-items: flex-start; gap: 7px; min-width: 0; padding: 9px; color: var(--lf-text, #17313d); text-decoration: none; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 10px; background: var(--lf-surface-soft, #f7f9f8); }.lf-chat-sources__list a:hover { border-color: var(--lf-brand-400, #54b0a6); }.lf-chat-sources__list a > span { display: grid; min-width: 0; }.lf-chat-sources__list strong { overflow: hidden; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.lf-chat-sources__list small { color: var(--lf-text-muted, #62737b); font-size: 9px; }
@keyframes lf-chat-pulse { 0%, 100% { opacity: .25; transform: translateY(1px); } 50% { opacity: 1; transform: translateY(-2px); } }
@media (max-width: 640px) { .lf-chat-stream { min-height: 420px; max-height: none; padding: 14px 10px 170px; border-radius: 17px; }.lf-chat-message__body { width: calc(100% - 44px); }.lf-chat-sources__list { grid-template-columns: 1fr; } }
</style>
