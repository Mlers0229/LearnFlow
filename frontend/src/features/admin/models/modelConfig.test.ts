import { describe, expect, it } from 'vitest';
import { connectionResult, draftFromConfig, providerLabel, sameDraft, syncResult } from './modelConfig';

describe('admin model configuration helpers', () => {
  it('creates a safe editable draft without an API key', () => {
    const draft = draftFromConfig({ apiBase: 'https://api.deepseek.com', defaultModel: 'deepseek-chat', hasApiKey: true });
    expect(draft).toEqual({ apiBase: 'https://api.deepseek.com', defaultModel: 'deepseek-chat', enableLlmPlan: true, autoDiscoverModels: true });
    expect(draft).not.toHaveProperty('apiKey');
  });

  it('ignores a trailing API Base slash when checking changes', () => {
    expect(sameDraft(
      { apiBase: 'https://api.example.com/', defaultModel: 'model-a', enableLlmPlan: true, autoDiscoverModels: true },
      { apiBase: 'https://api.example.com', defaultModel: 'model-a', enableLlmPlan: true, autoDiscoverModels: true }
    )).toBe(true);
  });

  it('recognizes common providers without requiring provider state', () => {
    expect(providerLabel('https://api.deepseek.com')).toBe('DeepSeek');
    expect(providerLabel('https://openrouter.ai/api')).toBe('OpenRouter');
    expect(providerLabel('https://models.example.com')).toBe('OpenAI 兼容服务');
  });

  it('does not report a fallback catalog as a successful connection', () => {
    expect(connectionResult({ configured: true, catalog: { source: 'fallback', models: [{ id: 'fallback' }] } }).tone).toBe('error');
    expect(syncResult({ configured: true, catalog: { source: 'remote', message: 'ok', models: [{ id: 'a' }] } }).tone).toBe('success');
  });
});
