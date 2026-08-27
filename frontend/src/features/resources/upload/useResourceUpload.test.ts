import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  getResourceIngestion: vi.fn(),
  listMyResources: vi.fn(),
  reingestResourceUrl: vi.fn(),
  submitResourceDocument: vi.fn(),
  submitResourceText: vi.fn(),
  submitResourceUrl: vi.fn(),
}))

vi.mock('../../../api/resource', () => api)

import { useResourceUpload } from './useResourceUpload'

describe('useResourceUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listMyResources.mockResolvedValue([])
  })

  it('submits backend-compatible fields and polls through success', async () => {
    api.submitResourceUrl.mockResolvedValue({ resourceId: 42, ingestionId: 'ingestion-42' })
    api.getResourceIngestion
      .mockResolvedValueOnce({ status: 'PROCESSING' })
      .mockResolvedValueOnce({ status: 'SUCCEEDED' })

    const upload = useResourceUpload({ pollInterval: 0, maxPolls: 3 })
    Object.assign(upload.form, {
      url: 'https://example.com/course', title: '课程资料', domain: '编程开发',
      level: '入门', estimatedMinutes: 45, tags: 'Web', rightsConfirmed: true,
    })

    await upload.submit()

    expect(api.submitResourceUrl).toHaveBeenCalledWith(expect.objectContaining({
      url: 'https://example.com/course', durationMinutes: 45, rightsConfirmed: true,
    }), expect.stringMatching(/^resource-/))
    expect(api.getResourceIngestion).toHaveBeenCalledTimes(2)
    expect(upload.progress.value).toMatchObject({ phase: 'success', percent: 100 })
    expect(api.listMyResources).toHaveBeenCalledOnce()
  })

  it('exposes a useful reason when ingestion fails', async () => {
    api.submitResourceText.mockResolvedValue({ resourceId: 43, ingestionId: 'ingestion-43' })
    api.getResourceIngestion.mockResolvedValue({ status: 'FAILED', errorCode: 'PARSE_FAILED' })
    const upload = useResourceUpload({ pollInterval: 0, maxPolls: 1 })
    Object.assign(upload.form, {
      sourceType: 'TEXT', text: '学习正文', title: '正文资料', domain: '其他', rightsConfirmed: true,
    })

    await expect(upload.submit()).rejects.toThrow('内容解析失败')
    expect(upload.progress.value).toMatchObject({ phase: 'failed', errorCode: 'PARSE_FAILED' })
  })
})
