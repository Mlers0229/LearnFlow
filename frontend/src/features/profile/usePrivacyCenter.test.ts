import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  createPrivacyExport: vi.fn(),
  downloadPrivacyExport: vi.fn(),
  getPrivacyRequest: vi.fn(),
  requestAccountErasure: vi.fn(),
}))
vi.mock('../../api/privacy', () => api)

import { usePrivacyCenter } from './usePrivacyCenter'

describe('usePrivacyCenter', () => {
  beforeEach(() => vi.clearAllMocks())

  it('polls an export request until the download is ready', async () => {
    api.createPrivacyExport.mockResolvedValue({ id: 'export-8', status: 'PENDING' })
    api.getPrivacyRequest
      .mockResolvedValueOnce({ status: 'RUNNING', downloadReady: false })
      .mockResolvedValueOnce({ status: 'SUCCEEDED', downloadReady: true, artifactExpiresAt: '2026-08-28T00:00:00Z' })
    const privacy = usePrivacyCenter({ pollInterval: 0, maxPolls: 3 })

    await privacy.requestExport()

    expect(api.getPrivacyRequest).toHaveBeenCalledTimes(2)
    expect(privacy.progress).toMatchObject({ state: 'ready', percent: 100, requestId: 'export-8' })
  })

  it('shows a mapped export failure reason', async () => {
    api.createPrivacyExport.mockResolvedValue({ id: 'export-9' })
    api.getPrivacyRequest.mockResolvedValue({ status: 'FAILED', errorCode: 'STORAGE_FAILED' })
    const privacy = usePrivacyCenter({ pollInterval: 0, maxPolls: 1 })

    await expect(privacy.requestExport()).rejects.toThrow('暂时无法保存')
    expect(privacy.progress.state).toBe('failed')
  })

  it('requires all three erasure confirmations and sends no client identity', async () => {
    api.requestAccountErasure.mockResolvedValue({ id: 'erase-1', status: 'PENDING' })
    const privacy = usePrivacyCenter()
    privacy.setUsername('Mlers')
    privacy.erasure.password = 'current-password'
    privacy.erasure.confirmation = 'DELETE Mlers'
    privacy.erasure.impactConfirmed = true

    expect(privacy.canErase.value).toBe(true)
    await privacy.eraseAccount()
    expect(api.requestAccountErasure).toHaveBeenCalledWith({ password: 'current-password', confirmation: 'DELETE Mlers' })
  })
})
