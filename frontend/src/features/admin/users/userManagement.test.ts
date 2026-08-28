import { describe, expect, it } from 'vitest'
import { auditForUser, filterUsers, mutationGuard, summarizeUsers, type AdminUserRecord } from './userManagement'

const users: AdminUserRecord[] = [
  { id: 1, username: 'Mlers', role: 'admin', status: 'ACTIVE', createdAt: '2026-08-20T00:00:00Z' },
  { id: 2, username: 'student', email: 'student@example.com', role: 'student', status: 'ACTIVE', createdAt: '2026-08-27T00:00:00Z' },
  { id: 3, username: 'disabled', role: 'student', status: 'DISABLED', createdAt: '2026-06-01T00:00:00Z' },
]

describe('user management rules', () => {
  it('filters users and computes operational totals', () => {
    expect(filterUsers(users, { keyword: 'example', role: '', status: '', registered: '' }).map((user) => user.id)).toEqual([2])
    expect(filterUsers(users, { keyword: '', role: '', status: '', registered: '30' }, new Date('2026-08-28T00:00:00Z')).map((user) => user.id)).toEqual([2, 1])
    expect(summarizeUsers(users)).toEqual({ total: 3, admins: 1, active: 2, disabled: 1 })
  })

  it('protects the current and last active administrator', () => {
    expect(mutationGuard(users, users[0], 1, { status: 'DISABLED' })).toContain('当前登录管理员')
    expect(mutationGuard(users, users[0], 99, { role: 'student' })).toContain('至少保留一个')
    expect(mutationGuard([...users, { id: 4, username: 'other', role: 'admin', status: 'ACTIVE' }], users[0], 99, { role: 'student' })).toBe('')
  })

  it('links audit rows to the selected user', () => {
    expect(auditForUser([{ id: 1, targetType: 'USER', targetId: 2 }, { id: 2, targetType: 'RESOURCE', targetId: 2 }], 2)).toHaveLength(1)
  })
})
