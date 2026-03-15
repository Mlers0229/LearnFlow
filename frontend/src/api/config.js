const isBrowser = typeof window !== 'undefined';
const browserOrigin = isBrowser ? window.location.origin : '';
const isLocalViteDev =
  browserOrigin === 'http://localhost:5173' || browserOrigin === 'http://127.0.0.1:5173';

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function resolveApiBaseUrl() {
  const envValue = import.meta.env.VITE_API_BASE_URL;
  if (envValue) {
    return stripTrailingSlash(envValue);
  }
  if (isLocalViteDev) {
    return 'http://localhost:18081';
  }
  if (browserOrigin) {
    return stripTrailingSlash(browserOrigin);
  }
  return 'http://localhost:18081';
}

function resolveChatApiBaseUrl() {
  const envValue = import.meta.env.VITE_CHAT_API_BASE_URL;
  if (envValue) {
    return stripTrailingSlash(envValue);
  }
  if (isLocalViteDev) {
    return 'http://localhost:8000';
  }
  if (browserOrigin) {
    return `${stripTrailingSlash(browserOrigin)}/agent`;
  }
  return 'http://localhost:8000';
}

export const API_BASE_URL = resolveApiBaseUrl();
export const CHAT_API_BASE_URL = resolveChatApiBaseUrl();
