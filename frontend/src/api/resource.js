import { API_BASE_URL } from './config';
import { apiFetch as fetch } from './client';

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
  if (!res.ok) throw new Error(`Resource ingestion failed: ${res.status}`);
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

export async function updateResourceStatus(id, status) {
  const res = await fetch(
    `${API_BASE_URL}/api/resources/${id}/status?status=${encodeURIComponent(status)}`,
    {
      method: 'PATCH'
    }
  );

  if (!res.ok) {
    throw new Error(`Update resource status failed: ${res.status}`);
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
    throw new Error(`Batch update resource status failed: ${res.status}`);
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
