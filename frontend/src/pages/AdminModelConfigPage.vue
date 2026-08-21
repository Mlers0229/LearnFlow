<template>
  <div class="admin-model-page">
    <section class="hero-panel">
      <div>
        <div class="hero-kicker">Model Control Center</div>
        <h1 class="hero-title">管理端模型配置页</h1>
        <p class="hero-subtitle">
          把第三方 API、默认模型、自动同步策略和计划 Agent 开关集中到管理端统一维护。用户侧仅展示分配结果，不再直接选择模型。
        </p>
        <div class="hero-chip-row">
          <span class="header-chip header-chip-admin-page">系统统一分配</span>
          <span class="header-chip header-chip-admin-subtle">模型目录自动发现</span>
          <span class="header-chip header-chip-admin-subtle">Chat / Plan 共用配置</span>
        </div>
      </div>

      <div class="hero-status-card">
        <div class="status-label">当前状态</div>
        <div class="status-value">{{ providerStatusText }}</div>
        <div class="status-meta">{{ providerStatusHint }}</div>
        <div class="status-facts">
          <div class="status-fact">
            <span class="status-fact-label">模型目录</span>
            <span class="status-fact-value">{{ catalogSummary }}</span>
          </div>
          <div class="status-fact">
            <span class="status-fact-label">默认模型</span>
            <span class="status-fact-value">{{ config?.defaultModel || '未设置' }}</span>
          </div>
        </div>
      </div>
    </section>

    <div v-if="error" class="page-error">
      <span>{{ error }}</span>
      <n-button size="small" tertiary type="primary" @click="loadConfig">重试</n-button>
    </div>

    <div class="page-grid">
      <n-card class="config-card" :bordered="true">
        <template #header>
          <div class="panel-head">
            <div>
              <div class="panel-kicker">Provider Config</div>
              <div class="panel-title">模型接入与策略配置</div>
            </div>
            <span class="panel-note">保存后立即作用于聊天页与计划 Agent</span>
          </div>
        </template>

        <n-spin :show="loading || saving">
          <n-form label-placement="top" size="small">
            <div class="form-grid form-grid-2">
              <n-form-item label="API Base">
                <n-input
                  v-model:value="form.apiBase"
                  placeholder="例如：https://api.deepseek.com"
                />
              </n-form-item>

              <n-form-item label="默认模型">
                <n-input
                  v-model:value="form.defaultModel"
                  placeholder="例如：deepseek-chat / gpt-4o-mini"
                />
              </n-form-item>
            </div>

            <n-alert type="info" :show-icon="true">
              API Key 仅允许通过部署平台 Secret 或环境变量 <code>LLM_API_KEY</code> 注入，管理页面不再接收或保存密钥。
            </n-alert>

            <div class="strategy-grid">
              <div class="strategy-card">
                <div>
                  <div class="strategy-title">自动同步模型目录</div>
                  <div class="strategy-desc">开启后会调用第三方 `/v1/models` 获取可用模型列表。</div>
                </div>
                <n-switch v-model:value="form.autoDiscoverModels" />
              </div>

              <div class="strategy-card">
                <div>
                  <div class="strategy-title">启用 LLM 计划生成</div>
                  <div class="strategy-desc">关闭后，PlanAgent 回退到规则计划，不直接调用大模型。</div>
                </div>
                <n-switch v-model:value="form.enableLlmPlan" />
              </div>
            </div>

            <div class="suggestion-block">
              <div class="suggestion-head">
                <div class="suggestion-title">可用模型建议</div>
                <div class="suggestion-desc">点击下方标签可快速填入默认模型</div>
              </div>
              <div class="tag-cloud">
                <n-tag
                  v-for="item in modelTags"
                  :key="item.id"
                  checkable
                  :checked="form.defaultModel === item.id"
                  @click="pickModel(item.id)"
                >{{ item.id }}</n-tag>
                <span v-if="!modelTags.length" class="empty-inline">当前还没有同步到模型目录。</span>
              </div>
            </div>

            <div class="action-row">
              <n-space>
                <n-button type="primary" :loading="saving" @click="handleSave">保存配置</n-button>
                <n-button :loading="syncing" @click="handleRefreshCatalog">同步模型目录</n-button>
                <n-button quaternary @click="resetForm">重置表单</n-button>
              </n-space>
              <span class="action-hint">保存配置不会影响用户侧入口，只会更新管理端统一分配策略。</span>
            </div>
          </n-form>
        </n-spin>
      </n-card>

      <n-card class="snapshot-card" :bordered="true">
        <template #header>
          <div class="panel-head">
            <div>
              <div class="panel-kicker">Runtime Snapshot</div>
              <div class="panel-title">当前生效配置</div>
            </div>
            <span class="panel-note">展示运行态来源与模型目录状态</span>
          </div>
        </template>

        <div class="snapshot-stack">
          <div class="snapshot-highlight">
            <div class="snapshot-label">配置来源</div>
            <div class="snapshot-value">{{ configSourceSummary }}</div>
            <div class="snapshot-desc">优先读取运行时保存配置，其次回退到 `llm_settings.py` 或环境变量。</div>
          </div>

          <div class="fact-grid">
            <div class="fact-card">
              <div class="fact-label">API Base</div>
              <div class="fact-value fact-value-mono">{{ config?.apiBase || '未配置' }}</div>
            </div>
            <div class="fact-card">
              <div class="fact-label">API Key</div>
              <div class="fact-value fact-value-mono">{{ config?.maskedApiKey || '未配置' }}</div>
            </div>
            <div class="fact-card">
              <div class="fact-label">目录来源</div>
              <div class="fact-value">{{ catalogSourceText }}</div>
            </div>
            <div class="fact-card">
              <div class="fact-label">最后更新</div>
              <div class="fact-value">{{ updatedAtText }}</div>
            </div>
          </div>

          <div class="policy-list">
            <article v-for="item in policyCards" :key="item.title" class="policy-card">
              <div class="policy-card-top">
                <div class="policy-card-title">{{ item.title }}</div>
                <n-tag size="small" :type="item.tagType">{{ item.tag }}</n-tag>
              </div>
              <div class="policy-card-desc">{{ item.desc }}</div>
            </article>
          </div>
        </div>
      </n-card>
    </div>

    <n-card class="catalog-card" :bordered="true">
      <template #header>
        <div class="panel-head">
          <div>
            <div class="panel-kicker">Model Catalog</div>
            <div class="panel-title">已发现模型目录</div>
          </div>
          <span class="panel-note">共 {{ modelTags.length }} 个模型，来源：{{ catalogSourceText }}</span>
        </div>
      </template>

      <div class="catalog-grid">
        <article
          v-for="item in modelTags"
          :key="item.id"
          :class="['catalog-item', form.defaultModel === item.id && 'catalog-item-active']"
          @click="pickModel(item.id)"
        >
          <div class="catalog-item-top">
            <div>
              <div class="catalog-item-title">{{ item.id }}</div>
              <div class="catalog-item-meta">{{ item.ownedBy || 'remote' }}</div>
            </div>
            <n-tag size="small" :type="form.defaultModel === item.id ? 'success' : 'default'">
              {{ form.defaultModel === item.id ? '默认模型' : '可选' }}
            </n-tag>
          </div>
          <div class="catalog-item-desc">
            {{ formatCreated(item.created) }}
          </div>
        </article>

        <div v-if="!modelTags.length" class="catalog-empty">
          <div class="catalog-empty-title">还没有可展示的模型目录</div>
          <div class="catalog-empty-desc">
            请确认 API Base 与部署环境中的 LLM Secret 正确，或在关闭自动同步时手动填写默认模型。
          </div>
        </div>
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useMessage } from 'naive-ui';
import {
  fetchAdminChatConfig,
  refreshAdminChatModels,
  updateAdminChatConfig
} from '../api/chat';

const message = useMessage();
const loading = ref(false);
const saving = ref(false);
const syncing = ref(false);
const error = ref('');
const config = ref(null);

const form = reactive({
  apiBase: '',
  defaultModel: '',
  enableLlmPlan: true,
  autoDiscoverModels: true
});

const modelTags = computed(() => config.value?.catalog?.models || []);
const catalogSource = computed(() => config.value?.catalog?.source || 'fallback');
const providerStatusText = computed(() => (config.value?.configured ? '配置已生效' : '等待接入'));
const providerStatusHint = computed(() => {
  if (!config.value?.configured) return '请配置 API Base，并通过部署平台 Secret 注入 API Key。';
  if (config.value?.autoDiscoverModels) return '管理端已启用自动同步，可按目录结果选择默认模型。';
  return '当前为手动模型策略，用户侧会直接使用这里配置的默认模型。';
});
const catalogSummary = computed(() => `${modelTags.value.length} 个模型 / ${catalogSourceText.value}`);
const configSourceSummary = computed(() => {
  const source = config.value?.source || {};
  return [
    `API Base：${formatSource(source.apiBase)}`,
    `API Key：${formatSource(source.apiKey)}`,
    `默认模型：${formatSource(source.defaultModel)}`
  ].join(' / ');
});
const catalogSourceText = computed(() => {
  if (catalogSource.value === 'remote') return '远端同步';
  if (catalogSource.value === 'manual') return '手动策略';
  return '默认回退';
});
const updatedAtText = computed(() => {
  const value = config.value?.updatedAt;
  if (!value) return '尚未通过管理端保存';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', { hour12: false });
});
const policyCards = computed(() => [
  {
    title: '用户端策略',
    tag: '只读',
    tagType: 'info',
    desc: '用户在分级前后都不直接切换模型，只展示系统分配结果与当前状态。'
  },
  {
    title: '计划生成策略',
    tag: form.enableLlmPlan ? '开启' : '关闭',
    tagType: form.enableLlmPlan ? 'success' : 'warning',
    desc: form.enableLlmPlan
      ? 'PlanAgent 会读取当前默认模型，生成更完整的学习计划。'
      : 'PlanAgent 已回退到规则计划，适合调试和限流场景。'
  },
  {
    title: '模型目录策略',
    tag: form.autoDiscoverModels ? '自动' : '手动',
    tagType: form.autoDiscoverModels ? 'success' : 'default',
    desc: form.autoDiscoverModels
      ? '系统会定期和按需刷新第三方模型目录，管理端可直接挑选默认模型。'
      : '系统不再远端拉取目录，仅使用这里手动指定的默认模型。'
  }
]);

function applyConfig(data) {
  config.value = data;
  form.apiBase = data?.apiBase || '';
  form.defaultModel = data?.defaultModel || '';
  form.enableLlmPlan = data?.enableLlmPlan !== false;
  form.autoDiscoverModels = data?.autoDiscoverModels !== false;
}

async function loadConfig(refresh = false) {
  loading.value = true;
  error.value = '';
  try {
    const data = await fetchAdminChatConfig(refresh);
    applyConfig(data);
  } catch (err) {
    console.error(err);
    error.value = '模型配置加载失败，请确认 agent-platform 已启动。';
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  saving.value = true;
  error.value = '';
  try {
    const data = await updateAdminChatConfig({
      apiBase: form.apiBase,
      defaultModel: form.defaultModel,
      enableLlmPlan: form.enableLlmPlan,
      autoDiscoverModels: form.autoDiscoverModels
    });
    applyConfig(data);
    message.success('模型配置已保存');
  } catch (err) {
    console.error(err);
    error.value = err.message || '模型配置保存失败';
    message.error('模型配置保存失败');
  } finally {
    saving.value = false;
  }
}

async function handleRefreshCatalog() {
  syncing.value = true;
  error.value = '';
  try {
    const data = await refreshAdminChatModels();
    applyConfig(data);
    message.success('模型目录已同步');
  } catch (err) {
    console.error(err);
    error.value = err.message || '模型目录同步失败';
    message.error('模型目录同步失败');
  } finally {
    syncing.value = false;
  }
}

function resetForm() {
  if (config.value) {
    applyConfig(config.value);
  }
}

function pickModel(modelId) {
  form.defaultModel = modelId;
}

function formatSource(value) {
  if (value === 'runtime') return '运行时配置';
  if (value === 'default') return '系统默认';
  return '本地文件 / 环境变量';
}

function formatCreated(value) {
  if (!value) return '未返回创建时间';
  const normalized = Number(value);
  const date = Number.isFinite(normalized) && normalized > 0
    ? new Date(normalized * 1000)
    : new Date(value);
  if (Number.isNaN(date.getTime())) return `创建时间：${value}`;
  return `创建时间：${date.toLocaleString('zh-CN', { hour12: false })}`;
}

onMounted(() => {
  loadConfig();
});
</script>

<style scoped>
.admin-model-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(300px, 0.7fr);
  gap: 16px;
  padding: 22px 24px;
  border-radius: 24px;
  border: 1px solid rgba(17, 42, 59, 0.08);
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.82), transparent 35%),
    linear-gradient(135deg, #f9fcfc, #f1f6f4 55%, #eef3f8);
}

.hero-kicker,
.panel-kicker {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: #6f8191;
}

.hero-title {
  margin: 6px 0 0;
  font-family: 'Georgia', 'Times New Roman', serif;
  font-size: 32px;
  line-height: 1.08;
  color: #102235;
}

.hero-subtitle {
  margin: 10px 0 0;
  max-width: 860px;
  font-size: 13px;
  line-height: 1.8;
  color: #607483;
}

.hero-chip-row {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-status-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, #173850, #2b6661 125%);
  color: #f8fafc;
  box-shadow: 0 16px 28px rgba(15, 41, 64, 0.16);
}

.status-label {
  font-size: 12px;
  color: rgba(243, 249, 251, 0.74);
}

.status-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
}

.status-meta {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(243, 249, 251, 0.84);
}

.status-facts {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.status-fact {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
}

.status-fact-label {
  display: block;
  font-size: 12px;
  color: rgba(243, 249, 251, 0.74);
}

.status-fact-value {
  display: block;
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
}

.page-error {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 248, 235, 0.92);
  border: 1px solid rgba(245, 158, 11, 0.24);
  color: #8a5a08;
  font-size: 12px;
}

.page-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 14px;
}

.config-card,
.snapshot-card,
.catalog-card {
  border-radius: 20px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
  color: #102235;
}

.panel-note {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.7;
}

.form-grid {
  display: grid;
  gap: 12px;
}

.form-grid-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.field-hint-row {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.field-hint,
.action-hint,
.empty-inline {
  font-size: 12px;
  color: #738496;
}

.strategy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.strategy-card,
.snapshot-highlight,
.fact-card,
.policy-card,
.catalog-item,
.catalog-empty {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcfd, #f7fafb);
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.strategy-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.strategy-title,
.suggestion-title,
.policy-card-title,
.catalog-item-title,
.catalog-empty-title {
  font-size: 15px;
  font-weight: 700;
  color: #102235;
}

.strategy-desc,
.suggestion-desc,
.snapshot-desc,
.policy-card-desc,
.catalog-item-desc,
.catalog-empty-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.75;
  color: #607483;
}

.suggestion-block {
  margin-top: 8px;
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f9fbfb, #f2f7f5);
  border: 1px solid rgba(19, 84, 90, 0.1);
}

.suggestion-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.tag-cloud {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-row {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.snapshot-stack,
.policy-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.snapshot-label,
.fact-label {
  font-size: 12px;
  color: #738496;
}

.snapshot-value,
.fact-value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: #102235;
}

.fact-value-mono {
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    'Liberation Mono',
    'Courier New',
    monospace;
}

.fact-grid,
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.policy-card-top,
.catalog-item-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.catalog-item {
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.catalog-item:hover,
.catalog-item-active {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px rgba(17, 42, 59, 0.1);
  border-color: rgba(43, 102, 97, 0.28);
}

.catalog-item-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #738496;
}

.catalog-empty {
  grid-column: 1 / -1;
  text-align: center;
}

@media (max-width: 1100px) {
  .hero-panel,
  .page-grid,
  .fact-grid,
  .catalog-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .panel-head,
  .action-row,
  .field-hint-row,
  .suggestion-head {
    flex-direction: column;
    align-items: stretch;
  }

  .strategy-grid,
  .form-grid-2,
  .fact-grid,
  .catalog-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .strategy-card,
  .policy-card-top,
  .catalog-item-top {
    flex-direction: column;
  }
}
</style>
