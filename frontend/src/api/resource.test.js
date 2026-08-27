import { beforeEach, describe, expect, it, vi } from 'vitest';

import { clearAccessToken } from './client';
import { deleteResource, getResourceFeedbacks, submitResourceDocument, submitResourceText, submitResourceUrl, updateResourceStatus } from './resource';

describe('resource ingestion API', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('submits URL and text sources with explicit idempotency keys', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ingestionId: 'ingestion-url' }), { status: 202, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ingestionId: 'ingestion-text' }), { status: 202, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await submitResourceUrl({ title: 'Docs', url: 'https://example.com/docs', domain: 'java' }, 'url-key');
    await submitResourceText({ title: 'Notes', text: 'safe notes', domain: 'java' }, 'text-key');

    expect(fetchMock.mock.calls[0][1].headers.get('Idempotency-Key')).toBe('url-key');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body).text).toBe('safe notes');
  });

  it('uses multipart without manually setting a content type for documents', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ingestionId: 'document' }), { status: 202, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);
    const file = new File(['document'], 'lesson.txt', { type: 'text/plain' });

    await submitResourceDocument({ title: 'Lesson', domain: 'java' }, file, 'document-key');

    const request = fetchMock.mock.calls[0][1];
    expect(request.body).toBeInstanceOf(FormData);
    expect(request.headers.get('Content-Type')).toBeNull();
    expect(request.headers.get('Idempotency-Key')).toBe('document-key');
  });

  it('loads recent feedback with a bounded limit', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([{ id: 1, rating: 4 }]), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    const feedbacks = await getResourceFeedbacks(42, 500);

    expect(feedbacks).toEqual([{ id: 1, rating: 4 }]);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/resources/42/feedbacks?limit=100');
  });

  it('deletes a submitted resource with an authenticated API request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await deleteResource(42);

    expect(fetchMock.mock.calls[0][0]).toContain('/api/resources/42');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'DELETE' });
  });

  it('preserves the backend activation conflict message', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'RESOURCE_INGESTION_NOT_READY',
      message: '资源摄取成功后才能上线；请先重新摄取或更换可访问的来源'
    }), { status: 409, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(updateResourceStatus(1, 'ACTIVE')).rejects.toMatchObject({
      status: 409,
      code: 'RESOURCE_INGESTION_NOT_READY',
      message: '资源摄取成功后才能上线；请先重新摄取或更换可访问的来源'
    });
  });
});
