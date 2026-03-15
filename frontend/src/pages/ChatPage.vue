<template>
  <div class="page">
    <n-space vertical size="large">
      <n-card size="large" :segmented="{ content: true }" bordered>
        <template #header>
          <div class="header">
            <div class="title">
              <span class="dot" />
              <span>AI 对话陪练</span>
              <n-tag type="success" size="small">流式输出</n-tag>
            </div>
            <div class="model-panel">
              <div class="model-panel-copy">
                <span class="model-panel-label">当前模型</span>
                <span class="model-panel-value">{{ currentModelText }}</span>
              </div>
              <div class="model-panel-tags">
                <n-tag size="small" round :type="levelTagType">{{ levelText }}</n-tag>
                <n-tag size="small" round type="info">管理端统一配置</n-tag>
              </div>
            </div>
          </div>
        </template>
        <n-space vertical size="small">
          <p class="desc">
            像 ChatGPT 一样提问，实时看到逐字输出。适合做总结、解释、代码讲解等。
          </p>
          <p class="sub-desc">
            用户侧当前不开放模型切换，仅展示管理端已配置的对话模型。
          </p>
          <div class="chips">
            <n-tag
              v-for="preset in presets"
              :key="preset"
              checkable
              @click="usePreset(preset)"
            >{{ preset }}</n-tag>
          </div>
        </n-space>
      </n-card>

      <n-card :segmented="{ footer: true }" bordered>
        <div class="conversation-shell">
          <div class="conversation-banner">
            <div class="conversation-banner-copy">
              <span class="conversation-banner-label">实时陪练</span>
              <h3 class="conversation-banner-title">围绕当前学习任务继续追问、拆解与复盘</h3>
              <p class="conversation-banner-desc">
                适合解释概念、梳理知识点、分析代码示例，或把当天学习内容整理成更易复习的结构。
              </p>
            </div>
            <div class="conversation-banner-meta">
              <div class="conversation-stat">
                <span class="conversation-stat-label">对话模式</span>
                <strong>流式响应</strong>
              </div>
              <div class="conversation-stat">
                <span class="conversation-stat-label">当前等级</span>
                <strong>{{ levelDisplayText }}</strong>
              </div>
            </div>
          </div>

          <div class="chat-window" ref="chatBox">
            <div
              v-for="(m, idx) in messages"
              :key="idx"
              class="chat-line"
              :class="[m.role, m.role === 'user' ? 'align-end' : 'align-start']"
            >
              <div class="avatar" :class="m.role">
                {{ m.role === 'user' ? '我' : 'AI' }}
              </div>
              <div class="bubble-shell" :class="m.role">
                <div class="meta-line">
                  <span class="role">{{ m.role === 'user' ? '你' : 'AI 助手' }}</span>
                  <span class="time">{{ formatTime(m.createdAt) }}</span>
                </div>
                <div class="bubble" :class="m.role">
                  <template v-if="m.role === 'assistant'">
                    <div class="assistant-header">
                      <n-button text size="tiny" @click="copyContent(m.content)">复制内容</n-button>
                      <n-button text size="tiny" type="error" @click="handleStop" :disabled="!loading">
                        停止生成
                      </n-button>
                    </div>
                    <div class="content markdown-body" v-html="renderMarkdown(m.content)" />
                  </template>
                  <template v-else>
                    <pre class="content">{{ m.content }}</pre>
                  </template>
                </div>
              </div>
            </div>

            <div v-if="loading" class="typing">
              <span class="dot-typing" />
              正在思考，流式输出中...
            </div>
          </div>
        </div>

        <template #footer>
          <div class="composer-shell">
            <div class="input-area">
              <n-input
                v-model:value="inputText"
                type="textarea"
                :autosize="{ minRows: 3, maxRows: 6 }"
                placeholder="请输入想问 AI 的问题..."
                :disabled="loading"
                @keydown.enter.prevent="handleSend"
              />
              <div class="actions">
                <n-space>
                  <n-button type="primary" :loading="loading" @click="handleSend">
                    {{ loading ? '回答生成中...' : '发送' }}
                  </n-button>
                  <n-button quaternary :disabled="!loading" @click="handleStop">
                    停止生成
                  </n-button>
                  <n-button text :disabled="loading" @click="handleClear">
                    清空对话
                  </n-button>
                </n-space>
                <span class="hint">支持长回答流式输出，适合边看边追问</span>
              </div>
            </div>
          </div>
        </template>
      </n-card>
    </n-space>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { fetchChatModels, streamChat } from '../api/chat';
import { useAuthStore } from '../store/auth';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

const message = useMessage();
const { currentUser, isLoggedIn } = useAuthStore();

const messages = ref([
  {
    role: 'assistant',
    content: '你好，我是你的学习小助手。随时提问，我会实时回答！',
    createdAt: new Date()
  }
]);
const inputText = ref('');
const loading = ref(false);
const chatBox = ref(null);
const abortController = ref(null);
const modelLoading = ref(false);
const modelSource = ref('fallback');
const resolvedModel = ref('');
const presets = [
  '用 3 行总结今天学到的内容',
  '给“Java Stream 过滤示例”写一段示例代码',
  '把下面文字转成要点列表：',
  '给我一个 5 天复习计划'
];

const LEVEL_META = {
  beginner: { text: '当前等级：零基础', tagType: 'warning' },
  intermediate: { text: '当前等级：有一点基础', tagType: 'success' },
  advanced: { text: '当前等级：进阶', tagType: 'error' },
  ungraded: { text: '当前等级：待分级', tagType: 'default' }
};

const normalizedLevel = computed(() => {
  const raw = currentUser.value?.level;
  if (!raw) return 'ungraded';

  const value = String(raw).trim().toLowerCase();
  if (['beginner', 'intermediate', 'advanced'].includes(value)) {
    return value;
  }
  if (value.includes('初') || value.includes('零')) {
    return 'beginner';
  }
  if (value.includes('进阶') || value.includes('高')) {
    return 'advanced';
  }
  return 'intermediate';
});

const levelMeta = computed(() => LEVEL_META[normalizedLevel.value] || LEVEL_META.ungraded);
const levelText = computed(() => levelMeta.value.text);
const levelTagType = computed(() => levelMeta.value.tagType);
const levelDisplayText = computed(() => levelText.value.replace('当前等级：', ''));

const currentModelText = computed(() => {
  if (modelLoading.value) return '同步中...';
  if (resolvedModel.value) return resolvedModel.value;
  return modelSource.value === 'remote' ? '自动分配' : '系统默认';
});

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight;
    }
  });
};

const renderMarkdown = (text) => {
  const html = marked.parse(text || '', { breaks: true });
  return DOMPurify.sanitize(html);
};

const formatTime = (date) => {
  if (!date) return '';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

const usePreset = (text) => {
  inputText.value = text;
};

const handleClear = () => {
  messages.value = [
    {
      role: 'assistant',
      content: '你好，我是你的学习小助手。随时提问，我会实时回答！',
      createdAt: new Date()
    }
  ];
  inputText.value = '';
};

const handleStop = () => {
  if (abortController.value) {
    abortController.value.abort();
  }
};

const copyContent = async (text) => {
  try {
    await navigator.clipboard.writeText(text || '');
    message.success('已复制');
  } catch (e) {
    message.error('复制失败');
  }
};

const applyModelInfo = (payload) => {
  const modelIds = Array.isArray(payload?.models)
    ? payload.models
        .filter((item) => item && item.id)
        .map((item) => item.id)
    : [];

  modelSource.value = payload?.source || 'fallback';
  resolvedModel.value = payload?.defaultModel || modelIds[0] || '';
};

const loadModels = async () => {
  modelLoading.value = true;
  try {
    const payload = await fetchChatModels();
    applyModelInfo(payload);
    if (payload?.source === 'fallback' && payload?.message) {
      message.warning('模型目录暂未同步成功，当前使用系统默认配置');
    }
  } catch (err) {
    console.error(err);
    modelSource.value = 'fallback';
    resolvedModel.value = '';
  } finally {
    modelLoading.value = false;
  }
};

const handleSend = async () => {
  if (!isLoggedIn.value) {
    message.warning('请先登录后再使用对话功能');
    return;
  }
  if (!inputText.value.trim()) {
    message.warning('请输入问题');
    return;
  }
  if (loading.value) return;

  const userMsg = reactive({ role: 'user', content: inputText.value.trim(), createdAt: new Date() });
  const aiMsg = reactive({ role: 'assistant', content: '', createdAt: new Date() });
  messages.value.push(userMsg);
  messages.value.push(aiMsg);
  inputText.value = '';
  scrollToBottom();

  loading.value = true;
  abortController.value = new AbortController();

  try {
    await streamChat(
      messages.value.map((m) => ({ role: m.role, content: m.content })),
      (chunk) => {
        aiMsg.content += chunk;
        scrollToBottom();
      },
      abortController.value.signal,
      null
    );
  } catch (err) {
    aiMsg.content += `\n[对话失败] ${err.message || err}`;
    message.error('对话接口调用失败，请稍后重试');
  } finally {
    loading.value = false;
    abortController.value = null;
    scrollToBottom();
  }
};

onMounted(() => {
  loadModels();
});
</script>

<style scoped>
.page {
  padding: 12px;
}
.desc {
  margin: 0;
  color: #374151;
  line-height: 1.6;
}
.sub-desc {
  margin: 0;
  color: #6b7280;
  line-height: 1.7;
  font-size: 13px;
}
.conversation-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.conversation-banner {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-radius: 20px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0) 38%),
    linear-gradient(135deg, #f4fbf7 0%, #eef4ff 55%, #fffaf2 100%);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
}
.conversation-banner-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 680px;
}
.conversation-banner-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #6b7280;
}
.conversation-banner-title {
  margin: 0;
  font-size: 20px;
  line-height: 1.35;
  color: #111827;
}
.conversation-banner-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #4b5563;
}
.conversation-banner-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 10px;
  min-width: 280px;
}
.conversation-stat {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(15, 23, 42, 0.06);
}
.conversation-stat-label {
  font-size: 12px;
  color: #6b7280;
}
.conversation-stat strong {
  font-size: 15px;
  color: #0f172a;
}
.chat-window {
  min-height: 360px;
  max-height: 520px;
  overflow-y: auto;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(255, 255, 255, 0.96)),
    radial-gradient(circle at top, rgba(225, 244, 236, 0.55), transparent 38%),
    linear-gradient(135deg, #f5faf8 0%, #f7f9ff 52%, #fffdf8 100%);
  border-radius: 24px;
  padding: 22px 20px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.86),
    0 16px 40px rgba(148, 163, 184, 0.12);
}
.chat-line {
  display: flex;
  gap: 14px;
  margin-bottom: 20px;
}
.align-end {
  flex-direction: row-reverse;
}
.bubble-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: min(840px, 86vw);
}
.bubble {
  position: relative;
  border-radius: 20px;
  padding: 14px 16px;
  white-space: pre-wrap;
  line-height: 1.7;
  box-shadow: 0 14px 30px rgba(148, 163, 184, 0.14);
}
.bubble.assistant {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}
.bubble.assistant::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 20px 0 0 20px;
  background: linear-gradient(180deg, #22c55e, #3b82f6);
}
.bubble.user {
  background: linear-gradient(135deg, #153e2f 0%, #1f6b4f 100%);
  border: 1px solid rgba(21, 128, 61, 0.24);
  color: #effcf4;
  box-shadow: 0 16px 34px rgba(22, 101, 52, 0.22);
}
.content {
  margin: 0;
  font-size: 14px;
  color: #111827;
  white-space: pre-wrap;
  word-break: break-word;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #fff;
  margin-top: 4px;
  flex-shrink: 0;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.18);
}
.avatar.assistant {
  background: linear-gradient(135deg, #2563eb, #14b8a6);
}
.avatar.user {
  background: linear-gradient(135deg, #14532d, #16a34a);
}
.meta-line {
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 8px;
  align-items: center;
}
.bubble-shell.user .meta-line {
  justify-content: flex-end;
}
.typing {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #4b5563;
  font-size: 13px;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 10px 22px rgba(148, 163, 184, 0.14);
}
.dot-typing {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0ea5e9, #22c55e);
  animation: pulse 1s infinite ease-in-out;
}
@keyframes pulse {
  0% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.3;
  }
}
.markdown-body {
  line-height: 1.65;
}
.markdown-body p {
  margin: 0.4em 0;
}
.markdown-body code {
  background: #f3f4f6;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 13px;
}
.markdown-body pre {
  background: #0b1021;
  color: #e5e7eb;
  padding: 10px;
  border-radius: 6px;
  overflow: auto;
}
.markdown-body ul {
  padding-left: 1.2em;
  margin: 0.4em 0;
}
.bubble.user .content,
.bubble.user :deep(.markdown-body),
.bubble.user :deep(.markdown-body p),
.bubble.user :deep(.markdown-body li) {
  color: #effcf4;
}
.bubble.user :deep(.markdown-body code) {
  background: rgba(255, 255, 255, 0.14);
  color: #f8fafc;
}
.bubble.user :deep(.markdown-body a) {
  color: #dcfce7;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}
.title .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #22c55e, #4f46e5);
  box-shadow: 0 0 8px rgba(79, 70, 229, 0.35);
}
.model-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f7faf9, #eef5f7);
  border: 1px solid rgba(15, 23, 42, 0.08);
}
.model-panel-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  text-align: right;
}
.model-panel-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7b8794;
}
.model-panel-value {
  max-width: 360px;
  font-size: 16px;
  line-height: 1.35;
  font-weight: 700;
  color: #111827;
  word-break: break-word;
}
.model-panel-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.assistant-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.composer-shell {
  padding: 2px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(226, 232, 240, 0.9), rgba(209, 250, 229, 0.85));
}
.input-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: 22px;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hint {
  color: #9ca3af;
  font-size: 12px;
}
.input-area :deep(.n-input) {
  --n-border: rgba(148, 163, 184, 0.28);
  --n-border-hover: rgba(59, 130, 246, 0.36);
  --n-border-focus: rgba(34, 197, 94, 0.52);
  --n-border-radius: 18px;
}
.input-area :deep(.n-input__textarea-el) {
  line-height: 1.7;
}

@media (max-width: 900px) {
  .header,
  .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .model-panel {
    align-items: stretch;
  }

  .model-panel-copy,
  .model-panel-tags {
    text-align: left;
    justify-content: flex-start;
  }

  .conversation-banner {
    flex-direction: column;
  }

  .conversation-banner-meta {
    grid-template-columns: 1fr;
    min-width: 0;
  }
}
</style>
