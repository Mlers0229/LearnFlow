import { API_BASE_URL } from './config'
import { apiFetch } from './client'
import { apiErrorFromResponse } from '../shared/api/errors'
import type { AdminAuditRecord, AdminUserRecord } from '../features/admin/users/userManagement'

async function checked(response: Response, fallback: string) {
  if (!response.ok) throw await apiErrorFromResponse(response, fallback)
  return response
}

export async function listAdminUsers(): Promise<AdminUserRecord[]> {
  return (await checked(await apiFetch(`${API_BASE_URL}/api/admin/users`), '用户列表读取失败。')).json()
}

export async function listAdminAudits(): Promise<AdminAuditRecord[]> {
  return (await checked(await apiFetch(`${API_BASE_URL}/api/admin/audit/logs`), '用户审计记录读取失败。')).json()
}

export async function updateAdminUser(id: number, payload: { role?: string; status?: string }) {
  await checked(await apiFetch(`${API_BASE_URL}/api/admin/users/${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }), '用户状态更新失败。')
}

export async function createAdminUser(payload: { username: string; email?: string; role: string; level: string }): Promise<AdminUserRecord> {
  return (await checked(await apiFetch(`${API_BASE_URL}/api/admin/users`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }), '用户创建失败。')).json()
}

export async function resetAdminUserPassword(id: number) {
  return (await checked(await apiFetch(`${API_BASE_URL}/api/admin/users/${id}/reset-password`, { method: 'POST' }), '临时密码生成失败。')).text()
}
