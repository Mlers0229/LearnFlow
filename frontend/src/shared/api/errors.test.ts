import { describe, expect, it } from 'vitest';
import { ApiError, apiErrorFromResponse, getUserFacingError, networkError } from './errors';

describe('API error model', () => {
  it('classifies an authentication response and preserves the server message', async () => {
    const response = new Response(JSON.stringify({ code: 'AUTH_FAILED', message: '凭据无效' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    });

    const error = await apiErrorFromResponse(response, '登录失败');

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe('authentication');
    expect(error.code).toBe('AUTH_FAILED');
    expect(getUserFacingError(error)).toBe('凭据无效');
  });

  it('normalizes a fetch failure into a network error', () => {
    const error = networkError(new TypeError('fetch failed'));

    expect(error.kind).toBe('network');
    expect(getUserFacingError(error)).toContain('网络连接失败');
  });
});
