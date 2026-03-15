import { API_BASE_URL } from './config';

export async function createResource(payload) {
  const res = await fetch(`${API_BASE_URL}/api/resources`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`Create resource failed: ${res.status}`);
  }

  return res.json();
}

export async function listResources() {
  const res = await fetch(`${API_BASE_URL}/api/resources`);

  if (!res.ok) {
    throw new Error(`List resources failed: ${res.status}`);
  }

  return res.json();
}

export async function listMyResources(params = {}) {
  const query = new URLSearchParams();
  if (params.userId !== undefined && params.userId !== null && params.userId !== '') {
    query.set('userId', String(params.userId));
  }
  if (params.username) {
    query.set('username', String(params.username));
  }

  const suffix = query.toString() ? `?${query.toString()}` : '';
  const res = await fetch(`${API_BASE_URL}/api/resources/mine${suffix}`);

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
  const res = await fetch(`${API_BASE_URL}/api/resources/${id}/feedback`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`Submit resource feedback failed: ${res.status}`);
  }
}
