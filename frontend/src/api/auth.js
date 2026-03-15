import { API_BASE_URL } from './config';

/**
 * 用户注册。
 * @param {{username:string,password:string,email?:string,level?:string}} payload
 */
export async function register(payload) {
  const res = await fetch(`${API_BASE_URL}/api/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`注册失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 用户登录。
 * @param {{username:string,password:string}} payload
 */
export async function login(payload) {
  const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`登录失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 更新个人信息（邮箱、学习水平、可选修改密码）
 * @param {{userId:number,email?:string,level?:string,oldPassword?:string,newPassword?:string}} payload
 */
export async function updateProfile(payload) {
  const res = await fetch(`${API_BASE_URL}/api/auth/profile`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`更新个人信息失败，状态码：${res.status}`);
  }

  return res.json();
}


