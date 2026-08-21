import { API_BASE_URL } from './config';

let accessToken = null;
let refreshPromise = null;
let authenticationFailureHandler = null;

export function setAccessToken(value) {
  accessToken = value || null;
}

export function clearAccessToken() {
  accessToken = null;
}

export function onAuthenticationFailure(handler) {
  authenticationFailureHandler = typeof handler === 'function' ? handler : null;
}

function resolveUrl(input) {
  const value = String(input);
  return /^https?:\/\//i.test(value) ? value : `${API_BASE_URL}${value}`;
}

export async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json' }
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`会话刷新失败，状态码：${response.status}`);
        }
        const session = await response.json();
        setAccessToken(session.accessToken);
        return session;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function apiFetch(input, init = {}, allowRefresh = true) {
  const headers = new Headers(init.headers || {});
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  const response = await fetch(resolveUrl(input), {
    ...init,
    headers,
    credentials: 'include'
  });

  const isRefreshRequest = String(input).includes('/api/auth/refresh');
  if (response.status === 401 && allowRefresh && !isRefreshRequest) {
    try {
      await refreshAccessToken();
      return apiFetch(input, init, false);
    } catch (error) {
      clearAccessToken();
      if (authenticationFailureHandler) authenticationFailureHandler();
      throw error;
    }
  }
  return response;
}
