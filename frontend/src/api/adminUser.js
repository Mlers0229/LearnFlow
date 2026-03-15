import { API_BASE_URL } from './config';

/**
 * 获取用户列表
 */
export async function listUsers() {
  const res = await fetch(`${API_BASE_URL}/api/admin/users`);
  if (!res.ok) {
    throw new Error(`获取用户列表失败，状态码：${res.status}`);
  }
  return res.json();
}

/**
 * 更新用户（角色 / 状态）
 * @param {number} id
 * @param {{role?: 'student'|'admin', status?: 'ACTIVE'|'DISABLED'}} payload
 */
export async function updateUser(id, payload) {
  const res = await fetch(`${API_BASE_URL}/api/admin/users/${id}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    throw new Error(`更新用户失败，状态码：${res.status}`);
  }
}

export async function createUser(payload) {
  const res = await fetch(`${API_BASE_URL}/api/admin/users`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    throw new Error(`创建用户失败，状态码：${res.status}`);
  }
  return res.json();
}

export async function resetPassword(id) {
  const res = await fetch(`${API_BASE_URL}/api/admin/users/${id}/reset-password`, {
    method: 'POST'
  });
  if (!res.ok) {
    throw new Error(`重置密码失败，状态码：${res.status}`);
  }
  return res.text();
}


