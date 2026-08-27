export interface ModelCatalogItem {
  id: string;
  label?: string;
  ownedBy?: string;
  created?: number | string | null;
}

export interface ModelCatalog {
  configured?: boolean;
  source?: 'remote' | 'manual' | 'fallback' | string;
  defaultModel?: string;
  message?: string;
  models?: ModelCatalogItem[];
}

export interface AdminModelConfig {
  configured?: boolean;
  apiBase?: string;
  defaultModel?: string;
  enableLlmPlan?: boolean;
  autoDiscoverModels?: boolean;
  hasApiKey?: boolean;
  maskedApiKey?: string;
  updatedAt?: string | null;
  source?: Record<string, string>;
  catalog?: ModelCatalog;
}

export interface ModelConfigDraft {
  apiBase: string;
  defaultModel: string;
  enableLlmPlan: boolean;
  autoDiscoverModels: boolean;
}

export interface OperationResult {
  tone: 'success' | 'error' | 'warning';
  title: string;
  detail: string;
}

export function draftFromConfig(config?: AdminModelConfig | null): ModelConfigDraft {
  return {
    apiBase: config?.apiBase ?? '',
    defaultModel: config?.defaultModel ?? '',
    enableLlmPlan: config?.enableLlmPlan !== false,
    autoDiscoverModels: config?.autoDiscoverModels !== false
  };
}

export function sameDraft(left: ModelConfigDraft, right: ModelConfigDraft): boolean {
  return left.apiBase.trim().replace(/\/$/, '') === right.apiBase.trim().replace(/\/$/, '')
    && left.defaultModel.trim() === right.defaultModel.trim()
    && left.enableLlmPlan === right.enableLlmPlan
    && left.autoDiscoverModels === right.autoDiscoverModels;
}

export function providerLabel(apiBase?: string | null): string {
  const value = String(apiBase ?? '').toLowerCase();
  if (value.includes('deepseek')) return 'DeepSeek';
  if (value.includes('openrouter')) return 'OpenRouter';
  if (value.includes('openai')) return 'OpenAI';
  return value ? 'OpenAI 兼容服务' : '尚未配置';
}

export function connectionResult(config: AdminModelConfig): OperationResult {
  if (!config.configured) return { tone: 'error', title: '凭据未就绪', detail: 'API Base 或服务器 Secret 尚未配置。' };
  if (config.catalog?.source === 'remote' && config.catalog.message === 'ok') {
    const count = config.catalog.models?.length ?? 0;
    return { tone: 'success', title: '连接成功', detail: `提供商已响应，共返回 ${count} 个模型。` };
  }
  if (config.catalog?.source === 'manual') return { tone: 'warning', title: '未执行远端探测', detail: '自动同步已关闭；开启并保存后再测试连接。' };
  return { tone: 'error', title: '连接失败', detail: '服务器未能从提供商读取模型目录，请检查 API Base、Secret 与网络。' };
}

export function syncResult(config: AdminModelConfig): OperationResult {
  const result = connectionResult(config);
  if (result.tone === 'success') return { ...result, title: '目录同步完成' };
  return { ...result, title: result.tone === 'warning' ? '目录同步已跳过' : '目录同步失败' };
}

export function formatConfigSource(source?: string): string {
  if (source === 'runtime') return '运行时配置';
  if (source === 'environment') return '部署 Secret';
  if (source === 'default') return '系统默认';
  return '配置文件 / 环境变量';
}
