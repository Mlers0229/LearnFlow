import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  cancelAsyncTask,
  createPlanTask,
  getAsyncTask,
  getMasteryProfiles,
  markExerciseReviewed,
  pauseAsyncTask,
  recomputeMasteryProfiles,
  resumeAsyncTask
} from './plan';
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

  it('pauses and resumes the same owned task resource', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1', status: 'PAUSED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1', status: 'PENDING' }), {
        status: 202,
        headers: { 'Content-Type': 'application/json' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    expect((await pauseAsyncTask('task-1')).status).toBe('PAUSED');
    expect((await resumeAsyncTask('task-1')).status).toBe('PENDING');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/tasks/task-1/pause');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/tasks/task-1/resume');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
  });

  it('reads and explicitly recomputes the authenticated mastery projection', async () => {
    const payload = [{ knowledge_key: 'abc', mastery_score: 0.75, sample_count: 2 }];
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    const profiles = await getMasteryProfiles(10);
    const recomputed = await recomputeMasteryProfiles(10);
    await markExerciseReviewed(42);

    expect(profiles[0].masteryScore).toBe(0.75);
    expect(recomputed[0].sampleCount).toBe(2);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/mastery?limit=10');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/mastery/recompute?limit=10');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(fetchMock.mock.calls[2][0]).toContain('/api/exercise-records/42/review');
    expect(fetchMock.mock.calls[2][1].method).toBe('POST');
  });
});
