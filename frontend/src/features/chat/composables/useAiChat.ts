import { computed, reactive, ref, type Ref } from 'vue';
import { fetchChatModels, streamChat } from '../../../api/chat';
import type { ChatMessage, ChatSource } from '../types';

type ChatApi = {
  fetchModels: () => Promise<Record<string, unknown>>;
  stream: (
    messages: Array<{ role: string; content: string }>,
    onChunk: (chunk: string) => void,
    signal: AbortSignal,
    model: string | null
  ) => Promise<void>;
};

export type LearningContext = {
  planTitle?: string;
  dayTitle?: string;
  date?: string;
  tasks?: string[];
  resources?: Array<{ title?: string; url?: string; reason?: string }>;
};

type Options = {
  isLoggedIn: Ref<boolean>;
  api?: ChatApi;
};

const defaultApi: ChatApi = {
  fetchModels: () => fetchChatModels(),
  stream: (messages, onChunk, signal, model) => streamChat(messages, onChunk, signal, model)
};

const WELCOME_CONTENT = '你好，我是你的学习小助手。选择右侧学习上下文，或直接告诉我你想理解、练习或复盘的内容。';

function createId() {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function createMessage(role: ChatMessage['role'], content: string, status: ChatMessage['status'] = 'complete'): ChatMessage {
  return reactive({ id: createId(), role, content, createdAt: new Date(), status }) as ChatMessage;
}

export function extractChatSources(markdown: string): ChatSource[] {
  const sources = new Map<string, ChatSource>();
  const pattern = /\[([^\]]+)]\((https?:\/\/[^\s)]+)\)/g;
  for (const match of markdown.matchAll(pattern)) {
    try {
      const url = new URL(match[2]);
      if (!sources.has(url.href)) {
        sources.set(url.href, { title: match[1].trim() || url.hostname, url: url.href, hostname: url.hostname });
      }
    } catch {
      // Ignore malformed model-generated links instead of exposing unsafe navigation.
    }
  }
  return [...sources.values()];
}

export function buildLearningContextPrompt(context?: LearningContext | null) {
  if (!context?.planTitle && !context?.dayTitle && !context?.tasks?.length && !context?.resources?.length) return '';
  const lines = [
    '请结合下面的 LearnFlow 学习上下文回答。不要虚构资源内容；不确定时明确说明。',
    context.planTitle ? `学习计划：${context.planTitle}` : '',
    context.dayTitle ? `当前学习日：${context.dayTitle}${context.date ? `（${context.date}）` : ''}` : '',
    context.tasks?.length ? `今日任务：\n- ${context.tasks.join('\n- ')}` : '',
    context.resources?.length
      ? `可引用资源：\n${context.resources.map((item) => `- ${item.title || '学习资源'}${item.url ? `：${item.url}` : ''}${item.reason ? `（${item.reason}）` : ''}`).join('\n')}`
      : '',
    '若使用资源，请在答案中用 Markdown 链接标注来源。'
  ];
  return lines.filter(Boolean).join('\n\n');
}

export function useAiChat(options: Options) {
  const api = options.api ?? defaultApi;
  const messages = ref<ChatMessage[]>([createMessage('assistant', WELCOME_CONTENT)]);
  const inputText = ref('');
  const loading = ref(false);
  const activeAssistantId = ref('');
  const modelLoading = ref(false);
  const modelSource = ref('fallback');
  const resolvedModel = ref('');
  const modelError = ref('');
  let abortController: AbortController | null = null;
  let streamSequence = 0;

  const modelStatusText = computed(() => {
    if (modelLoading.value) return '正在连接模型服务';
    if (modelError.value) return '模型服务暂不可用';
    if (resolvedModel.value) return `服务已就绪 · ${resolvedModel.value}`;
    return modelSource.value === 'remote' ? '模型服务已就绪' : '使用系统默认服务';
  });
  const completedTurns = computed(() => messages.value.filter((item) => item.role === 'user').length);

  async function loadModels() {
    modelLoading.value = true;
    modelError.value = '';
    try {
      const payload = await api.fetchModels();
      const models = Array.isArray(payload?.models) ? payload.models as Array<{ id?: string }> : [];
      modelSource.value = typeof payload?.source === 'string' ? payload.source : 'fallback';
      resolvedModel.value = typeof payload?.defaultModel === 'string'
        ? payload.defaultModel
        : models.find((item) => item?.id)?.id || '';
      return true;
    } catch (cause) {
      console.error(cause);
      modelSource.value = 'fallback';
      resolvedModel.value = '';
      modelError.value = '暂时无法连接模型服务，请稍后重试。';
      return false;
    } finally {
      modelLoading.value = false;
    }
  }

  function apiMessages(untilIndex = messages.value.length) {
    return messages.value
      .slice(1, untilIndex)
      .filter((item) => item.content.trim() && item.status !== 'error')
      .map((item) => ({ role: item.role, content: item.content }));
  }

  async function runAssistant(assistant: ChatMessage, payload: Array<{ role: string; content: string }>) {
    const sequence = ++streamSequence;
    abortController = new AbortController();
    loading.value = true;
    activeAssistantId.value = assistant.id;
    assistant.status = 'streaming';
    assistant.error = '';
    try {
      await api.stream(payload, (chunk) => {
        if (sequence === streamSequence) assistant.content += chunk;
      }, abortController.signal, resolvedModel.value || null);
      if (sequence === streamSequence) assistant.status = 'complete';
      return { ok: true as const };
    } catch (cause) {
      if (sequence !== streamSequence) return { ok: false as const, reason: 'stale' as const };
      const aborted = abortController.signal.aborted || (cause instanceof DOMException && cause.name === 'AbortError');
      if (aborted) {
        assistant.status = 'stopped';
        assistant.error = '回答已停止，你可以重新生成。';
        return { ok: false as const, reason: 'stopped' as const };
      }
      assistant.status = 'error';
      assistant.error = cause instanceof Error ? cause.message : '模型服务返回异常';
      return { ok: false as const, reason: 'error' as const };
    } finally {
      if (sequence === streamSequence) {
        loading.value = false;
        activeAssistantId.value = '';
        abortController = null;
      }
    }
  }

  async function send(context?: LearningContext | null) {
    if (!options.isLoggedIn.value) return { ok: false as const, reason: 'auth' as const };
    const content = inputText.value.trim();
    if (!content) return { ok: false as const, reason: 'empty' as const };
    if (loading.value) return { ok: false as const, reason: 'busy' as const };

    const user = createMessage('user', content);
    const assistant = createMessage('assistant', '', 'streaming');
    messages.value.push(user, assistant);
    inputText.value = '';
    const contextPrompt = buildLearningContextPrompt(context);
    const payload: Array<{ role: string; content: string }> = apiMessages();
    if (contextPrompt) payload.unshift({ role: 'system', content: contextPrompt });
    return runAssistant(assistant, payload);
  }

  async function regenerate(assistantId: string, context?: LearningContext | null) {
    if (loading.value) return { ok: false as const, reason: 'busy' as const };
    const index = messages.value.findIndex((item) => item.id === assistantId && item.role === 'assistant');
    if (index < 2 || messages.value[index - 1]?.role !== 'user') return { ok: false as const, reason: 'missing' as const };

    const assistant = messages.value[index];
    messages.value.splice(index + 1);
    assistant.content = '';
    assistant.status = 'streaming';
    assistant.error = '';
    const contextPrompt = buildLearningContextPrompt(context);
    const payload: Array<{ role: string; content: string }> = apiMessages(index);
    if (contextPrompt) payload.unshift({ role: 'system', content: contextPrompt });
    return runAssistant(assistant, payload);
  }

  function stop() {
    abortController?.abort();
  }

  function clear() {
    streamSequence += 1;
    abortController?.abort();
    abortController = null;
    loading.value = false;
    activeAssistantId.value = '';
    messages.value = [createMessage('assistant', WELCOME_CONTENT)];
    inputText.value = '';
  }

  return {
    messages,
    inputText,
    loading,
    activeAssistantId,
    modelLoading,
    modelError,
    resolvedModel,
    modelStatusText,
    completedTurns,
    loadModels,
    send,
    regenerate,
    stop,
    clear
  };
}
