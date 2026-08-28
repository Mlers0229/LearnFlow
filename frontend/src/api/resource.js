import { API_BASE_URL } from './config';
import { apiFetch as fetch } from './client';
import { apiErrorFromResponse } from '../shared/api/errors';

export async function createResource(payload) {
  const trustedPayload = { ...(payload || {}) };
  delete trustedPayload.uploaderUserId;
  delete trustedPayload.uploaderUsername;
  const res = await fetch(`${API_BASE_URL}/api/resources`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(trustedPayload)
  });

  if (!res.ok) {
    throw new Error(`Create resource failed: ${res.status}`);
  }

  return res.json();
}

function ingestionHeaders(idempotencyKey, json = true) {
  const headers = { 'Idempotency-Key': idempotencyKey || crypto.randomUUID() };
  if (json) headers['Content-Type'] = 'application/json';
  return headers;
}

async function parseIngestionResponse(res) {
  if (!res.ok) throw await apiErrorFromResponse(res, '资源摄取任务提交失败。');
  return res.json();
}

export async function submitResourceUrl(payload, idempotencyKey) {
  const res = await fetch(`${API_BASE_URL}/api/resources/ingestions/url`, {
    method: 'POST',
    headers: ingestionHeaders(idempotencyKey),
    body: JSON.stringify(payload)
  });
  return parseIngestionResponse(res);
}

export async function submitResourceText(payload, idempotencyKey) {
  const res = await fetch(`${API_BASE_URL}/api/resources/ingestions/text`, {
    method: 'POST',
    headers: ingestionHeaders(idempotencyKey),
    body: JSON.stringify(payload)
  });
  return parseIngestionResponse(res);
}

export async function submitResourceDocument(payload, file, idempotencyKey) {
  const body = new FormData();
  Object.entries(payload).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') body.append(key, String(value));
  });
  body.append('file', file);
  const res = await fetch(`${API_BASE_URL}/api/resources/ingestions/document`, {
    method: 'POST',
    headers: ingestionHeaders(idempotencyKey, false),
    body
  });
  return parseIngestionResponse(res);
}

export async function getResourceIngestion(ingestionId) {
  const res = await fetch(`${API_BASE_URL}/api/resources/ingestions/${encodeURIComponent(ingestionId)}`);
  if (!res.ok) throw new Error(`Get resource ingestion failed: ${res.status}`);
  return res.json();
}

export async function reingestResourceUrl(resourceId, url, idempotencyKey) {
  const res = await fetch(`${API_BASE_URL}/api/resources/${encodeURIComponent(resourceId)}/ingestions/url`, {
    method: 'POST',
    headers: ingestionHeaders(idempotencyKey),
    body: JSON.stringify({ content: url, rightsConfirmed: true })
  });
  return parseIngestionResponse(res);
}

export async function listResources() {
  const res = await fetch(`${API_BASE_URL}/api/resources`);

  if (!res.ok) {
    throw new Error(`List resources failed: ${res.status}`);
  }

  return res.json();
}

export async function listMyResources() {
  const res = await fetch(`${API_BASE_URL}/api/resources/mine`);

  if (!res.ok) {
    throw new Error(`List my resources failed: ${res.status}`);
  }

  return res.json();
}

function resourceFilename(contentDisposition, fallback) {
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition || '');
  if (encoded) {
    try { return decodeURIComponent(encoded[1]); } catch { /* use quoted fallback */ }
  }
  const quoted = /filename="([^"]+)"/i.exec(contentDisposition || '');
  return quoted?.[1] || fallback;
}

export async function getResourceSource(id) {
  const res = await fetch(`${API_BASE_URL}/api/resources/${encodeURIComponent(id)}/source`, {
    headers: { Accept: 'text/plain,application/pdf,application/octet-stream,*/*' }
  });
  if (!res.ok) throw await apiErrorFromResponse(res, '资源原件读取失败。');
  const contentType = res.headers.get('Content-Type') || 'application/octet-stream';
  return {
    blob: await res.blob(),
    filename: resourceFilename(res.headers.get('Content-Disposition'), `resource-${id}`),
    contentType,
    viewMode: res.headers.get('X-Resource-View-Mode') || (contentType.startsWith('text/') ? 'INLINE_TEXT' : contentType === 'application/pdf' ? 'INLINE_PDF' : 'DOWNLOAD'),
    sourceType: res.headers.get('X-Resource-Source-Type') || '',
    contentSha256: res.headers.get('X-Content-SHA256') || ''
  };
}

export async function deleteResource(id) {
  const res = await fetch(`${API_BASE_URL}/api/resources/${encodeURIComponent(id)}`, {
    method: 'DELETE'
  });
  if (!res.ok) {
    throw await apiErrorFromResponse(res, '资源删除失败。');
  }
}

export async function updateResourceStatus(id, status) {
  const res = await fetch(
    `${API_BASE_URL}/api/resources/${id}/status?status=${encodeURIComponent(status)}`,
    {
      method: 'PATCH'
    }
  );

  if (!res.ok) {
    throw await apiErrorFromResponse(res, '资源状态更新失败。');
  }
}

export async function batchUpdateResourceStatus(ids, status) {
  const res = await fetch(`${API_BASE_URL}/api/resources/batch/status`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ ids, status })
  });

  if (!res.ok) {
    throw await apiErrorFromResponse(res, '批量更新资源状态失败。');
  }
}

export async function updateResource(id, payload) {
  const res = await fetch(`${API_BASE_URL}/api/resources/${id}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`Update resource failed: ${res.status}`);
  }
}

export async function getResourceQualityStats() {
  const res = await fetch(`${API_BASE_URL}/api/resources/quality-stats`);

  if (!res.ok) {
    throw new Error(`Get resource quality stats failed: ${res.status}`);
  }

  return res.json();
}

export async function getResourceFeedbacks(id, limit = 20) {
  const safeLimit = Math.max(1, Math.min(100, Number(limit) || 20));
  const res = await fetch(`${API_BASE_URL}/api/resources/${encodeURIComponent(id)}/feedbacks?limit=${safeLimit}`);

  if (!res.ok) {
    throw new Error(`Get resource feedbacks failed: ${res.status}`);
  }

  return res.json();
}

export async function submitResourceFeedback(id, payload) {
  const trustedPayload = { ...(payload || {}) };
  delete trustedPayload.userId;
  const res = await fetch(`${API_BASE_URL}/api/resources/${id}/feedback`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(trustedPayload)
  });

  if (!res.ok) {
    throw new Error(`Submit resource feedback failed: ${res.status}`);
  }
}
