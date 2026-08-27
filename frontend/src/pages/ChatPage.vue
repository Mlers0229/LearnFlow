<template>
  <main class="lf-ai-page">
    <header class="lf-ai-hero">
      <div class="lf-ai-hero__copy">
        <span class="lf-ai-hero__eyebrow">Context-aware study partner</span>
        <h1>把问题放回正在学习的内容里</h1>
        <p>从计划、当天任务和推荐资源出发，获得可继续追问、可核对来源的学习回答。</p>
      </div>
      <div class="lf-ai-hero__status">
        <div>
          <span>模型服务</span>
          <strong>{{ modelStatusText }}</strong>
        </div>
        <n-tag :type="modelError ? 'error' : modelLoading ? 'warning' : 'success'" size="small" round>
          {{ modelLoading ? '连接中' : modelError ? '需重试' : '可用' }}
        </n-tag>
        <p>模型由管理员统一配置；你只需关注服务是否可用。</p>
        <n-button v-if="modelError" secondary size="small" :loading="modelLoading" @click="loadModels">
          <RefreshCw :size="14" />重新连接
        </n-button>
      </div>
    </header>

    <section class="lf-ai-metrics" aria-label="对话状态">
      <article><MessagesSquare :size="17" /><div><span>本次对话</span><strong>{{ completedTurns }} 轮</strong></div></article>
      <article><GraduationCap :size="17" /><div><span>学习阶段</span><strong>{{ levelLabel }}</strong></div></article>
      <article><Paperclip :size="17" /><div><span>已附加上下文</span><strong>{{ contextSummary }}</strong></div></article>
    </section>

    <div class="lf-ai-workspace">
      <section class="lf-ai-conversation" aria-labelledby="conversation-title">
        <div class="lf-ai-conversation__head">
          <div>
            <span>Conversation</span>
            <h2 id="conversation-title">对话工作区</h2>
            <p>回答生成失败时可直接重试；重新生成会从对应问题继续。</p>
          </div>
          <n-tag v-if="loading" type="info" round><LoaderCircle class="lf-ai-spin" :size="14" />流式生成中</n-tag>
        </div>

        <ChatMessageList
          :messages="messages"
          :loading="loading"
          :active-assistant-id="activeAssistantId"
          @copy="copyMessage"
          @regenerate="regenerateMessage"
          @stop="stop"
        />

        <ChatComposer
          v-model="inputText"
          :loading="loading"
          :context-label="contextLabel"
          :presets="presets"
          @preset="inputText = $event"
          @send="sendMessage"
          @stop="stop"
          @clear="confirmClear"
        />
      </section>

      <ChatContextPanel
        :enabled="enabled"
        :selected-plan-id="selectedPlanId"
        :selected-day-id="currentDayId == null ? null : String(currentDayId)"
        :plan-options="planOptions"
        :day-options="dayOptions"
        :current-day="currentDay"
        :resources="resources"
        :loading="contextLoading"
        :resources-loading="resourcesLoading"
        :error="contextError"
        :resources-error="resourcesError"
        @update:enabled="enabled = $event"
        @select-plan="selectPlan"
        @select-day="selectDay"
        @open-day="openLearningDay"
      />
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useDialog, useMessage } from 'naive-ui';
import { GraduationCap, LoaderCircle, MessagesSquare, Paperclip, RefreshCw } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import ChatComposer from '../features/chat/components/ChatComposer.vue';
import ChatContextPanel from '../features/chat/components/ChatContextPanel.vue';
import ChatMessageList from '../features/chat/components/ChatMessageList.vue';
import { useAiChat } from '../features/chat/composables/useAiChat';
import { useLearningContext } from '../features/chat/composables/useLearningContext';
import type { ChatMessage } from '../features/chat/types';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const dialog = useDialog();
const notification = useMessage();
const { currentUser, isLoggedIn } = useAuthStore();
const {
  messages, inputText, loading, activeAssistantId, modelLoading, modelError, modelStatusText,
  completedTurns, loadModels, send, regenerate, stop, clear
} = useAiChat({ isLoggedIn });
const {
  enabled, currentPlan, currentDayId, currentDay, resources, loading: contextLoading,
  resourcesLoading, error: contextError, resourcesError, planOptions, dayOptions,
  learningContext, contextLabel, load: loadContext, selectPlan, selectDay
} = useLearningContext();

const presets = ['解释今天最难的概念', '根据任务出 3 道自测题', '把今天内容整理成复习清单'];
const selectedPlanId = computed(() => currentPlan.value?.id == null ? null : String(currentPlan.value.id));
const contextSummary = computed(() => {
  if (!enabled.value) return '未启用';
  if (resourcesLoading.value) return '同步中';
  if (currentDay.value) return `${resources.value.length} 项资源`;
  return '暂无计划';
});
const levelLabel = computed(() => {
  const value = String(currentUser.value?.level || '').toLowerCase();
  if (value === 'beginner' || value.includes('初') || value.includes('零')) return '入门';
  if (value === 'advanced' || value.includes('进阶') || value.includes('高')) return '进阶';
  if (value) return '有基础';
  return '待分级';
});

onMounted(() => Promise.all([loadModels(), loadContext()]));

async function sendMessage() {
  const result = await send(learningContext.value);
  if (result.reason === 'auth') notification.warning('请先登录后再使用 AI 对话');
  else if (result.reason === 'empty') notification.warning('请输入问题');
  else if (result.reason === 'error') notification.error('回答生成失败，可在消息下方直接重试');
}

async function regenerateMessage(item: ChatMessage) {
  const result = await regenerate(item.id, learningContext.value);
  if (result.reason === 'error') notification.error('重新生成失败，请稍后再试');
}

async function copyMessage(item: ChatMessage) {
  try {
    await navigator.clipboard.writeText(item.content);
    notification.success('回答已复制');
  } catch {
    notification.error('复制失败，请检查浏览器剪贴板权限');
  }
}

function confirmClear() {
  if (messages.value.length <= 1) return clear();
  dialog.warning({
    title: '清空本次对话？',
    content: '当前问题和回答会从页面中移除，学习计划与资源上下文不会受到影响。',
    positiveText: '确认清空',
    negativeText: '保留对话',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: () => clear()
  });
}

function openLearningDay() {
  if (!currentPlan.value || currentDayId.value == null) return;
  router.push({ name: 'plan-history', query: { planId: String(currentPlan.value.id), dayId: String(currentDayId.value) } });
}
</script>

<style scoped>
.lf-ai-page { display: grid; gap: 15px; min-width: 0; padding-bottom: 28px; }.lf-ai-hero { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(270px, .65fr); gap: 18px; padding: clamp(22px, 4vw, 38px); overflow: hidden; border: 1px solid rgba(22,84,80,.12); border-radius: 28px; background: radial-gradient(circle at 88% 10%, rgba(94,170,207,.18), transparent 29%), radial-gradient(circle at 64% 100%, rgba(196,139,69,.16), transparent 32%), linear-gradient(135deg, #effaf7, #fff 57%, #f4f6fb); }.lf-ai-hero__eyebrow { color: var(--lf-brand-700, #147a73); font-size: 10px; font-weight: 850; letter-spacing: .15em; text-transform: uppercase; }.lf-ai-hero h1 { max-width: 760px; margin: 9px 0 10px; color: var(--lf-text, #17313d); font-size: clamp(30px, 4.7vw, 50px); line-height: 1.08; letter-spacing: -.04em; }.lf-ai-hero__copy p { max-width: 680px; margin: 0; color: var(--lf-text-muted, #62737b); font-size: 13px; line-height: 1.8; }.lf-ai-hero__status { align-self: stretch; display: grid; align-content: center; grid-template-columns: minmax(0, 1fr) auto; gap: 8px 10px; padding: 18px; border: 1px solid rgba(255,255,255,.82); border-radius: 19px; background: rgba(255,255,255,.72); box-shadow: 0 16px 40px rgba(18,53,59,.07); backdrop-filter: blur(10px); }.lf-ai-hero__status > div { display: grid; gap: 3px; }.lf-ai-hero__status span { color: var(--lf-text-muted, #62737b); font-size: 9px; font-weight: 800; }.lf-ai-hero__status strong { color: var(--lf-text, #17313d); font-size: 13px; overflow-wrap: anywhere; }.lf-ai-hero__status p { grid-column: 1 / -1; margin: 0; color: var(--lf-text-muted, #62737b); font-size: 10px; line-height: 1.55; }.lf-ai-hero__status :deep(.n-button) { grid-column: 1 / -1; justify-self: start; }
.lf-ai-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }.lf-ai-metrics article { display: flex; align-items: center; gap: 9px; padding: 12px 14px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 14px; background: #fff; }.lf-ai-metrics svg { color: var(--lf-brand-600, #1a897f); }.lf-ai-metrics article > div { display: grid; }.lf-ai-metrics span { color: var(--lf-text-muted, #62737b); font-size: 9px; }.lf-ai-metrics strong { color: var(--lf-text, #17313d); font-size: 13px; }.lf-ai-workspace { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(280px, .58fr); align-items: start; gap: 14px; }.lf-ai-conversation { display: grid; min-width: 0; gap: 13px; padding: 18px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 22px; background: #fff; }.lf-ai-conversation__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }.lf-ai-conversation__head span { color: var(--lf-brand-700, #147a73); font-size: 9px; font-weight: 850; letter-spacing: .13em; text-transform: uppercase; }.lf-ai-conversation__head h2 { margin: 3px 0 0; color: var(--lf-text, #17313d); font-size: 19px; }.lf-ai-conversation__head p { margin: 4px 0 0; color: var(--lf-text-muted, #62737b); font-size: 10px; }.lf-ai-spin { animation: lf-ai-spin 1s linear infinite; }
@keyframes lf-ai-spin { to { transform: rotate(360deg); } }
@media (max-width: 1040px) { .lf-ai-workspace, .lf-ai-hero { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .lf-ai-page { padding-bottom: 164px; }.lf-ai-hero { border-radius: 22px; }.lf-ai-metrics { grid-template-columns: 1fr; }.lf-ai-conversation { padding: 11px; border-radius: 18px; }.lf-ai-conversation__head { flex-direction: column; } }
</style>
