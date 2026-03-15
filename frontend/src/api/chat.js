import { API_BASE_URL, CHAT_API_BASE_URL } from './config';

export async function fetchChatModels(refresh = false) {
  const suffix = refresh ? '?refresh=true' : '';
  const res = await fetch(`${API_BASE_URL}/api/chat/models${suffix}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json'
    },
    cache: 'no-store'
  });

  if (!res.ok) {
    throw new Error(`获取模型列表失败，状态码：${res.status}`);
  }

  return res.json();
}

export async function fetchAdminChatConfig(refresh = false) {
  const suffix = refresh ? '?refresh=true' : '';
  const res = await fetch(`${API_BASE_URL}/api/chat/admin-config${suffix}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json'
    },
    cache: 'no-store'
  });

  if (!res.ok) {
    throw new Error(`获取模型配置失败，状态码：${res.status}`);
  }

  return res.json();
}

export async function updateAdminChatConfig(payload) {
  const res = await fetch(`${API_BASE_URL}/api/chat/admin-config`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    cache: 'no-store',
    body: JSON.stringify(payload || {})
  });

  if (!res.ok) {
    throw new Error(`保存模型配置失败，状态码：${res.status}`);
  }

  return res.json();
}

export async function refreshAdminChatModels() {
  const res = await fetch(`${API_BASE_URL}/api/chat/admin-config/refresh-models`, {
    method: 'POST',
    headers: {
      Accept: 'application/json'
    },
    cache: 'no-store'
  });

  if (!res.ok) {
    throw new Error(`刷新模型目录失败，状态码：${res.status}`);
  }

  return res.json();
}

export async function streamChat(messages, onChunk, signal, model) {
  const res = await fetch(`${CHAT_API_BASE_URL}/api/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    cache: 'no-store',
    body: JSON.stringify({ messages, model: model || null }),
    signal
  });

  if (!res.ok || !res.body) {
    throw new Error(`流式对话接口返回异常，状态码：${res.status}`);
  }

  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    if (value) {
      buffer += value;
      let sepIndex;
      while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, sepIndex);
        buffer = buffer.slice(sepIndex + 2);
        if (!rawEvent.startsWith('data:')) continue;
        const data = rawEvent.slice(5).trimStart();
        if (data === '[DONE]') return;
        onChunk(data.replace(/\\n/g, '\n'));
      }
    }

    await new Promise(requestAnimationFrame);
  }
}
