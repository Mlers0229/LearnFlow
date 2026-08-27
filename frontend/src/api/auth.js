import { API_BASE_URL } from './config';
import { apiFetch, clearAccessToken, refreshAccessToken } from './client';
import { apiErrorFromResponse } from '../shared/api/errors';

/**
 * 用户注册。
 * @param {{username:string,password:string,email?:string,level?:string}} payload
 */
export async function register(payload) {
  const res = await apiFetch(`${API_BASE_URL}/api/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }, false);

  if (!res.ok) {
    throw await apiErrorFromResponse(res, '注册失败');
  }

  return res.json();
}

/**
 * 用户登录。
 * @param {{username:string,password:string}} payload
 */
export async function login(payload) {
  const res = await apiFetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }, false);

  if (!res.ok) {
    throw await apiErrorFromResponse(res, '登录失败');
  }

  return res.json();
}

/**
 * 更新个人信息（邮箱、学习水平、可选修改密码）
 * @param {{email?:string,level?:string,oldPassword?:string,newPassword?:string}} payload
 */
export async function updateProfile(payload) {
  const res = await apiFetch(`${API_BASE_URL}/api/auth/profile`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw await apiErrorFromResponse(res, '更新个人信息失败');
  }

  return res.json();
}

export async function restoreSession() {
  return refreshAccessToken();
}

export async function logoutSession() {
  try {
    await apiFetch(`${API_BASE_URL}/api/auth/logout`, { method: 'POST' }, false);
  } finally {
    clearAccessToken();
  }
}

export async function requestPasswordReset(payload) {
  const response = await apiFetch(`${API_BASE_URL}/api/auth/password-reset/request`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }, false);
  if (!response.ok) {
    throw await apiErrorFromResponse(response, '密码重置请求失败');
  }
}

export async function confirmPasswordReset(payload) {
  const response = await apiFetch(`${API_BASE_URL}/api/auth/password-reset/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }, false);
  if (!response.ok) {
    throw await apiErrorFromResponse(response, '密码重置失败');
  }
}
