import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { buildLearningContextPrompt, extractChatSources, useAiChat } from './useAiChat';

function createApi(overrides = {}) {
  return {
    fetchModels: vi.fn().mockResolvedValue({ source: 'remote', defaultModel: 'deepseek-chat', models: [] }),
    stream: vi.fn().mockImplementation(async (_messages, onChunk) => {
      onChunk('先理解概念，');
      onChunk('再完成练习。');
    }),
    ...overrides
  };
}

describe('useAiChat', () => {
  it('streams an answer with explicit learning context', async () => {
    const api = createApi();
    const chat = useAiChat({ isLoggedIn: ref(true), api });
    chat.inputText.value = '帮我复习今天的内容';

    const result = await chat.send({
      planTitle: 'Spring Boot 进阶',
      dayTitle: '自动配置',
      tasks: ['理解条件注解'],
      resources: [{ title: '官方文档', url: 'https://spring.io/guides' }]
    });

    expect(result).toEqual({ ok: true });
    expect(chat.messages.value.at(-1)).toMatchObject({ role: 'assistant', content: '先理解概念，再完成练习。', status: 'complete' });
    expect(api.stream.mock.calls[0][0][0]).toMatchObject({ role: 'system' });
    expect(api.stream.mock.calls[0][0][0].content).toContain('Spring Boot 进阶');
  });

  it('marks an active answer as stopped without presenting it as a failure', async () => {
    const api = createApi({
      stream: vi.fn().mockImplementation((_messages, onChunk, signal) => new Promise((resolve, reject) => {
        onChunk('已经生成一部分');
        signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      }))
    });
    const chat = useAiChat({ isLoggedIn: ref(true), api });
    chat.inputText.value = '生成一个长回答';

    const sending = chat.send();
    await vi.waitFor(() => expect(chat.loading.value).toBe(true));
    chat.stop();
    const result = await sending;

    expect(result).toEqual({ ok: false, reason: 'stopped' });
    expect(chat.messages.value.at(-1)).toMatchObject({ content: '已经生成一部分', status: 'stopped' });
  });

  it('retries a failed answer from its original user message', async () => {
    const api = createApi({
      stream: vi.fn()
        .mockRejectedValueOnce(new Error('upstream unavailable'))
        .mockImplementationOnce(async (_messages, onChunk) => onChunk('重试成功'))
    });
    const chat = useAiChat({ isLoggedIn: ref(true), api });
    chat.inputText.value = '解释依赖注入';
    await chat.send();
    const failed = chat.messages.value.at(-1)!;

    expect(failed.status).toBe('error');
    expect(await chat.regenerate(failed.id)).toEqual({ ok: true });
    expect(chat.messages.value.at(-1)).toMatchObject({ content: '重试成功', status: 'complete' });
  });
});

describe('chat source helpers', () => {
  it('deduplicates safe Markdown links and builds a transparent context prompt', () => {
    expect(extractChatSources('[文档](https://example.com/docs) 与 [重复](https://example.com/docs)')).toEqual([
      { title: '文档', url: 'https://example.com/docs', hostname: 'example.com' }
    ]);
    expect(buildLearningContextPrompt({ dayTitle: '自动配置', tasks: ['完成条件注解练习'] })).toContain('完成条件注解练习');
  });
});
