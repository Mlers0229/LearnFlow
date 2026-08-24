import { API_BASE_URL } from './config';
import { apiFetch, clearAccessToken } from './client';

function idempotencyKey(prefix) {
  const value = globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}:${value}`;
}

export async function createPrivacyExport() {
  const response = await apiFetch(`${API_BASE_URL}/api/privacy/exports`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey('export') }
  });
  if (!response.ok) throw new Error(`数据导出请求失败，状态码：${response.status}`);
  return response.json();
}

export async function getPrivacyRequest(requestId) {
  const response = await apiFetch(`${API_BASE_URL}/api/privacy/requests/${encodeURIComponent(requestId)}`, {
    headers: { Accept: 'application/json' }
  });
  if (!response.ok) throw new Error(`隐私请求查询失败，状态码：${response.status}`);
  return response.json();
}

export async function downloadPrivacyExport(requestId) {
  const response = await apiFetch(
    `${API_BASE_URL}/api/privacy/exports/${encodeURIComponent(requestId)}/download`,
    { headers: { Accept: 'application/json' } }
  );
  if (!response.ok) throw new Error(`数据导出下载失败，状态码：${response.status}`);
  const disposition = response.headers.get('Content-Disposition') || '';
  const match = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  return {
    blob: await response.blob(),
    filename: match ? decodeURIComponent(match[1]) : `learnflow-data-export-${requestId}.json`
  };
}

export async function requestAccountErasure(payload) {
  const response = await apiFetch(`${API_BASE_URL}/api/privacy/erasure`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey('erasure')
    },
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new Error(`账户注销请求失败，状态码：${response.status}`);
  clearAccessToken();
  return response.json();
}
