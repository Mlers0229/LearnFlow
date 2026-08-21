import { beforeEach, describe, expect, it, vi } from 'vitest';

import { cancelAsyncTask, createPlanTask, getAsyncTask } from './plan';
import { clearAccessToken } from './client';

describe('durable plan task API', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('submits a trusted payload with an idempotency key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'task-1',
      task_type: 'PLAN_GENERATION',
      status: 'PENDING',
      result_resource_id: null
    }), {
      status: 202,
      headers: { 'Content-Type': 'application/json' }
    }));
    vi.stubGlobal('fetch', fetchMock);

    const task = await createPlanTask({ goalText: 'Java', userId: 999 }, 'request-1');

    const request = fetchMock.mock.calls[0][1];
    expect(request.headers.get('Idempotency-Key')).toBe('request-1');
    expect(JSON.parse(request.body)).not.toHaveProperty('userId');
    expect(task.taskType).toBe('PLAN_GENERATION');
  });

  it('queries and cancels the same task resource', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1', status: 'RUNNING' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1', status: 'CANCELLED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    expect((await getAsyncTask('task-1')).status).toBe('RUNNING');
    expect((await cancelAsyncTask('task-1')).status).toBe('CANCELLED');
    expect(fetchMock.mock.calls[1][1].method).toBe('DELETE');
  });
});
