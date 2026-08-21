import { beforeEach, describe, expect, it, vi } from 'vitest';

import { apiFetch, clearAccessToken, setAccessToken } from './client';

describe('API client authentication recovery', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('refreshes once after 401 and retries with the new access token', async () => {
    setAccessToken('expired-token');
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'fresh-token' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await apiFetch('/api/plans');

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer expired-token');
    expect(fetchMock.mock.calls[2][1].headers.get('Authorization')).toBe('Bearer fresh-token');
  });
});
