import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  updateProfile: vi.fn(),
  setUser: vi.fn(),
  currentUser: { value: { id: 7, username: 'Mlers', email: 'old@example.com', level: 'beginner', role: 'student' } },
}))

vi.mock('../../api/auth', () => ({ updateProfile: mocks.updateProfile }))
vi.mock('../../store/auth', () => ({ useAuthStore: () => ({ currentUser: mocks.currentUser, setUser: mocks.setUser }) }))

import { useProfileSettings } from './useProfileSettings'

describe('useProfileSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.currentUser.value = { id: 7, username: 'Mlers', email: 'old@example.com', level: 'beginner', role: 'student' }
  })

  it('tracks unsaved account changes and saves only account fields', async () => {
    mocks.updateProfile.mockResolvedValue({ email: 'new@example.com', level: 'beginner' })
    const settings = useProfileSettings()
    settings.form.email = 'new@example.com'

    expect(settings.accountDirty.value).toBe(true)
    await settings.save('account')

    expect(mocks.updateProfile).toHaveBeenCalledWith({ email: 'new@example.com' })
    expect(settings.accountDirty.value).toBe(false)
    expect(settings.feedback.state).toBe('success')
  })

  it('requires a strong matching password before saving', async () => {
    const settings = useProfileSettings()
    settings.form.oldPassword = 'current-password'
    settings.form.newPassword = 'too-short'
    settings.form.confirmPassword = 'different'

    await expect(settings.save('security')).rejects.toThrow('至少需要 12 位')
    expect(mocks.updateProfile).not.toHaveBeenCalled()
    expect(settings.feedback.state).toBe('error')
  })

  it('clears password fields after a successful update', async () => {
    mocks.updateProfile.mockResolvedValue({ email: 'old@example.com', level: 'beginner' })
    const settings = useProfileSettings()
    settings.form.oldPassword = 'current-password'
    settings.form.newPassword = 'StrongPassword!42'
    settings.form.confirmPassword = 'StrongPassword!42'

    await settings.save('security')

    expect(mocks.updateProfile).toHaveBeenCalledWith({ oldPassword: 'current-password', newPassword: 'StrongPassword!42' })
    expect(settings.securityDirty.value).toBe(false)
  })
})
