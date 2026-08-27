import { describe, expect, it, vi } from 'vitest';
import { usePlanGenerationTask } from './usePlanGenerationTask';

const payload = {
  goalText: '学习 Java',
  durationWeeks: 8,
  hoursPerDay: 1,
  level: 'beginner' as const,
  targetRole: null,
  preferredStyle: 'balanced' as const,
  finalDeliverable: null,
  constraints: []
};

function createApi(overrides = {}) {
  return {
    createTask: vi.fn().mockResolvedValue({ id: 'task-1', status: 'PENDING', progress: 5 }),
    getTask: vi.fn().mockResolvedValue({
      id: 'task-1',
      status: 'SUCCEEDED',
      progress: 100,
      resultResourceId: 42
    }),
    getPlan: vi.fn().mockResolvedValue({ id: 42, title: 'Java 学习计划' }),
    recentPlans: vi.fn().mockResolvedValue([]),
    pauseTask: vi.fn().mockResolvedValue({ id: 'task-1', status: 'PAUSED', progress: 35 }),
    resumeTask: vi.fn().mockResolvedValue({ id: 'task-1', status: 'RUNNING', progress: 35 }),
    cancelTask: vi.fn().mockResolvedValue({ id: 'task-1', status: 'CANCELLED', progress: 35 }),
    ...overrides
  };
}

describe('usePlanGenerationTask', () => {
  it('creates, polls and resolves a durable generation task', async () => {
    const api = createApi();
    const task = usePlanGenerationTask({ api, pollIntervalMs: 0 });

    const result = await task.start(payload);

    expect(result).toMatchObject({ id: 42 });
    expect(task.phase.value).toBe('succeeded');
    expect(task.progress.value).toBe(100);
    expect(api.createTask).toHaveBeenCalledOnce();
    expect(api.getTask).toHaveBeenCalledWith('task-1');
  });

  it('exposes a stable failure state when a durable task fails', async () => {
    const api = createApi({
      getTask: vi.fn().mockResolvedValue({
        id: 'task-1',
        status: 'FAILED',
        progress: 60,
        errorCode: 'TASK_MODEL_UNAVAILABLE'
      })
    });
    const task = usePlanGenerationTask({ api, pollIntervalMs: 0 });

    await task.start(payload);

    expect(task.phase.value).toBe('failed');
    expect(task.error.value).toContain('TASK_MODEL_UNAVAILABLE');
  });

  it('keeps the paused state when an older running poll arrives later', async () => {
    const api = createApi({
      getTask: vi.fn().mockResolvedValue({ id: 'task-1', status: 'RUNNING', progress: 28 })
    });
    const task = usePlanGenerationTask({ api, pollIntervalMs: 10 });
    const running = task.start(payload);

    await vi.waitFor(() => expect(task.taskId.value).toBe('task-1'));
    await task.pause();
    await new Promise((resolve) => setTimeout(resolve, 25));

    expect(task.phase.value).toBe('paused');
    expect(task.canResume.value).toBe(true);

    await task.cancel();
    await running;
  });
});
