import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createPrivacyExport,
  downloadPrivacyExport,
  getPrivacyRequest,
  requestAccountErasure
} from './privacy';
import { clearAccessToken } from './client';

describe('privacy request API', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('creates and polls an owned export request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'export-1', status: 'PENDING' }), {
        status: 202,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'export-1', status: 'SUCCEEDED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    await createPrivacyExport();
    await getPrivacyRequest('export-1');

    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[0][1].headers.get('Idempotency-Key')).toMatch(/^export:/);
    expect(fetchMock.mock.calls[1][0]).toContain('/api/privacy/requests/export-1');
  });

  it('downloads the bounded JSON artifact with server filename', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"schemaVersion":1}', {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Content-Disposition': 'attachment; filename="learnflow-export.json"'
      }
    }));
    vi.stubGlobal('fetch', fetchMock);

    const artifact = await downloadPrivacyExport('export-1');

    expect(artifact.filename).toBe('learnflow-export.json');
    expect(artifact.blob.type).toBe('application/json');
  });

  it('requires an idempotency key and explicit erasure payload', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'erase-1', type: 'ERASURE', status: 'PENDING'
    }), {
      status: 202,
      headers: { 'Content-Type': 'application/json' }
    }));
    vi.stubGlobal('fetch', fetchMock);

    await requestAccountErasure({ password: 'secret', confirmation: 'DELETE alice' });

    const request = fetchMock.mock.calls[0][1];
    expect(request.headers.get('Idempotency-Key')).toMatch(/^erasure:/);
    expect(JSON.parse(request.body)).toEqual({ password: 'secret', confirmation: 'DELETE alice' });
  });
});
