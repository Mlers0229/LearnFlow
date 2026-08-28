export type AdminUserRecord = {
  id: number
  username: string
  email?: string | null
  role: 'admin' | 'student' | string
  level?: string | null
  status: 'ACTIVE' | 'DISABLED' | string
  createdAt?: string | null
}

export type AdminAuditRecord = {
  id: number
  type?: string | null
  operator?: string | null
  targetType?: string | null
  targetId?: number | null
  detail?: string | null
  createdAt?: string | null
}

export type UserFilters = { keyword: string; role: string; status: string; registered: string }

export function filterUsers(users: AdminUserRecord[], filters: UserFilters, now = new Date()) {
  const keyword = filters.keyword.trim().toLowerCase()
  const days = Number(filters.registered || 0)
  const cutoff = days ? new Date(now.getTime() - days * 86_400_000) : null
  return [...users]
    .filter((user) => !keyword || `${user.username} ${user.email || ''}`.toLowerCase().includes(keyword))
    .filter((user) => !filters.role || user.role === filters.role)
    .filter((user) => !filters.status || user.status === filters.status)
    .filter((user) => !cutoff || (user.createdAt && new Date(user.createdAt) >= cutoff))
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')) || b.id - a.id)
}

export function summarizeUsers(users: AdminUserRecord[]) {
  return {
    total: users.length,
    admins: users.filter((user) => user.role === 'admin' && user.status === 'ACTIVE').length,
    active: users.filter((user) => user.status === 'ACTIVE').length,
    disabled: users.filter((user) => user.status === 'DISABLED').length,
  }
}

export function mutationGuard(users: AdminUserRecord[], user: AdminUserRecord, currentUserId: number | string | undefined, change: { role?: string; status?: string }) {
  const currentId = Number(currentUserId)
  const affectsPrivilege = (change.role && change.role !== 'admin') || change.status === 'DISABLED'
  if (user.id === currentId && affectsPrivilege) return '不能降低或禁用当前登录管理员的权限。'
  const activeAdmins = users.filter((item) => item.role === 'admin' && item.status === 'ACTIVE')
  if (user.role === 'admin' && user.status === 'ACTIVE' && activeAdmins.length <= 1 && affectsPrivilege) return '系统必须至少保留一个有效管理员。'
  return ''
}

export function auditForUser(audits: AdminAuditRecord[], userId: number) {
  return audits.filter((item) => item.targetType === 'USER' && Number(item.targetId) === userId).sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
}

export const roleLabel = (role?: string | null) => role === 'admin' ? '管理员' : '学习者'
export const statusLabel = (status?: string | null) => status === 'DISABLED' ? '已禁用' : '正常'
export const levelLabel = (level?: string | null) => ({ beginner: '入门', intermediate: '进阶', advanced: '高级' }[String(level || '')] || level || '未设置')
