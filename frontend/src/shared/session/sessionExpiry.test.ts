import { beforeEach, describe, expect, it } from 'vitest'
import {
  closeSessionExpired,
  createLoginUrl,
  sessionExpiryState,
  showSessionExpired,
} from './sessionExpiry'

describe('session expiry state', () => {
  beforeEach(() => {
    sessionExpiryState.returnTo = '/'
    closeSessionExpired()
  })

  it('keeps the return path for signing in again', () => {
    showSessionExpired('/admin/users?status=ACTIVE')

    expect(sessionExpiryState.visible).toBe(true)
    expect(createLoginUrl()).toBe('/login?redirect=%2Fadmin%2Fusers%3Fstatus%3DACTIVE')
  })

  it('does not redirect back to the login page', () => {
    showSessionExpired('/login?redirect=/admin/users')
    expect(createLoginUrl()).toBe('/login?redirect=%2F')
  })
})
