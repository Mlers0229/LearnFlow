import { computed, reactive, ref } from 'vue';
import { fetchAdminChatConfig, refreshAdminChatModels, updateAdminChatConfig } from '../../../api/chat';
import { connectionResult, draftFromConfig, sameDraft, syncResult, type AdminModelConfig, type ModelConfigDraft, type OperationResult } from './modelConfig';

export function useAdminModelConfig() {
  const config = ref<AdminModelConfig | null>(null);
  const savedDraft = ref<ModelConfigDraft>(draftFromConfig());
  const form = reactive<ModelConfigDraft>(draftFromConfig());
  const loading = ref(false);
  const saving = ref(false);
  const syncing = ref(false);
  const testing = ref(false);
  const error = ref('');
  const saveState = ref<'idle' | 'saved' | 'error'>('idle');
  const syncState = ref<OperationResult | null>(null);
  const connectionState = ref<OperationResult | null>(null);
  const dirty = computed(() => !sameDraft(form, savedDraft.value));
  const models = computed(() => config.value?.catalog?.models ?? []);

  function applyConfig(data: AdminModelConfig) {
    config.value = data;
    const next = draftFromConfig(data);
    Object.assign(form, next);
    savedDraft.value = { ...next };
  }

  async function load() {
    loading.value = true;
    error.value = '';
    try { applyConfig(await fetchAdminChatConfig()); }
    catch { error.value = '模型配置加载失败，请确认 agent-platform 服务可用。'; }
    finally { loading.value = false; }
  }

  async function save() {
    if (!dirty.value || saving.value) return;
    saving.value = true;
    saveState.value = 'idle';
    error.value = '';
    try {
      applyConfig(await updateAdminChatConfig({
        apiBase: form.apiBase.trim().replace(/\/$/, ''),
        defaultModel: form.defaultModel.trim(),
        enableLlmPlan: form.enableLlmPlan,
        autoDiscoverModels: form.autoDiscoverModels
      }));
      syncState.value = null;
      connectionState.value = null;
      saveState.value = 'saved';
    } catch {
      saveState.value = 'error';
      error.value = '配置保存失败，当前运行配置未更改。';
    } finally { saving.value = false; }
  }

  async function syncCatalog() {
    if (dirty.value) {
      syncState.value = { tone: 'warning', title: '请先保存配置', detail: '目录同步只读取服务器当前生效配置，请先保存未提交的更改。' };
      return;
    }
    syncing.value = true;
    syncState.value = null;
    try {
      const data = await refreshAdminChatModels();
      applyConfig(data);
      syncState.value = syncResult(data);
    } catch { syncState.value = { tone: 'error', title: '目录同步失败', detail: '服务请求失败，请检查网络后重试。' }; }
    finally { syncing.value = false; }
  }

  async function testConnection() {
    if (dirty.value) {
      connectionState.value = { tone: 'warning', title: '请先保存配置', detail: '连接测试只使用服务器当前生效的 API Base 与 Secret。' };
      return;
    }
    testing.value = true;
    connectionState.value = null;
    try {
      const data = await fetchAdminChatConfig(true);
      config.value = data;
      connectionState.value = connectionResult(data);
    } catch { connectionState.value = { tone: 'error', title: '连接测试失败', detail: '无法访问配置服务，请检查 agent-platform 状态。' }; }
    finally { testing.value = false; }
  }

  function reset() { Object.assign(form, savedDraft.value); saveState.value = 'idle'; }
  function pickModel(id: string) { form.defaultModel = id; saveState.value = 'idle'; }

  return { config, form, loading, saving, syncing, testing, error, saveState, syncState, connectionState, dirty, models, load, save, syncCatalog, testConnection, reset, pickModel };
}
