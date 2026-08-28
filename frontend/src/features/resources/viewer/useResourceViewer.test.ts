import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ getResourceSource: vi.fn() }))
vi.mock('../../../api/resource', () => api)

import { useResourceViewer } from './useResourceViewer'

describe('useResourceViewer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useResourceViewer().closeViewer()
  })

  it('renders authenticated text sources as plain text', async () => {
    api.getResourceSource.mockResolvedValue({
      blob: new Blob(['安全正文'], { type: 'text/plain' }),
      filename: 'notes.txt',
      contentType: 'text/plain;charset=UTF-8',
      viewMode: 'INLINE_TEXT',
    })
    const viewer = useResourceViewer()

    await viewer.openResource({ id: 7, title: '课堂笔记', sourceType: 'TEXT', ingestionStatus: 'SUCCEEDED' })

    expect(api.getResourceSource).toHaveBeenCalledWith(7)
    expect(viewer.state.mode).toBe('TEXT')
    expect(viewer.state.text).toBe('安全正文')
    expect(viewer.state.filename).toBe('notes.txt')
  })

  it('opens allowlisted external URLs without requesting stored content', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    const viewer = useResourceViewer()

    await viewer.openResource({ id: 8, title: '官方文档', sourceType: 'URL', url: 'https://example.com/docs' })

    expect(open).toHaveBeenCalledWith('https://example.com/docs', '_blank', 'noopener,noreferrer')
    expect(api.getResourceSource).not.toHaveBeenCalled()
  })
})
